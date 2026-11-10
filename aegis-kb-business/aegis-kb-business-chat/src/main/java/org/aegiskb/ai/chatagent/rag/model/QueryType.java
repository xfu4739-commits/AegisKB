package org.aegiskb.ai.chatagent.rag.model;

public enum QueryType {

    DOCUMENT_QA,

    FOLLOW_UP,

    STRUCTURE_NAVIGATION,

    TABLE_QUERY,

    GRAPH_RELATION,

    GLOBAL_SUMMARY,

    /**
     * 多跳研究：需要迭代检索（多次改写查询、逐步取证），或需要把内部文档证据与外部时效性事实对照才能回答。
     * 只由受控 LLM advisor 在高置信度下产出；确定性 fallback 绝不产出该类型，未达置信度时 fail closed 回 DOCUMENT_QA。
     * 命中后由 {@code DocumentQuestionRouter} 翻译为 {@code ExecutionMode.SAG}，进入 SAG 搜索循环。
     */
    MULTI_HOP_RESEARCH,

    OPEN_CHAT,

    CAPABILITY_QUERY
}
