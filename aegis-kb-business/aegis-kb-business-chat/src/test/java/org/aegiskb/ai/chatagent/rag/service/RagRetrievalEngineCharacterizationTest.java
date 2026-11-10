package org.aegiskb.ai.chatagent.rag.service;

import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.aegiskb.ai.chatagent.rag.model.ConversationExecutionPlan;
import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.aegiskb.ai.chatagent.rag.model.RagRetrievalContext;
import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionRequest;
import org.aegiskb.ai.chatagent.rag.model.RetrievalPlan;
import org.aegiskb.ai.chatagent.rag.model.SubQuestionEvidence;
import org.aegiskb.ai.chatagent.rag.retrieve.channel.RetrievalChannel;
import org.aegiskb.ai.chatagent.rag.retrieve.channel.RetrievalChannelResult;
import org.aegiskb.ai.manage.service.DocumentKnowledgeService;
import org.aegiskb.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S19-P1 characterization：冻结 RagRetrievalEngine 当前编排行为。
 *
 * <p>只回答"旧实现现在做什么"，为 SAG 分支拆分引擎提供基线保护。</p>
 */
class RagRetrievalEngineCharacterizationTest {

    private static final String CHANNEL_ALPHA = "FIXTURE_CHANNEL_ALPHA";
    private static final String CHANNEL_BETA = "FIXTURE_CHANNEL_BETA";

    private ExecutorService executorService;
    private DocumentKnowledgeService documentKnowledgeService;
    private RagRerankService ragRerankService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
        documentKnowledgeService = mock(DocumentKnowledgeService.class);
        when(documentKnowledgeService.elevateToParentBlocks(anyList(), anyInt())).thenReturn(List.of());
        ragRerankService = mock(RagRerankService.class);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    private RagRetrievalEngine engine(List<RetrievalChannel> channels) {
        return new RagRetrievalEngine(
            channels,
            new org.aegiskb.ai.chatagent.rag.config.ChatRagProperties(),
            ragRerankService,
            documentKnowledgeService,
            executorService
        );
    }

    private ConversationExecutionPlan executionPlan(RetrievalPlan retrievalPlan) {
        return ConversationExecutionPlan.builder()
            .mode(ExecutionMode.RETRIEVAL)
            .retrievalPlan(retrievalPlan)
            .noEvidenceReply("fixture no evidence reply")
            .build();
    }

