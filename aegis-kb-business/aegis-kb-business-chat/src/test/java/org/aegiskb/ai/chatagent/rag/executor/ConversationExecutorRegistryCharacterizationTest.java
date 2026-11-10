package org.aegiskb.ai.chatagent.rag.executor;

import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.aegiskb.ai.chatagent.service.TaskInfo;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S19-P1 characterization：冻结 executor 注册与分发契约。
 *
 * <p>S19-P4 新增 SAG 模式时，registry 必须保持"未注册模式 fail closed"。</p>
 */
class ConversationExecutorRegistryCharacterizationTest {

    private static ConversationExecutor stub(ExecutionMode mode) {
        return new ConversationExecutor() {
            @Override
            public ExecutionMode mode() {
                return mode;
            }

            @Override
            public Flux<String> execute(TaskInfo taskInfo) {
                return Flux.empty();
            }
        };
    }

    @Test
    void getReturnsExecutorRegisteredForMode() {
        ConversationExecutor retrieval = stub(ExecutionMode.RETRIEVAL);
        ConversationExecutor reactAgent = stub(ExecutionMode.REACT_AGENT);
        ConversationExecutorRegistry registry = new ConversationExecutorRegistry(List.of(retrieval, reactAgent));

        assertThat(registry.get(ExecutionMode.RETRIEVAL)).isSameAs(retrieval);
        assertThat(registry.get(ExecutionMode.REACT_AGENT)).isSameAs(reactAgent);
    }

    @Test
    void getFailsClosedForUnregisteredMode() {
        ConversationExecutorRegistry registry = new ConversationExecutorRegistry(List.of(stub(ExecutionMode.RETRIEVAL)));

        assertThatThrownBy(() -> registry.get(ExecutionMode.CLARIFICATION))
            .isInstanceOf(IllegalStateException.class);
    }
}
