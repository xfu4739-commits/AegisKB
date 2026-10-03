package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocument;
import org.aegiskb.ai.manage.data.SuperAgentDocumentStrategyPlan;
import org.aegiskb.ai.manage.data.SuperAgentDocumentStrategyStep;
import org.aegiskb.ai.manage.data.SuperAgentDocumentTask;
import org.aegiskb.ai.manage.data.SuperAgentDocumentTaskLog;
import org.aegiskb.ai.manage.vo.DocumentParseRouteProgressVo;

import java.util.List;

/**
 * 解析路由（PARSE_ROUTE）进度热缓存服务，供前端轮询 {@code queryParseRouteProgress} 时快速读取聚合快照。
 * <p>
 * Redis 仅缓存最近若干条任务日志与当前 document / task / plan 状态；MySQL 中的 task、task_log 仍是长期事实来源。
 * 写入失败不得反向打断解析主流程。默认实现见 {@link org.aegiskb.ai.manage.service.impl.RedisDocumentParseRouteProgressCacheService}。
 */
public interface DocumentParseRouteProgressCacheService {

    /**
     * 按任务 ID 读取 Redis 中的解析路由进度快照；不存在或读取失败时返回 {@code null}。
     *
     * @param taskId PARSE_ROUTE 任务 ID
     * @return 进度聚合视图，未命中缓存时为 {@code null}
     */
    DocumentParseRouteProgressVo get(Long taskId);

    /**
     * 初始化或刷新进度快照：以当前 document / task 状态重建缓存，不追加新日志。
     * <p>
     * 若 Redis 中已有快照，则保留其中日志列表，仅更新任务阶段、文档解析状态等字段。
     * 典型调用点：Kafka 消费 {@code handleParseRoute} 进入 CONTENT_PARSE 时。
     *
     * @param document 文档实体（含 parseStatus 等）
     * @param task     PARSE_ROUTE 任务实体（含 taskStatus、currentStage 等）
     */
    void init(SuperAgentDocument document, SuperAgentDocumentTask task);

    /**
     * 刷新进度快照，不追加新日志；语义与 {@link #init} 相同，用于解析流程中的阶段性状态同步。
     *
     * @param document 文档实体
     * @param task     PARSE_ROUTE 任务实体
     */
    void update(SuperAgentDocument document, SuperAgentDocumentTask task);

    /**
     * 刷新进度快照，并将 {@code latestLog} 合并进缓存中的日志列表（去重后最多保留 80 条）。
     * <p>
     * 典型调用点：文档上传事务提交后写入首条「文件上传完成」日志。
     *
     * @param document  文档实体
     * @param task      PARSE_ROUTE 任务实体
     * @param latestLog 本次新增的任务日志；为 {@code null} 时等价于两参数 {@link #update}
     */
    void update(SuperAgentDocument document, SuperAgentDocumentTask task, SuperAgentDocumentTaskLog latestLog);

    /**
     * 刷新进度快照，并附带策略方案、步骤列表与最新任务日志。
     * <p>
     * 典型调用点：策略推荐完成或解析路由收尾时，向前端暴露 plan / steps 与完整进度。
     *
     * @param document  文档实体
     * @param task      PARSE_ROUTE 任务实体
     * @param plan        当前策略方案；可为 {@code null}
     * @param steps       方案步骤列表；可为 {@code null} 或空列表
     * @param latestLog   本次新增的任务日志；可为 {@code null}
     */
    void update(SuperAgentDocument document,
                SuperAgentDocumentTask task,
                SuperAgentDocumentStrategyPlan plan,
                List<SuperAgentDocumentStrategyStep> steps,
                SuperAgentDocumentTaskLog latestLog);
}
