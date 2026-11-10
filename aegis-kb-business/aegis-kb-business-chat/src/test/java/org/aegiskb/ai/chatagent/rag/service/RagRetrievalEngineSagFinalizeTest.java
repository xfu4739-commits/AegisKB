package org.aegiskb.ai.chatagent.rag.service;

import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S19-P4 目标不变量：SAG 阶段2 对合并窗口一次性执行 final evidence selection。
 *
 * <p>per-call 阶段结果不得携带 final policy 决策；合并窗口按稳定 identity 去重保序；
 * 产出唯一 SubQuestionEvidence(index=1)，executionRequests 索引与证据索引一致。</p>
 */
class RagRetrievalEngineSagFinalizeTest {

    private static final String CHANNEL_ALPHA = "FIXTURE_CHANNEL_ALPHA";

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

    @Test
    void finalizeSelectsFinalEvidenceOnceOverMergedWindow() {
        Deque<List<Document>> responses = new ArrayDeque<>(List.of(
            List.of(
                RetrievalPlanFixtures.sourceDocument("alpha-doc-1", 101L, 0.91D),
                RetrievalPlanFixtures.sourceDocument("alpha-doc-2", 102L, 0.81D)
            ),
            List.of(
                RetrievalPlanFixtures.sourceDocument("alpha-doc-2", 102L, 0.79D),
                RetrievalPlanFixtures.sourceDocument("alpha-doc-3", 103L, 0.75D)
            )
        ));
        RecordingChannel alpha = new RecordingChannel(CHANNEL_ALPHA, request -> responses.poll());
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);
        RetrievalExecutionRequest request = RetrievalExecutionRequest.compile(plan).get(0);
        List<String> usedChannels = Collections.synchronizedList(new ArrayList<>());
        List<String> notes = Collections.synchronizedList(new ArrayList<>());
        RagRetrievalEngine engine = engine(List.of(alpha));

        RagRetrievalEngine.RetrievalStageResult firstCall =
            engine.executeRetrievalStagesAsync(request, plan, usedChannels, notes).join();
        RagRetrievalEngine.RetrievalStageResult secondCall =
            engine.executeRetrievalStagesAsync(request, plan, usedChannels, notes).join();

        List<Document> perCallCandidates = new ArrayList<>();
        perCallCandidates.addAll(firstCall.rerankResult().candidates());
        perCallCandidates.addAll(secondCall.rerankResult().candidates());
        assertThat(perCallCandidates)
            .allSatisfy(document -> assertThat(document.getMetadata())
                .doesNotContainKey(DocumentKnowledgeMetadataKeys.FINAL_SELECTION_REASON));

        RagRetrievalContext context = engine.finalizeSagContext(
            plan, List.of(firstCall, secondCall), usedChannels, notes, null);

        assertThat(context.getRetrievalQuestion()).isEqualTo("fixture normalized query");
        assertThat(context.getSubQuestionEvidenceList()).hasSize(1);

        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSubQuestionIndex()).isEqualTo(1);
        assertThat(evidence.getSourceDocuments()).hasSize(3);
        assertThat(evidence.getSourceDocuments())
            .allSatisfy(document -> assertThat(document.getMetadata())
                .containsKey(DocumentKnowledgeMetadataKeys.FINAL_SELECTION_REASON));
        assertThat(evidence.getReferences()).hasSize(3);
        assertThat(evidence.getReferences())
            .extracting(reference -> reference.getReferenceId())
            .containsExactly("1", "2", "3");
        assertThat(evidence.getEvidenceSelectionLedger()).isNotNull();
        assertThat(evidence.getEvidenceSelectionLedger().finalEvidenceDecisions()).hasSize(3);
        assertThat(evidence.getEvidenceSelectionLedger().evidenceLineage()
            .finalEvidencePolicyInputCandidateIds()).hasSize(3);
        assertThat(evidence.getObservationPersistence()).isNotNull();

        assertThat(context.getExecutionRequests()).hasSize(1);
        assertThat(context.getExecutionRequests().get(0).subQuestionIndex()).isEqualTo(1);
        assertThat(context.getUsedChannels()).contains(CHANNEL_ALPHA);
        assertThat(context.isEmpty()).isFalse();
        assertThat(context.flattenReferences()).hasSize(3);
    }

    @Test
    void finalizeWithoutAnyKbSearchCallYieldsEmptyEvidenceContext() {
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);
        List<String> usedChannels = Collections.synchronizedList(new ArrayList<>());
        List<String> notes = Collections.synchronizedList(new ArrayList<>());

        RagRetrievalContext context = engine(List.of()).finalizeSagContext(
            plan, List.of(), usedChannels, notes, null);

        assertThat(context.isEmpty()).isTrue();
        assertThat(context.getSubQuestionEvidenceList()).hasSize(1);
        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSubQuestionIndex()).isEqualTo(1);
        assertThat(evidence.getSourceDocuments()).isEmpty();
        assertThat(evidence.getContextDocuments()).isEmpty();
        assertThat(evidence.getReferences()).isEmpty();
        assertThat(evidence.getEvidenceSelectionLedger()).isNotNull();
        assertThat(evidence.getEvidenceSelectionLedger().finalEvidenceDecisions()).isEmpty();
        assertThat(evidence.getObservationPersistence()).isNotNull();
        assertThat(context.getExecutionRequests()).hasSize(1);
        assertThat(context.getExecutionRequests().get(0).subQuestionIndex()).isEqualTo(1);
    }

    private static final class RecordingChannel implements RetrievalChannel {

        private final String name;
        private final Function<RetrievalExecutionRequest, List<Document>> supplier;

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
            return new RetrievalChannelResult(name, new ArrayList<>(supplier.apply(request)));
        }
    }
}
