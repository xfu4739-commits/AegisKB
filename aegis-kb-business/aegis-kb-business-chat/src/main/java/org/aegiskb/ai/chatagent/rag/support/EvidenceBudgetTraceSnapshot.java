package org.aegiskb.ai.chatagent.rag.support;

import cn.hutool.core.util.StrUtil;
import org.aegiskb.ai.chatagent.rag.model.ObservationPersistence;
import org.aegiskb.ai.chatagent.rag.model.RagPromptAssemblyResult;
import org.aegiskb.ai.chatagent.rag.model.RagRetrievalContext;
import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionRequest;
import org.aegiskb.ai.chatagent.rag.model.SubQuestionEvidence;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EVIDENCE_BUDGET trace 快照的唯一权威投影：RETRIEVAL 与 SAG 执行器共用，
 * 保证两种模式的证据预算快照 schema 与 scope 校验完全一致。
 */
public final class EvidenceBudgetTraceSnapshot {

    private EvidenceBudgetTraceSnapshot() {
    }

    public static Map<String, Object> build(RagRetrievalContext context,
                                            RagPromptAssemblyResult promptAssemblyResult) {
        List<SubQuestionEvidence> evidence = context == null || context.getSubQuestionEvidenceList() == null
            ? List.of()
            : context.getSubQuestionEvidenceList().stream()
                .filter(item -> item != null)
                .sorted(Comparator.comparingInt(SubQuestionEvidence::getSubQuestionIndex))
                .toList();
        validateEvidenceBudgetScope(context, evidence);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", "S09");
        snapshot.put("subQuestionCount", evidence.size());
        snapshot.put("subQuestions", evidence.stream().map(EvidenceBudgetTraceSnapshot::subQuestionBlock).toList());
        snapshot.put("totalBudget", promptAssemblyResult == null ? 0 : promptAssemblyResult.getTotalBudget());
        snapshot.put("perSubQuestionBudget", promptAssemblyResult == null ? 0 : promptAssemblyResult.getPerSubQuestionBudget());
        snapshot.put("inputReferenceCount", promptAssemblyResult == null ? 0 : promptAssemblyResult.getInputReferenceCount());
        snapshot.put("referenceManifest", promptAssemblyResult == null ? List.of() : promptAssemblyResult.getReferenceManifest());
        snapshot.put("renderedSourceIdentities", promptAssemblyResult == null ? List.of() : promptAssemblyResult.getRenderedSourceIdentities());
        snapshot.put("renderedContextIdentities", promptAssemblyResult == null ? List.of() : promptAssemblyResult.getRenderedContextIdentities());
        snapshot.put("explicitCitationEligibleIdentities", promptAssemblyResult == null ? List.of() : promptAssemblyResult.getRenderedSourceIdentities());
        snapshot.put("answerShapeRequirements", promptAssemblyResult == null
            ? List.of()
            : promptAssemblyResult.getAnswerShapeRequirements().stream().map(Enum::name).toList());
        snapshot.put("PROMPT_RENDERED_CONTEXT", promptAssemblyResult == null ? List.of() : promptAssemblyResult.getRenderedContextIdentities());
        snapshot.put("systemPrompt", promptAssemblyResult == null ? "" : StrUtil.blankToDefault(promptAssemblyResult.getSystemPrompt(), ""));
        snapshot.put("userPrompt", promptAssemblyResult == null ? "" : StrUtil.blankToDefault(promptAssemblyResult.getUserPrompt(), ""));
        return snapshot;
    }

    public static ObservationPersistence requireObservationPersistence(SubQuestionEvidence evidence) {
        if (evidence.getObservationPersistence() == null) {
            throw new IllegalStateException("Every sub-question requires an observation persistence outcome");
        }
        return evidence.getObservationPersistence();
    }

    private static Map<String, Object> subQuestionBlock(SubQuestionEvidence evidence) {
        if (evidence.getEvidenceSelectionLedger() == null) {
            throw new IllegalStateException("Every sub-question requires a complete evidence selection ledger");
        }
        var ledger = evidence.getEvidenceSelectionLedger();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("subQuestionIndex", evidence.getSubQuestionIndex());
        block.put("executionQuery", StrUtil.blankToDefault(evidence.getSubQuestion(), ""));
        block.put("observationPersistence", requireObservationPersistence(evidence));
        block.put("evidenceLineage", ledger.evidenceLineage());
        block.put("rerankExecution", ledger.rerankExecution());
        block.put("sourceRankingWindowDecisions", ledger.sourceRankingWindowDecisions());
        block.put("sourceRankingWindowCandidates", ledger.sourceRankingWindowCandidates());
        block.put("rerankRequestCandidates", ledger.rerankRequestCandidates());
        block.put("rerankResultCandidates", ledger.rerankResultCandidates());
        block.put("identityTransitions", ledger.identityTransitions());
        block.put("finalEvidenceDecisions", ledger.finalEvidenceDecisions());
        return block;
    }

    private static void validateEvidenceBudgetScope(RagRetrievalContext context, List<SubQuestionEvidence> evidence) {
        List<Integer> evidenceIndexes = evidence.stream().map(SubQuestionEvidence::getSubQuestionIndex).toList();
        if (evidenceIndexes.stream().anyMatch(index -> index == null || index <= 0)
            || evidenceIndexes.stream().distinct().count() != evidenceIndexes.size()) {
            throw new IllegalStateException("EVIDENCE_BUDGET subQuestionIndex values must be positive and unique");
        }
        List<Integer> requestIndexes = context == null || context.getExecutionRequests() == null
            ? List.of()
            : context.getExecutionRequests().stream()
                .map(RetrievalExecutionRequest::subQuestionIndex)
                .sorted()
                .toList();
        if (!requestIndexes.equals(evidenceIndexes)) {
            throw new IllegalStateException("EVIDENCE_BUDGET blocks must match execution request indexes");
        }
    }
}
