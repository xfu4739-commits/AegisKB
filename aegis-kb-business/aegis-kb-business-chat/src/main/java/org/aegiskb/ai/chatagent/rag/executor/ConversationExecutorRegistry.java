package org.aegiskb.ai.chatagent.rag.executor;

import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ConversationExecutorRegistry {

    private final Map<ExecutionMode, ConversationExecutor> executorMap = new EnumMap<>(ExecutionMode.class);

    public ConversationExecutorRegistry(List<ConversationExecutor> executors) {
        for (ConversationExecutor executor : executors) {
            executorMap.put(executor.mode(), executor);
        }
    }

    public ConversationExecutor get(ExecutionMode mode) {
        ConversationExecutor executor = executorMap.get(mode);
        if (executor == null) {
            throw new IllegalStateException("未找到执行模式对应的执行器: " + mode);
        }
        return executor;
    }
}
