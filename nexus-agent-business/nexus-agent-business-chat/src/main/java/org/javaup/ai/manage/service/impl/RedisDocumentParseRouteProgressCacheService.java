package org.javaup.ai.manage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.manage.data.SuperAgentDocument;
import org.javaup.ai.manage.data.SuperAgentDocumentStrategyPlan;
import org.javaup.ai.manage.data.SuperAgentDocumentStrategyStep;
import org.javaup.ai.manage.data.SuperAgentDocumentTask;
import org.javaup.ai.manage.data.SuperAgentDocumentTaskLog;
import org.javaup.ai.manage.service.DocumentParseRouteProgressCacheService;
import org.javaup.ai.manage.vo.DocumentParseRouteProgressVo;
import org.javaup.ai.manage.vo.DocumentTaskLogVo;
import org.javaup.core.RedisKeyManage;
import org.javaup.enums.DocumentLogLevelEnum;
import org.javaup.enums.DocumentTaskEventTypeEnum;
import org.javaup.enums.DocumentTaskStageEnum;
import org.javaup.redis.RedisCache;
import org.javaup.redis.RedisKeyBuild;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor
@Service
public class RedisDocumentParseRouteProgressCacheService implements DocumentParseRouteProgressCacheService {

    private static final long TTL_HOURS = 5L;
    private static final int MAX_LOG_SIZE = 80;

    private final RedisCache redisCache;

    private final ObjectMapper objectMapper;

    @Override
    public DocumentParseRouteProgressVo get(Long taskId) {
        if (taskId == null) {
            return null;
        }
        try {
            return redisCache.get(key(taskId), DocumentParseRouteProgressVo.class);
        }
        catch (Exception exception) {
            log.warn("读取解析路由 Redis 进度快照失败，taskId={}, message={}", taskId, exception.getMessage());
            return null;
        }
    }

    @Override
    public void init(SuperAgentDocument document, SuperAgentDocumentTask task) {
        update(document, task, null, List.of(), null);
    }

    @Override
    public void update(SuperAgentDocument document, SuperAgentDocumentTask task) {
        update(document, task, null, List.of(), null);
    }

    @Override
    public void update(SuperAgentDocument document, SuperAgentDocumentTask task, SuperAgentDocumentTaskLog latestLog) {
        update(document, task, null, List.of(), latestLog);
    }

    @Override
    public void update(SuperAgentDocument document,
                       SuperAgentDocumentTask task,
                       SuperAgentDocumentStrategyPlan plan,
                       List<SuperAgentDocumentStrategyStep> steps,
                       SuperAgentDocumentTaskLog latestLog) {
        // 1：入参校验；document / task / taskId 缺失时直接返回，不写 Redis。
        if (document == null || task == null || task.getId() == null) {
            return;
        }
        try {
            // 2：读取 Redis 中该任务的上一次进度快照；MySQL 任务日志仍是长期事实来源。
            DocumentParseRouteProgressVo previous = get(task.getId());
            // 3：继承已有日志列表，若本次携带 latestLog 则追加到末尾。
            List<DocumentTaskLogVo> logs = previous == null || previous.getLogs() == null
                ? new ArrayList<>() : new ArrayList<>(previous.getLogs());
            if (latestLog != null) {
                logs.add(toLogVo(latestLog));
            }
            // 4：日志去重并裁剪，快照最多保留 MAX_LOG_SIZE 条。
            logs = DocumentParseRouteProgressAssembler.deduplicateAndTrimLogs(logs, MAX_LOG_SIZE);
            // 5：计算快照中最新一条日志的 ID，供前端增量拉取。
            Long latestLogId = DocumentParseRouteProgressAssembler.latestLogId(logs);
            // 6：累计总日志条数；仅当 latestLog 为 Redis 中尚未出现的新日志时才 +1。
            long totalLogCount = previous == null || previous.getTotalLogCount() == null ? 0L : previous.getTotalLogCount();
            if (latestLog != null && (previous == null || previous.getLogs() == null
                || previous.getLogs().stream().noneMatch(item -> Objects.equals(item.getId(), latestLog.getId())))) {
                totalLogCount++;
            }

            // 7：用当前 document / task / plan / steps 与合并后的日志组装新的进度视图。
            DocumentParseRouteProgressVo snapshot = DocumentParseRouteProgressAssembler.build(
                document,
                task,
                plan,
                steps == null ? List.of() : steps,
                logs,
                totalLogCount,
                objectMapper
            );
            snapshot.setLatestLogId(latestLogId);
            // 8：写入 Redis，TTL 为 TTL_HOURS 小时。
            redisCache.set(key(task.getId()), snapshot, TTL_HOURS, TimeUnit.HOURS);
        }
        catch (Exception exception) {
            // 缓存是加速层，写入失败不能反向打断解析主流程。
            log.warn("写入解析路由 Redis 进度快照失败，documentId={}, taskId={}, message={}",
                document.getId(), task.getId(), exception.getMessage());
        }
    }

    private DocumentTaskLogVo toLogVo(SuperAgentDocumentTaskLog logRecord) {
        return new DocumentTaskLogVo(
            logRecord.getId(),
            logRecord.getStageType(),
            stageName(logRecord.getStageType()),
            logRecord.getEventType(),
            eventTypeName(logRecord.getEventType()),
            logRecord.getLogLevel(),
            logLevelName(logRecord.getLogLevel()),
            logRecord.getContent(),
            logRecord.getDetailJson(),
            logRecord.getCreateTime()
        );
    }

    private RedisKeyBuild key(Long taskId) {
        return RedisKeyBuild.createRedisKey(RedisKeyManage.DOCUMENT_PARSE_ROUTE_PROGRESS, taskId);
    }

    private String stageName(Integer stage) {
        DocumentTaskStageEnum item = stage == null ? null : DocumentTaskStageEnum.getRc(stage);
        return item == null ? "" : item.getMsg();
    }

    private String eventTypeName(Integer eventType) {
        DocumentTaskEventTypeEnum item = eventType == null ? null : DocumentTaskEventTypeEnum.getRc(eventType);
        return item == null ? "" : item.getMsg();
    }

    private String logLevelName(Integer logLevel) {
        DocumentLogLevelEnum item = logLevel == null ? null : DocumentLogLevelEnum.getRc(logLevel);
        return item == null ? "" : item.getMsg();
    }
}
