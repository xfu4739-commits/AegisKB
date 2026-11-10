package org.aegiskb.ai.chatagent.tool;

import org.aegiskb.ai.chatagent.rag.model.RetrievalPlan;
import org.aegiskb.ai.chatagent.rag.service.RagRetrievalEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SAG 一次 exchange 内 kb_search 的冻结会话状态。
 *
 * <p>由 SAG 执行器在循环开始前用冻结 {@link RetrievalPlan} 构造并写入 ToolContext；kb_search 每次调用只新增
 * 查询文本，scope/channel/预算/排序特征一律沿用冻结 Plan，逐次调用结果按调用顺序累积，供阶段2合并窗口使用。</p>
 *
 * <p>构造即校验冻结 Plan 可执行性：越界或缺失 scope 的 Plan 在会话建立时就 fail closed，不会进入检索循环。</p>
 */
public class KbRetrievalSession {

    private final RetrievalPlan frozenPlan;
    private final AtomicInteger callSequence = new AtomicInteger(0);
    private final List<RagRetrievalEngine.RetrievalStageResult> stageResults =
        Collections.synchronizedList(new ArrayList<>());
    private final List<String> usedChannels = Collections.synchronizedList(new ArrayList<>());
    private final List<String> notes = Collections.synchronizedList(new ArrayList<>());

    public KbRetrievalSession(RetrievalPlan frozenPlan) {
        this.frozenPlan = Objects.requireNonNull(frozenPlan, "kb_search 会话需要冻结 RetrievalPlan");
        this.frozenPlan.validateForExecution();
    }

    public RetrievalPlan getFrozenPlan() {
        return frozenPlan;
    }

    public int nextCallIndex() {
        return callSequence.incrementAndGet();
    }

    public void record(RagRetrievalEngine.RetrievalStageResult stageResult) {
        Objects.requireNonNull(stageResult, "stageResult");
        stageResults.add(stageResult);
    }

    public List<RagRetrievalEngine.RetrievalStageResult> getStageResults() {
        return stageResults;
    }

    public List<String> getUsedChannels() {
        return usedChannels;
    }

    public List<String> getNotes() {
        return notes;
    }
}