    @Test
    void retrieveRejectsMissingPlans() {
        RagRetrievalEngine engine = engine(List.of());
        assertThatThrownBy(() -> engine.retrieve(null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.retrieve(ConversationExecutionPlan.builder().build(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RetrievalPlan is required");
    }

    @Test
    void retrieveExecutesEnabledChannelsOncePerSubQuestionAndProjectsPlanVerbatim() {
        RecordingChannel alpha = new RecordingChannel(CHANNEL_ALPHA, request -> List.of(
            RetrievalPlanFixtures.sourceDocument("alpha-doc-1", 101L, 0.91D),
            RetrievalPlanFixtures.sourceDocument("alpha-doc-2", 102L, 0.81D)
        ));
        RecordingChannel beta = new RecordingChannel(CHANNEL_BETA, request -> List.of(
            RetrievalPlanFixtures.sourceDocument("beta-doc-1", 201L, 0.71D)
        ));
        RetrievalPlan plan = RetrievalPlanFixtures.plan(
            List.of(
                RetrievalPlanFixtures.executionQuery(1, "fixture execution query one"),
                RetrievalPlanFixtures.executionQuery(2, "fixture execution query two")
            ),
            List.of(RetrievalPlanFixtures.channelPlan(CHANNEL_ALPHA), RetrievalPlanFixtures.channelPlan(CHANNEL_BETA))
        );

        RagRetrievalContext context = engine(List.of(alpha, beta)).retrieve(executionPlan(plan), null);

        assertThat(alpha.receivedRequests).hasSize(2);
        assertThat(beta.receivedRequests).hasSize(2);
        assertThat(context.getExecutionRequests()).hasSize(2);
        assertThat(context.getRequestReconciliations())
            .allSatisfy(reconciliation -> assertThat(reconciliation.differences()).isEmpty());
        for (RetrievalExecutionRequest request : alpha.receivedRequests) {
            assertThat(request.scopeMode()).isEqualTo(plan.getScopeMode());
            assertThat(request.knowledgeBaseIds()).containsExactly(RetrievalPlanFixtures.KNOWLEDGE_BASE_ID);
            assertThat(request.documentScope()).containsExactly(RetrievalPlanFixtures.DOCUMENT_ID);
            assertThat(request.taskScope()).containsExactly(RetrievalPlanFixtures.TASK_ID);
        }
        assertThat(context.getSubQuestionEvidenceList())
            .extracting(SubQuestionEvidence::getSubQuestionIndex)
            .containsExactly(1, 2);
        assertThat(context.getUsedChannels()).contains(CHANNEL_ALPHA, CHANNEL_BETA);
    }

    @Test
    void retrieveSelectsSourceEvidenceOnceAndAssignsUniqueReferenceIds() {
        RecordingChannel alpha = new RecordingChannel(CHANNEL_ALPHA, request -> List.of(
            RetrievalPlanFixtures.sourceDocument("alpha-doc-1", 101L, 0.91D),
            RetrievalPlanFixtures.sourceDocument("alpha-doc-2", 102L, 0.81D)
        ));
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);

        RagRetrievalContext context = engine(List.of(alpha)).retrieve(executionPlan(plan), null);

        assertThat(context.isEmpty()).isFalse();
        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).hasSize(2);
        assertThat(evidence.getContextDocuments()).isEmpty();
        assertThat(evidence.getEvidenceSelectionLedger()).isNotNull();
        assertThat(evidence.getObservationPersistence()).isNotNull();
        assertThat(evidence.getSourceDocuments())
            .allSatisfy(document -> assertThat(document.getMetadata())
                .containsKey(DocumentKnowledgeMetadataKeys.FINAL_SELECTION_REASON));
        assertThat(evidence.getReferences()).hasSize(2);
        assertThat(evidence.getReferences())
            .extracting(reference -> reference.getReferenceId())
            .containsExactly("1", "2");
        assertThat(context.flattenReferences()).hasSize(2);
    }

    @Test
    void channelFailureDegradesToNoteAndKeepsOtherChannelEvidence() {
        RecordingChannel failing = new RecordingChannel(CHANNEL_ALPHA, request -> {
            throw new IllegalStateException("fixture channel failure");
        });
        RecordingChannel healthy = new RecordingChannel(CHANNEL_BETA, request -> List.of(
            RetrievalPlanFixtures.sourceDocument("beta-doc-1", 201L, 0.71D)
        ));
        RetrievalPlan plan = RetrievalPlanFixtures.plan(
            List.of(RetrievalPlanFixtures.executionQuery(1, "fixture execution query")),
            List.of(RetrievalPlanFixtures.channelPlan(CHANNEL_ALPHA), RetrievalPlanFixtures.channelPlan(CHANNEL_BETA))
        );

        RagRetrievalContext context = engine(List.of(failing, healthy)).retrieve(executionPlan(plan), null);

        assertThat(context.getRetrievalNotes())
            .anySatisfy(note -> assertThat(note).contains(CHANNEL_ALPHA).contains("失败"));
        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).hasSize(1);
        assertThat(context.getUsedChannels()).contains(CHANNEL_BETA).doesNotContain(CHANNEL_ALPHA);
    }

    @Test
    void disabledChannelsProduceEmptyEvidenceWithNote() {
        RetrievalPlan plan = RetrievalPlanFixtures.plan(
            List.of(RetrievalPlanFixtures.executionQuery(1, "fixture execution query")),
            List.of(RetrievalPlanFixtures.disabledChannelPlan(CHANNEL_ALPHA))
        );

        RagRetrievalContext context = engine(List.of()).retrieve(executionPlan(plan), null);

        assertThat(context.getRetrievalNotes())
            .anySatisfy(note -> assertThat(note).contains("没有可用的检索通道"));
        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).isEmpty();
        assertThat(evidence.getContextDocuments()).isEmpty();
        assertThat(context.isEmpty()).isTrue();
    }

    @Test
    void enabledChannelWithoutImplementationFailsClosed() {
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);
        RagRetrievalEngine engine = engine(List.of());

        assertThatThrownBy(() -> engine.retrieve(executionPlan(plan), null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exactly one implementation");
    }

    private static final class RecordingChannel implements RetrievalChannel {

        private final String name;
        private final Function<RetrievalExecutionRequest, List<Document>> supplier;
        private final List<RetrievalExecutionRequest> receivedRequests =
            Collections.synchronizedList(new ArrayList<>());

        private RecordingChannel(String name, Function<RetrievalExecutionRequest, List<Document>> supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        @Override
        public String channelName() {
            return name;
        }

        @Override
        public RetrievalChannelResult retrieve(RetrievalExecutionRequest request) {
            receivedRequests.add(request);
            return new RetrievalChannelResult(name, new ArrayList<>(supplier.apply(request)));
        }
    }
}
