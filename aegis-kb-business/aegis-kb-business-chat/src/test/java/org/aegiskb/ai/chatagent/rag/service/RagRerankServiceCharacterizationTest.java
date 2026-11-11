package org.aegiskb.ai.chatagent.rag.service;

import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.aegiskb.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankExecutionReason;
import org.aegiskb.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankFailureStage;
import org.aegiskb.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankResultStatus;
import org.aegiskb.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.aegiskb.ai.ragtools.client.RagToolsClient;
import org.aegiskb.ai.ragtools.model.RagToolsRerankRequest;
import org.aegiskb.ai.ragtools.model.RagToolsRerankResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S20-P1 characterization：冻结 RagRerankService 当前重排打分传输/校验契约。
 *
 * <p>只回答"旧实现现在做什么"：真实 service + mock RagToolsClient，覆盖成功写回、
 * 全覆盖/唯一 rank/有限 score 校验、各类失败语义与降级 reason，为 S20 可切换 Jev
 * 打分后端提供基线保护。Jev 后端必须产出同形状契约，否则沿用这里的失败语义。</p>
 */
class RagRerankServiceCharacterizationTest {

    private RagToolsClient ragToolsClient;
    private RagRerankService ragRerankService;

    @BeforeEach
    void setUp() {
        ragToolsClient = mock(RagToolsClient.class);
        ragRerankService = new RagRerankService(ragToolsClient);
    }

