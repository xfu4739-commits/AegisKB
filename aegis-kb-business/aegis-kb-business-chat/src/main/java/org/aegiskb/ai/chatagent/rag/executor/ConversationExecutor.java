package org.aegiskb.ai.chatagent.rag.executor;

import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.aegiskb.ai.chatagent.service.TaskInfo;
import reactor.core.publisher.Flux;

public interface ConversationExecutor {

    ExecutionMode mode();

    Flux<String> execute(TaskInfo taskInfo);
}
