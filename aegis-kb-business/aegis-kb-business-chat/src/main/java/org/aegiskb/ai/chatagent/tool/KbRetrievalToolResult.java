package org.aegiskb.ai.chatagent.tool;

import java.util.List;

/**
 * kb_search 单次调用的 Agent 可见结果。
 *
 * <p>仅作为搜索循环内模型判断"证据是否充分"的读取视图；不承载 reference id，不进入 final Source policy，
 * 最终答案与引用仍由 SAG 阶段2的统一权威管线产出。</p>
 */
public record KbRetrievalToolResult(
    String query,
    int callIndex,
    int candidateCount,
    List<Candidate> candidates
) {

    public KbRetrievalToolResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * 单条候选的只读预览。
     */
    public record Candidate(
        String documentName,
        double score,
        String textPreview
    ) {

        public Candidate {
            documentName = documentName == null ? "" : documentName;
            textPreview = textPreview == null ? "" : textPreview;
        }
    }
}
