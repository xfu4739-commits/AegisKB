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
import org.aegiskb.ai.ragtools.client.RagToolsClient;
import org.aegiskb.ai.ragtools.model.RagToolsRerankRequest;
import org.aegiskb.ai.ragtools.model.RagToolsRerankResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

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
import static org.mockito.Mockito.when;

/**
 * S20-P3 集成验证：Jev 打分后端 fail-closed 降级链路。
 *
 * <p>与 P1 characterization（mock service）不同，本类接真实 {@link RagRerankService} +
 * 真实 {@link RagRetrievalEngine}，只在 HTTP 边界 mock {@link RagToolsClient}，抛出 Python
 * `/rerank` 在 Jev 不可用/无 key/超时/契约不符时返回的 503 所对应的真实 Spring 异常
 * {@link HttpServerErrorException.ServiceUnavailable}。证明：503 → TRANSPORT_ERROR/TRANSPORT
 * → executeRerank weighted-hybrid 兜底（保留 fusion 候选、移除 score/rank、加 note、exchange
 * 继续、final selection 单次、references/O9 守恒）。成功控制组证明同一接线对合法响应仍写回
 * score/rank（backend 无关）。Java 权威零改动，无真实出境。</p>
 */
class RagRerankJevDegradationIntegrationTest {

    private static final String CHANNEL_ALPHA = "FIXTURE_CHANNEL_ALPHA";
    private static final String RERANK_CHANNEL = "rerank";

    private ExecutorService executorService;
    private DocumentKnowledgeService documentKnowledgeService;
    private RagToolsClient ragToolsClient;
    private RagRerankService ragRerankService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
        documentKnowledgeService = mock(DocumentKnowledgeService.class);
        when(documentKnowledgeService.elevateToParentBlocks(anyList(), anyInt())).thenReturn(List.of());
        ragToolsClient = mock(RagToolsClient.class);
        ragRerankService = new RagRerankService(ragToolsClient);
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

    private HttpServerErrorException python503() {
        return HttpServerErrorException.create(
            HttpStatus.SERVICE_UNAVAILABLE, "503 Service Unavailable", null, null, null);
    }

    @Test
    void python503MapsToTransportErrorFailedAfterRetainingFusionCandidates() {
        when(ragToolsClient.rerank(any())).thenThrow(python503());
        List<Document> candidates = List.of(
            RetrievalPlanFixtures.sourceDocument("doc-1", 201L, 0.90D),
            RetrievalPlanFixtures.sourceDocument("doc-2", 202L, 0.80D)
        );

        RagRerankService.TransportResult result = ragRerankService.rerank("fixture query", candidates);

        assertThat(result.success()).isFalse();
        assertThat(result.attempted()).isTrue();
        assertThat(result.reason()).isEqualTo(RerankExecutionReason.TRANSPORT_ERROR);
        assertThat(result.failureStage()).isEqualTo(RerankFailureStage.TRANSPORT);
        assertThat(result.resultCandidates()).isEmpty();
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.error()).contains("ServiceUnavailable");
    }

    @Test
    void jev503DegradesToWeightedHybridConservesEvidenceAndContinuesExchange() {
        when(ragToolsClient.rerank(any())).thenThrow(python503());
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryRerankPlan(CHANNEL_ALPHA);

        RagRetrievalContext context = engine(List.of(alphaChannel())).retrieve(executionPlan(plan), null);

        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);
        assertThat(evidence.getSourceDocuments()).hasSize(2);
        assertThat(evidence.getSourceDocuments()).allSatisfy(document -> assertThat(document.getMetadata())
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_STATUS, RerankStatus.FAILED.name())
            .containsKey(DocumentKnowledgeMetadataKeys.RERANK_ERROR)
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
            .isEqualTo(RerankExecutionReason.TRANSPORT_ERROR);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().failureStage())
            .isEqualTo(RerankFailureStage.TRANSPORT);
        assertThat(evidence.getEvidenceSelectionLedger().rerankExecution().attempted()).isTrue();
    }

    @Test
    void successfulRerankResponseWritesScoreRankThroughRealServiceRegardlessOfBackend() {
        when(ragToolsClient.rerank(any())).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            List<RagToolsRerankResponse.Result> results = new ArrayList<>();
            int rank = 1;
            for (RagToolsRerankRequest.Candidate candidate : request.getCandidates()) {
                RagToolsRerankResponse.Result result = new RagToolsRerankResponse.Result();
                result.setId(candidate.getId());
                result.setScore(0.9D - rank * 0.1D);
                result.setRank(rank++);
                results.add(result);
            }
            RagToolsRerankResponse response = new RagToolsRerankResponse();
            response.setResults(results);
            return response;
        });
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryRerankPlan(CHANNEL_ALPHA);

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