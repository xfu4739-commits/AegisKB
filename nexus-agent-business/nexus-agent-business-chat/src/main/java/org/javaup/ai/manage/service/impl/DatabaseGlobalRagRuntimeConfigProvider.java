package org.javaup.ai.manage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.chatagent.rag.model.RagRuntimeOptions;
import org.javaup.ai.chatagent.rag.service.GlobalRagRuntimeConfigProvider;
import org.javaup.ai.manage.data.SuperAgentSystemConfig;
import org.javaup.ai.manage.mapper.SuperAgentSystemConfigMapper;
import org.javaup.ai.manage.model.SystemConfigSnapshot;
import org.javaup.ai.manage.service.SystemConfigProvider;
import org.javaup.ai.manage.support.SystemConfigSnapshotCodec;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DatabaseGlobalRagRuntimeConfigProvider implements GlobalRagRuntimeConfigProvider, SystemConfigProvider {

    private static final long CURRENT_CONFIG_ID = 1L;
    private final SuperAgentSystemConfigMapper configMapper;
    private final SystemConfigSnapshotCodec codec;
    private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean();
    private volatile SystemConfigSnapshot cachedSnapshot;

    public DatabaseGlobalRagRuntimeConfigProvider(SuperAgentSystemConfigMapper configMapper,
                                                  SystemConfigSnapshotCodec codec) {
        this.configMapper = configMapper;
        this.codec = codec;
    }

    @Override
    public RagRuntimeOptions currentOptions() {
        return currentSnapshot().getRagRuntime().deepCopy();
    }

    @Override
    public SystemConfigSnapshot currentSnapshot() {
        SystemConfigSnapshot cached = cachedSnapshot;
        if (cached != null) {
            return cached;
        }
        SystemConfigSnapshot fallback = SystemConfigSnapshot.defaults();
        try {
            SuperAgentSystemConfig row = configMapper.selectById(CURRENT_CONFIG_ID);
            if (row == null || row.getStatus() == null || row.getStatus() != 1) {
                warnFallback("数据库中没有启用的 RAG 运行时配置，请先执行初始化脚本。", null);
                cachedSnapshot = fallback;
                return cachedSnapshot;
            }
            cachedSnapshot = codec.deserialize(row.getConfigJson(), fallback);
            return cachedSnapshot;
        }
        catch (RuntimeException exception) {
            warnFallback("数据库 RAG 运行时配置不可用，当前进程使用内置应急默认值。", exception);
            cachedSnapshot = fallback;
            return cachedSnapshot;
        }
    }

    @Override
    public void invalidate() {
        cachedSnapshot = null;
    }

    private void warnFallback(String message, RuntimeException exception) {
        if (!fallbackWarningLogged.compareAndSet(false, true)) {
            return;
        }
        if (exception == null) {
            log.warn(message);
        }
        else {
            log.warn(message, exception);
        }
    }
}
