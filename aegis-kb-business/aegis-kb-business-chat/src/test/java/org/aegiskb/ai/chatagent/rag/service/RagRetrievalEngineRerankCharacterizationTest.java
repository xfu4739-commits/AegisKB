package org.aegiskb.ai.chatagent.rag.service;

import org.aegiskb.ai.chatagent.rag.config.ChatRagProperties;
import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.aegiskb.ai.chatagent.rag.model.ConversationExecutionPlan;
import org.aegiskb.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankExecutionReason;
import org.aegiskb.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankExecutionStatus;
import org.aegiskb.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankFailureStage;
import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.aegiskb.ai.chatagent.rag.model.FinalEvidenceDecision.RerankStatus;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S20-P1 characterization：冻结 RagRetrievalEngine.executeRerank 当前编排行为。
 *
 * <p>只回答"旧实现现在做什么"：rerank 成功写回 score/rank 并标记 rerank 通道；
 * 失败时保留 fusion 候选（weighted-hybrid 兜底）、移除 score/rank、加 note、exchange
 * 继续、final selection 仍只执行一次、O9 ledger 守恒。为 S20 Jev 打分后端失败降级
 * 提供基线保护——Jev 失败必须沿用这里的兜底语义，不得中断 exchange。</p>
 */
class RagRetrievalEngineRerankCharacterizationTest {

    private static final String CHANNEL_ALPHA = "FIXTURE_CHANNEL_ALPHA";
    private static final String RERANK_CHANNEL = "rerank";

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
            new ChatRagProperties(),
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

    private RecordingChannel alphaChannel() {
        return new RecordingChannel(CHANNEL_ALPHA, request -> List.of(
            RetrievalPlanFixtures.sourceDocument("alpha-doc-1", 101L, 0.91D),
            RetrievalPlanFixtures.sourceDocument("alpha-doc-2", 102L, 0.81D)
        ));
    }

    @Test
    void rerankSuccessMarksStatusSuccessKeepsScoreRankAndUsedChannel() {
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryRerankPlan(CHANNEL_ALPHA);
        when(ragRerankService.rerank(any(), anyList())).thenAnswer(invocation -> {
            List<Document> candidates = invocation.getArgument(1);
            int rank = 1;
            for (Document candidate : candidates) {
                candidate.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_SCORE, 0.9D - rank * 0.1D);
                candidate.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_RANK, rank++);
            }
            return new RagRerankService.TransportResult(candidates, true, true,
                RerankExecutionReason.NONE, RerankFailureStage.NONE, List.of(), List.of(), "");
        });

        RagRetrievalContext context = engine(List.of(alphaChannel())).retrieve(executionPlan(plan), null);

        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).hasSize(2);
        assertThat(evidence.getSourceDocuments()).allSatisfy(document -> assertThat(document.getMetadata())
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_STATUS, RerankStatus.SUCCESS.name())
            .containsKey(DocumentKnowledgeMetadataKeys.RERANK_SCORE)
            .containsKey(DocumentKnowledgeMetadataKeys.RERANK_RANK));
        assertThat(context.getUsedChannels()).contains(RERANK_CHANNEL);
        assertThat(evidence.getReferences()).extracting(reference -> reference.getReferenceId())
            .containsExactly("1", "2");
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().status())
            .isEqualTo(RerankExecutionStatus.SUCCESS);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().requested()).isTrue();
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().attempted()).isTrue();
        verify(ragRerankService).rerank(any(), anyList());
    }

    @Test
    void rerankFailureFallsBackToWeightedHybridRemovesScoreRankAndContinues() {
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryRerankPlan(CHANNEL_ALPHA);
        when(ragRerankService.rerank(any(), anyList())).thenAnswer(invocation -> {
            List<Document> candidates = invocation.getArgument(1);
            for (Document candidate : candidates) {
                candidate.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_SCORE, 0.5D);
                candidate.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_RANK, 1);
            }
            return new RagRerankService.TransportResult(candidates, true, false,
                RerankExecutionReason.TIMEOUT, RerankFailureStage.TRANSPORT, List.of(), List.of(), "fixture timeout");
        });

        RagRetrievalContext context = engine(List.of(alphaChannel())).retrieve(executionPlan(plan), null);

        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).hasSize(2);
        assertThat(evidence.getSourceDocuments()).allSatisfy(document -> assertThat(document.getMetadata())
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_STATUS, RerankStatus.FAILED.name())
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_ERROR, "fixture timeout")
            .doesNotContainKey(DocumentKnowledgeMetadataKeys.RERANK_SCORE)
            .doesNotContainKey(DocumentKnowledgeMetadataKeys.RERANK_RANK));
        assertThat(context.getUsedChannels()).contains(RERANK_CHANNEL);
        assertThat(context.getRetrievalNotes())
            .anySatisfy(note -> assertThat(note).contains("rerank 失败或超时"));
        assertThat(evidence.getReferences()).extracting(reference -> reference.getReferenceId())
            .containsExactly("1", "2");
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().status())
            .isEqualTo(RerankExecutionStatus.FAILED_AFTER_REQUEST);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().reason())
            .isEqualTo(RerankExecutionReason.TIMEOUT);
    }

    @Test
    void rerankFailureBeforeRequestDoesNotMarkUsedChannelButKeepsEvidence() {
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryRerankPlan(CHANNEL_ALPHA);
        when(ragRerankService.rerank(any(), anyList())).thenAnswer(invocation -> {
            List<Document> candidates = invocation.getArgument(1);
            return new RagRerankService.TransportResult(candidates, false, false,
                RerankExecutionReason.REQUEST_VALIDATION_FAILED, RerankFailureStage.PRE_REQUEST,
                List.of(), List.of(), "fixture validation");
        });

        RagRetrievalContext context = engine(List.of(alphaChannel())).retrieve(executionPlan(plan), null);

        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).hasSize(2);
        assertThat(evidence.getSourceDocuments()).allSatisfy(document -> assertThat(document.getMetadata())
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_STATUS, RerankStatus.FAILED.name())
            .doesNotContainKey(DocumentKnowledgeMetadataKeys.RERANK_SCORE));
        assertThat(context.getUsedChannels()).doesNotContain(RERANK_CHANNEL);
        assertThat(evidence.getReferences()).hasSize(2);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().status())
            .isEqualTo(RerankExecutionStatus.FAILED_BEFORE_REQUEST);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().attempted()).isFalse();
    }

    @Test
    void rerankNotRequestedWhenPlanDisabledNeverCallsService() {
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);

        RagRetrievalContext context = engine(List.of(alphaChannel())).retrieve(executionPlan(plan), null);

        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).hasSize(2);
        assertThat(evidence.getSourceDocuments()).allSatisfy(document -> assertThat(document.getMetadata())
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_STATUS, RerankStatus.NOT_REQUESTED.name())
            .doesNotContainKey(DocumentKnowledgeMetadataKeys.RERANK_SCORE)
            .doesNotContainKey(DocumentKnowledgeMetadataKeys.RERANK_RANK));
        assertThat(context.getUsedChannels()).doesNotContain(RERANK_CHANNEL);
        assertThat(evidence.getReferences()).hasSize(2);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().status())
            .isEqualTo(RerankExecutionStatus.NOT_REQUESTED);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().requested()).isFalse();
        verify(ragRerankService, never()).rerank(any(), anyList());
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