    private List<Document> candidates(int count) {
        List<Document> documents = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            documents.add(RetrievalPlanFixtures.sourceDocument("fixture-doc-" + index, 100L + index, 0.9D - index * 0.1D));
        }
        return documents;
    }

    private RagToolsRerankResponse.Result result(String id, Double score, Integer rank) {
        RagToolsRerankResponse.Result result = new RagToolsRerankResponse.Result();
        result.setId(id);
        result.setScore(score);
        result.setRank(rank);
        return result;
    }

    private RagToolsRerankResponse response(List<RagToolsRerankResponse.Result> results) {
        RagToolsRerankResponse response = new RagToolsRerankResponse();
        response.setResults(results);
        return response;
    }

    @Test
    void successWritesRerankMetadataAndConservesFullCoverage() {
        List<Document> documents = candidates(3);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class))).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            List<RagToolsRerankResponse.Result> results = new ArrayList<>();
            for (int index = 0; index < request.getCandidates().size(); index++) {
                results.add(result(request.getCandidates().get(index).getId(), 0.8D - index * 0.2D, index + 1));
            }
            return response(results);
        });

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isTrue();
        assertThat(transport.attempted()).isTrue();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.NONE);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.NONE);
        assertThat(transport.requestCandidates()).hasSize(3);
        assertThat(transport.resultCandidates()).hasSize(3);
        assertThat(transport.resultCandidates())
            .allSatisfy(candidate -> assertThat(candidate.resultStatus()).isEqualTo(RerankResultStatus.FINITE));
        assertThat(documents).allSatisfy(document -> assertThat(document.getMetadata())
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_MODEL, "rag-tools")
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_CANDIDATE_COUNT, 3)
            .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_TOP_K, 3)
            .containsKey(DocumentKnowledgeMetadataKeys.RERANK_SCORE)
            .containsKey(DocumentKnowledgeMetadataKeys.RERANK_RANK));
        assertThat(documents.get(0).getMetadata().get(DocumentKnowledgeMetadataKeys.RERANK_SCORE)).isEqualTo(0.8D);
        assertThat(documents.get(0).getMetadata().get(DocumentKnowledgeMetadataKeys.RERANK_RANK)).isEqualTo(1);
    }

    @Test
    void requestProjectionUsesReqPrefixVerbatimTextAndFullTopK() {
        List<Document> documents = candidates(2);
        List<RagToolsRerankRequest> captured = new ArrayList<>();
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class))).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            captured.add(request);
            List<RagToolsRerankResponse.Result> results = new ArrayList<>();
            for (int index = 0; index < request.getCandidates().size(); index++) {
                results.add(result(request.getCandidates().get(index).getId(), 0.5D, index + 1));
            }
            return response(results);
        });

        ragRerankService.rerank("fixture query", documents);

        assertThat(captured).hasSize(1);
        RagToolsRerankRequest request = captured.get(0);
        assertThat(request.getQuery()).isEqualTo("fixture query");
        assertThat(request.getTopK()).isEqualTo(2);
        assertThat(request.getCandidates()).hasSize(2);
        assertThat(request.getCandidates()).allSatisfy(candidate ->
            assertThat(candidate.getId()).startsWith("REQ:"));
        assertThat(request.getCandidates().get(0).getText()).isEqualTo(documents.get(0).getText());
    }

    @Test
    void nonFiniteScoreIsStoredAsZeroAndMarkedNonFinite() {
        List<Document> documents = candidates(1);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class))).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            return response(List.of(result(request.getCandidates().get(0).getId(), Double.NaN, 1)));
        });

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isTrue();
        assertThat(transport.resultCandidates()).hasSize(1);
        assertThat(transport.resultCandidates().get(0).resultStatus()).isEqualTo(RerankResultStatus.NON_FINITE_TO_ZERO);
        assertThat(documents.get(0).getMetadata().get(DocumentKnowledgeMetadataKeys.RERANK_SCORE)).isEqualTo(0D);
    }

    @Test
    void emptyResponseFailsAfterWithInvalidResponse() {
        List<Document> documents = candidates(2);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class)))
            .thenReturn(new RagToolsRerankResponse());

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.attempted()).isTrue();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.INVALID_RESPONSE);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.RESPONSE_VALIDATION);
        assertThat(transport.resultCandidates()).isEmpty();
        assertThat(documents).allSatisfy(document -> assertThat(document.getMetadata())
            .doesNotContainKey(DocumentKnowledgeMetadataKeys.RERANK_SCORE));
    }

    @Test
    void partialCoverageFailsAfterWithPartialResultAndUnusableLedger() {
        List<Document> documents = candidates(3);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class))).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            return response(List.of(
                result(request.getCandidates().get(0).getId(), 0.9D, 1),
                result(request.getCandidates().get(1).getId(), 0.8D, 2)
            ));
        });

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.PARTIAL_RESULT);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.RESPONSE_VALIDATION);
        assertThat(transport.resultCandidates()).hasSize(3);
        assertThat(transport.resultCandidates())
            .allSatisfy(candidate -> assertThat(candidate.resultStatus()).isEqualTo(RerankResultStatus.UNUSABLE));
    }

    @Test
    void unknownResultIdFailsAfterWithInvalidResponse() {
        List<Document> documents = candidates(1);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class)))
            .thenReturn(response(List.of(result("REQ:unknown-identity", 0.9D, 1))));

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.INVALID_RESPONSE);
        assertThat(transport.resultCandidates())
            .allSatisfy(candidate -> assertThat(candidate.resultStatus()).isEqualTo(RerankResultStatus.UNUSABLE));
    }

    @Test
    void duplicateRankFailsAfterWithInvalidResponse() {
        List<Document> documents = candidates(2);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class))).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            return response(List.of(
                result(request.getCandidates().get(0).getId(), 0.9D, 1),
                result(request.getCandidates().get(1).getId(), 0.8D, 1)
            ));
        });

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.INVALID_RESPONSE);
    }

    @Test
    void nonPositiveRankFailsAfterWithInvalidResponse() {
        List<Document> documents = candidates(1);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class))).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            return response(List.of(result(request.getCandidates().get(0).getId(), 0.9D, 0)));
        });

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.INVALID_RESPONSE);
    }

    @Test
    void nullScoreFailsAfterWithInvalidResponse() {
        List<Document> documents = candidates(1);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class))).thenAnswer(invocation -> {
            RagToolsRerankRequest request = invocation.getArgument(0);
            return response(List.of(result(request.getCandidates().get(0).getId(), null, 1)));
        });

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.INVALID_RESPONSE);
    }

    @Test
    void timeoutExceptionMapsToTimeoutReason() {
        List<Document> documents = candidates(1);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class)))
            .thenThrow(new IllegalStateException("fixture transport", new TimeoutException("read timed out")));

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.attempted()).isTrue();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.TIMEOUT);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.TRANSPORT);
    }

    @Test
    void cancellationMapsToCancelledReason() {
        List<Document> documents = candidates(1);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class)))
            .thenThrow(new IllegalStateException("fixture transport", new CancellationException("cancelled")));

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.CANCELLED);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.TRANSPORT);
    }

    @Test
    void genericTransportErrorMapsToTransportErrorReason() {
        List<Document> documents = candidates(1);
        when(ragToolsClient.rerank(any(RagToolsRerankRequest.class)))
            .thenThrow(new IllegalStateException("fixture connection refused"));

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.TRANSPORT_ERROR);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.TRANSPORT);
    }

    @Test
    void nullClientFailsBeforeWithClientInitializationFailed() {
        RagRerankService serviceWithoutClient = new RagRerankService(null);

        RagRerankService.TransportResult transport = serviceWithoutClient.rerank("fixture query", candidates(1));

        assertThat(transport.success()).isFalse();
        assertThat(transport.attempted()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.CLIENT_INITIALIZATION_FAILED);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.PRE_REQUEST);
    }

    @Test
    void emptyWindowFailsBeforeRequest() {
        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", List.of());

        assertThat(transport.success()).isFalse();
        assertThat(transport.attempted()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.REQUEST_VALIDATION_FAILED);
        assertThat(transport.failureStage()).isEqualTo(RerankFailureStage.PRE_REQUEST);
        assertThat(transport.error()).contains("empty");
    }

    @Test
    void blankQueryFailsBeforeRequest() {
        RagRerankService.TransportResult transport = ragRerankService.rerank("   ", candidates(1));

        assertThat(transport.success()).isFalse();
        assertThat(transport.attempted()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.REQUEST_VALIDATION_FAILED);
    }

    @Test
    void duplicateCandidateIdsFailBeforeRequest() {
        List<Document> documents = candidates(2);
        documents.get(0).getMetadata().put(DocumentKnowledgeMetadataKeys.CANDIDATE_ID, "fixture-duplicate");
        documents.get(1).getMetadata().put(DocumentKnowledgeMetadataKeys.CANDIDATE_ID, "fixture-duplicate");

        RagRerankService.TransportResult transport = ragRerankService.rerank("fixture query", documents);

        assertThat(transport.success()).isFalse();
        assertThat(transport.attempted()).isFalse();
        assertThat(transport.reason()).isEqualTo(RerankExecutionReason.REQUEST_VALIDATION_FAILED);
        assertThat(transport.error()).contains("unique");
    }
}