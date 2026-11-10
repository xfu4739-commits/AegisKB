package org.aegiskb.ai.chatagent.rag.model;

public enum ExecutionMode {

    /**
     * 普通知识库检索问答模式。
     *
     * <p>适用于大多数需要基于知识文档内容回答的问题。该模式由 {@code RagChatExecutor} 执行，会根据规划阶段
     * 得到的检索问题、子问题、文档范围，走向量检索、关键词检索、weighted hybrid 融合、父块提升、可选 rerank、Prompt
     * 预算组装，然后调用模型基于证据流式生成答案。</p>
     */
    RETRIEVAL,

    /**
     * 开放式 ReAct Agent 模式。
     *
     * <p>适用于固定 RAG 或结构图路径无法覆盖的问题，或者需要 Agent 自主判断是否调用工具的场景。
     * 该模式由 {@code ReactAgentExecutor} 执行，会把规划后的 agentQuestion 交给 ReAct Agent，
     * 由 Agent 自主进行推理、工具调用和最终回答输出。</p>
     */
    REACT_AGENT,

    /**
     * SAG（Search-Augmented Generation）两阶段搜索模式。
     *
     * <p>适用于需要在预算内自主决定检索什么、检索几次的知识库问答场景。该模式由 {@code SagChatExecutor}
     * 执行：阶段1由 SAG Agent 在循环中自主调用 kb_search（内部知识库检索）与 tavily_search（web 搜索，
     * 仅 Context Only）收集证据；阶段2把各次调用的 rerank 窗口合并去重后，交给唯一权威管线
     * （FinalEvidenceSelectionPolicy 一次选择 → Prompt manifest → 流式生成 → 显式 citation binding）。
     * 循环内的模型中间文本不进入 answer 流。</p>
     */
    SAG,

    /**
     * 澄清模式。
     *
     * <p>适用于路由阶段发现候选文档、知识范围或用户意图存在歧义，暂时不能稳定选择某个执行路径的场景。
     * 该模式由 {@code ClarificationExecutor} 执行，不进行检索或模型生成，而是直接返回澄清问题，
     * 引导用户补充更明确的文档名、主题或关键词。</p>
     */
    CLARIFICATION
}
