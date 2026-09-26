package org.javaup.ai.chatagent.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankFailureStage;
import org.javaup.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankRequestCandidate;
import org.javaup.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankResultCandidate;
import org.javaup.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankResultStatus;
import org.javaup.ai.chatagent.rag.model.EvidenceSelectionLedger.RerankExecutionReason;
import org.javaup.ai.chatagent.rag.support.EvidenceCandidateIdentity;
import org.javaup.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.javaup.ai.ragtools.client.RagToolsClient;
import org.javaup.ai.ragtools.model.RagToolsRerankRequest;
import org.javaup.ai.ragtools.model.RagToolsRerankResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class RagRerankService {

    private final RagToolsClient ragToolsClient;

    public RagRerankService(RagToolsClient ragToolsClient) {
        this.ragToolsClient = ragToolsClient;
    }

    public TransportResult rerank(String query, List<Document> candidates) {
        List<Document> sourceWindow;
        try {
            sourceWindow = candidates == null ? List.of() : List.copyOf(candidates);
        }
        catch (RuntimeException exception) {
            return TransportResult.failedBefore(
                List.of(),
                RerankExecutionReason.REQUEST_VALIDATION_FAILED,
                errorMessage(exception)
            );
        }
        if (sourceWindow.isEmpty()) {
            return TransportResult.failedBefore(
                sourceWindow,
                RerankExecutionReason.REQUEST_VALIDATION_FAILED,
                "Source rerank window is empty"
            );
        }

        PreparedRequest prepared;
        try {
            prepared = prepare(query, sourceWindow);
        }
        catch (RuntimeException exception) {
            return TransportResult.failedBefore(
                sourceWindow,
                RerankExecutionReason.REQUEST_VALIDATION_FAILED,
                errorMessage(exception)
            );
        }
        if (ragToolsClient == null) {
            return TransportResult.failedBefore(
                sourceWindow,
                RerankExecutionReason.CLIENT_INITIALIZATION_FAILED,
                "RagToolsClient is unavailable"
            );
        }

        RagToolsRerankResponse response;
        try {
            response = ragToolsClient.rerank(prepared.request());
        }
        catch (RuntimeException exception) {
            return TransportResult.failedAfter(
                sourceWindow,
                prepared.ledger(),
                transportReason(exception),
                RerankFailureStage.TRANSPORT,
                List.of(),
                errorMessage(exception)
            );
        }

        try {
            return validateResponse(sourceWindow, prepared, response);
        }
        catch (RuntimeException exception) {
            return TransportResult.failedAfter(
                sourceWindow,
                prepared.ledger(),
                RerankExecutionReason.INVALID_RESPONSE,
                RerankFailureStage.RESPONSE_VALIDATION,
                unusableResults(prepared.ledger()),
                errorMessage(exception)
            );
        }
    }

    private PreparedRequest prepare(String query, List<Document> candidates) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("rerank query is required");
        }
        Map<String, Document> documentsByRequestId = new LinkedHashMap<>();
        List<RerankRequestCandidate> ledger = new ArrayList<>(candidates.size());
        List<RagToolsRerankRequest.Candidate> requestCandidates = new ArrayList<>(candidates.size());
        for (Document document : candidates) {
            EvidenceCandidateIdentity.ensure(document);
            String candidateId = EvidenceCandidateIdentity.candidateId(document);
            String requestCandidateId = "REQ:" + candidateId;
            if (documentsByRequestId.putIfAbsent(requestCandidateId, document) != null) {
                throw new IllegalArgumentException("rerank candidate IDs must be unique");
            }
            ledger.add(new RerankRequestCandidate(requestCandidateId, candidateId));
            requestCandidates.add(new RagToolsRerankRequest.Candidate(
                requestCandidateId,
                document.getText() == null ? "" : document.getText(),
                new LinkedHashMap<>(document.getMetadata())
            ));
        }
        return new PreparedRequest(
            new RagToolsRerankRequest(query, requestCandidates, candidates.size()),
            List.copyOf(ledger),
            documentsByRequestId
        );
    }

    private TransportResult validateResponse(List<Document> candidates,
                                             PreparedRequest prepared,
                                             RagToolsRerankResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return TransportResult.failedAfter(
                candidates,
                prepared.ledger(),
                RerankExecutionReason.INVALID_RESPONSE,
                RerankFailureStage.RESPONSE_VALIDATION,
                List.of(),
                "rerank response is empty"
            );
        }

        Map<String, RagToolsRerankResponse.Result> resultsByRequestId = new LinkedHashMap<>();
        Set<Integer> ranks = new LinkedHashSet<>();
        boolean invalid = false;
        for (RagToolsRerankResponse.Result result : response.getResults()) {
            if (result == null || result.getId() == null
                || !prepared.documentsByRequestId().containsKey(result.getId())
                || resultsByRequestId.putIfAbsent(result.getId(), result) != null
                || result.getRank() == null || result.getRank() <= 0
                || !ranks.add(result.getRank())
                || result.getScore() == null) {
                invalid = true;
            }
        }
        if (invalid) {
            return TransportResult.failedAfter(
                candidates,
                prepared.ledger(),
                RerankExecutionReason.INVALID_RESPONSE,
                RerankFailureStage.RESPONSE_VALIDATION,
                unusableResults(prepared.ledger()),
                "rerank response contains unknown, duplicate, or unusable results"
            );
        }
        if (resultsByRequestId.size() != prepared.ledger().size()) {
            return TransportResult.failedAfter(
                candidates,
                prepared.ledger(),
                RerankExecutionReason.PARTIAL_RESULT,
                RerankFailureStage.RESPONSE_VALIDATION,
                unusableResults(prepared.ledger()),
                "rerank response does not cover the complete request"
            );
        }

        List<RerankResultCandidate> resultLedger = new ArrayList<>(prepared.ledger().size());
        for (RerankRequestCandidate requestCandidate : prepared.ledger()) {
            RagToolsRerankResponse.Result result = resultsByRequestId.get(requestCandidate.requestCandidateId());
            Document document = prepared.documentsByRequestId().get(requestCandidate.requestCandidateId());
            double rawScore = result.getScore();
            boolean finite = Double.isFinite(rawScore);
            double storedScore = finite ? rawScore : 0D;
            document.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_SCORE, storedScore);
            document.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_RANK, result.getRank());
            document.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_MODEL, "rag-tools");
            document.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_CANDIDATE_COUNT, candidates.size());
            document.getMetadata().put(DocumentKnowledgeMetadataKeys.RERANK_TOP_K, candidates.size());
            resultLedger.add(new RerankResultCandidate(
                requestCandidate.requestCandidateId(),
                requestCandidate.candidateId(),
                finite ? RerankResultStatus.FINITE : RerankResultStatus.NON_FINITE_TO_ZERO,
                storedScore,
                result.getRank()
            ));
        }
        log.info("rag-tools rerank 完成: candidateCount={}, topK={}, resultCount={}",
            candidates.size(), candidates.size(), resultLedger.size());
        return TransportResult.success(candidates, prepared.ledger(), resultLedger);
    }

    private List<RerankResultCandidate> unusableResults(List<RerankRequestCandidate> requests) {
        return requests.stream()
            .map(request -> new RerankResultCandidate(
                request.requestCandidateId(),
                request.candidateId(),
                RerankResultStatus.UNUSABLE,
                null,
                null
            ))
            .toList();
    }

    private RerankExecutionReason transportReason(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException || current instanceof InterruptedException) {
                return RerankExecutionReason.CANCELLED;
            }
            if (current instanceof TimeoutException
                || current.getClass().getSimpleName().toLowerCase().contains("timeout")) {
                return RerankExecutionReason.TIMEOUT;
            }
            current = current.getCause();
        }
        return RerankExecutionReason.TRANSPORT_ERROR;
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().trim();
        return throwable.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
    }

    private record PreparedRequest(
        RagToolsRerankRequest request,
        List<RerankRequestCandidate> ledger,
        Map<String, Document> documentsByRequestId
    ) {
    }

    public record TransportResult(
        List<Document> candidates,
        boolean attempted,
        boolean success,
        RerankExecutionReason reason,
        RerankFailureStage failureStage,
        List<RerankRequestCandidate> requestCandidates,
        List<RerankResultCandidate> resultCandidates,
        String error
    ) {

        public TransportResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            requestCandidates = requestCandidates == null ? List.of() : List.copyOf(requestCandidates);
            resultCandidates = resultCandidates == null ? List.of() : List.copyOf(resultCandidates);
            reason = reason == null ? RerankExecutionReason.INVALID_RESPONSE : reason;
            failureStage = failureStage == null ? RerankFailureStage.RESPONSE_VALIDATION : failureStage;
            error = error == null ? "" : error;
        }

        private static TransportResult success(List<Document> candidates,
                                               List<RerankRequestCandidate> requests,
                                               List<RerankResultCandidate> results) {
            return new TransportResult(candidates, true, true, RerankExecutionReason.NONE,
                RerankFailureStage.NONE, requests, results, "");
        }

        private static TransportResult failedBefore(List<Document> candidates,
                                                    RerankExecutionReason reason,
                                                    String error) {
            return new TransportResult(candidates, false, false, reason,
                RerankFailureStage.PRE_REQUEST, List.of(), List.of(), error);
        }

        private static TransportResult failedAfter(List<Document> candidates,
                                                   List<RerankRequestCandidate> requests,
                                                   RerankExecutionReason reason,
                                                   RerankFailureStage failureStage,
                                                   List<RerankResultCandidate> results,
                                                   String error) {
            return new TransportResult(candidates, true, false, reason,
                failureStage, requests, results, error);
        }
    }
}
