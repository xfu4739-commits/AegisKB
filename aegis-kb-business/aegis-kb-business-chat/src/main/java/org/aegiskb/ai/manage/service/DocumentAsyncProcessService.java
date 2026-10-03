package org.aegiskb.ai.manage.service;

/**
 * 文档异步处理服务：消费 Kafka 或线程池触发的长耗时任务，回查数据库后执行解析路由与索引构建。
 * <p>
 * 消息体仅携带 ID，权威状态以 MySQL 中的 document / task 为准；进度与失败阶段通过 task_log 与 Redis 快照对外暴露。
 */
public interface DocumentAsyncProcessService {

    /**
     * 执行 PARSE_ROUTE 异步解析路由：从 MinIO 下载原文件，调用 Python 解析，持久化产物与结构节点，
     * 生成文档画像与推荐策略方案，并更新文档 parse/strategy 状态及任务进度。
     * <p>
     * 成功时文档进入 {@code PARSE_SUCCESS} / {@code RECOMMENDED}，任务 {@code SUCCESS}；
     * 失败时写入 {@code PARSE_FAILED}、任务 {@code FAILED} 并保留失败时的 {@code currentStage}。
     * 由 {@link org.aegiskb.ai.manage.mq.DocumentKafkaConsumer#consumeParseRoute} 调用。
     *
     * @param documentId 文档 ID
     * @param taskId     PARSE_ROUTE 任务 ID
     */
    void handleParseRoute(Long documentId, Long taskId);

    /**
     * 将 BUILD_INDEX 任务提交到本机后台线程池异步执行（不阻塞 Kafka 消费线程）。
     * <p>
     * 提交前校验任务存在且未处于终态；本机已有同 taskId 在执行时跳过，避免重复构建。
     * 实际构建逻辑委托给 {@link #handleIndexBuild(Long, Long, Long)}。
     * 由 {@link org.aegiskb.ai.manage.mq.DocumentKafkaConsumer#consumeIndexBuild} 调用。
     *
     * @param documentId 文档 ID
     * @param taskId     BUILD_INDEX 任务 ID
     * @param planId     已确认的策略方案 ID
     * @throws java.util.concurrent.RejectedExecutionException 线程池已满、无法提交时
     */
    void submitIndexBuild(Long documentId, Long taskId, Long planId);

    /**
     * 在当前调用线程同步执行索引构建：切块、后处理、向量化、关键词/GraphRAG/RAPTOR 等阶段，
     * 并更新文档 index 状态、任务阶段与构建进度缓存。
     * <p>
     * 与 {@link #submitIndexBuild} 共用 {@code doHandleIndexBuild} 核心逻辑；本机同 taskId 已在执行时直接返回。
     * 成功时文档 {@code BUILD_SUCCESS}，失败时 {@code BUILD_FAILED} 并记录失败阶段。
     *
     * @param documentId 文档 ID
     * @param taskId     BUILD_INDEX 任务 ID
     * @param planId     已确认的策略方案 ID
     */
    void handleIndexBuild(Long documentId, Long taskId, Long planId);
}
