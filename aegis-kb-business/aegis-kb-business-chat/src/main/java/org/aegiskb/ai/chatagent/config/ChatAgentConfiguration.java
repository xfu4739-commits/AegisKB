package org.aegiskb.ai.chatagent.config;

import javax.sql.DataSource;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import org.aegiskb.ai.chatagent.support.DashScopeCompatibilityInterceptor;
import org.aegiskb.ai.chatagent.support.TavilyToolInputFallbackInterceptor;
import org.aegiskb.ai.chatagent.tool.KbRetrievalRequest;
import org.aegiskb.ai.chatagent.tool.KbRetrievalTool;
import org.aegiskb.ai.chatagent.tool.TavilySearchRequest;
import org.aegiskb.ai.chatagent.tool.TavilySearchTool;
import org.aegiskb.ai.prompt.PromptTemplateNames;
import org.aegiskb.ai.prompt.PromptTemplateService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@EnableConfigurationProperties({ChatAgentProperties.class, TavilySearchProperties.class})
public class ChatAgentConfiguration {

    @Bean
    public MysqlSaver mysqlCheckpointSaver(DataSource dataSource) {

        return MysqlSaver.builder()
            .dataSource(dataSource)
            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
            .build();
    }

    @Bean
    public ToolCallback tavilySearchToolCallback(TavilySearchTool tavilySearchTool) {

        return FunctionToolCallback
            .builder("tavily_search", tavilySearchTool::search)
            .description("联网搜索最新信息、事实资料和网页来源。调用时必须传 JSON 参数，且至少包含非空 query；可选 topic 和 maxResults，其中 topic 仅允许 general、news、finance。")
            .inputType(TavilySearchRequest.class)
            .build();
    }

    @Bean
    public ReactAgent businessChatReactAgent(ChatModel chatModel,
                                             MysqlSaver mysqlCheckpointSaver,
                                             @Qualifier("tavilySearchToolCallback") ToolCallback tavilySearchToolCallback,
                                             ChatAgentProperties chatAgentProperties,
                                             PromptTemplateService promptTemplateService,
                                             DashScopeCompatibilityInterceptor dashScopeCompatibilityInterceptor,
                                             TavilyToolInputFallbackInterceptor tavilyToolInputFallbackInterceptor) {
        return ReactAgent.builder()

            .name("business_chat_agent")
            .model(chatModel)
            .instruction(promptTemplateService.render(PromptTemplateNames.CHAT_AGENT_SYSTEM, Map.of()))

            .tools(tavilySearchToolCallback)
            .saver(mysqlCheckpointSaver)

            .parallelToolExecution(true)
            .maxParallelTools(4)

            .hooks(
                ModelCallLimitHook.builder()
                    .runLimit(chatAgentProperties.getMaxModelCallsPerRun())
                    .threadLimit(chatAgentProperties.getMaxModelCallsPerThread())
                    .exitBehavior(ModelCallLimitHook.ExitBehavior.END)
                    .build(),
                ToolCallLimitHook.builder()
                    .toolName("tavily_search")
                    .runLimit(chatAgentProperties.getMaxToolCallsPerRun())
                    .threadLimit(chatAgentProperties.getMaxToolCallsPerThread())
                    .exitBehavior(ToolCallLimitHook.ExitBehavior.END)
                    .build()
            )

            .interceptors(
                dashScopeCompatibilityInterceptor,
                tavilyToolInputFallbackInterceptor,
                ToolRetryInterceptor.builder()
                    .toolName("tavily_search")
                    .maxRetries(2)
                    .initialDelay(200L)
                    .maxDelay(1200L)
                    .jitter(true)
                    .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                    .build(),
                ToolErrorInterceptor.builder().build()
            )
            .build();
    }

    @Bean
    public ReactAgent sagChatReactAgent(ChatModel chatModel,
                                        MysqlSaver mysqlCheckpointSaver,
                                        KbRetrievalTool kbRetrievalTool,
                                        @Qualifier("tavilySearchToolCallback") ToolCallback tavilySearchToolCallback,
                                        ChatAgentProperties chatAgentProperties,
                                        PromptTemplateService promptTemplateService,
                                        DashScopeCompatibilityInterceptor dashScopeCompatibilityInterceptor,
                                        TavilyToolInputFallbackInterceptor tavilyToolInputFallbackInterceptor) {
        // kb_search 只属于 SAG agent：内联构造，不注册为全局 ToolCallback bean，否则会被 Spring AI
        // toolCallbackResolver 收集，经 KbRetrievalTool→RagRetrievalEngine→ObservedChatModelService 回到 ChatModel 形成循环依赖。
        ToolCallback kbSearchToolCallback = FunctionToolCallback
            .builder("kb_search", kbRetrievalTool::search)
            .description("检索当前选定的内部知识库，返回与查询相关的证据候选列表。调用时必须传 JSON 参数，且至少包含非空 query。检索范围、通道与预算由系统冻结计划统一控制，你只需要提供想检索的查询文本；可多次调用，每次使用不同的查询文本。")
            .inputType(KbRetrievalRequest.class)
            .build();
        return ReactAgent.builder()

            .name("sag_chat_agent")
            .model(chatModel)
            .instruction(promptTemplateService.render(PromptTemplateNames.SAG_AGENT_SYSTEM, Map.of()))

            .tools(kbSearchToolCallback, tavilySearchToolCallback)
            .saver(mysqlCheckpointSaver)

            // SAG 循环内 kb_search 必须串行执行，保证会话内调用顺序与合并窗口保序去重确定性。
            .parallelToolExecution(false)

            .hooks(
                ModelCallLimitHook.builder()
                    .runLimit(chatAgentProperties.getSagMaxModelCallsPerRun())
                    .threadLimit(chatAgentProperties.getSagMaxModelCallsPerThread())
                    .exitBehavior(ModelCallLimitHook.ExitBehavior.END)
                    .build(),
                ToolCallLimitHook.builder()
                    .toolName("kb_search")
                    .runLimit(chatAgentProperties.getSagMaxKbSearchCallsPerRun())
                    .threadLimit(chatAgentProperties.getSagMaxKbSearchCallsPerThread())
                    .exitBehavior(ToolCallLimitHook.ExitBehavior.END)
                    .build(),
                ToolCallLimitHook.builder()
                    .toolName("tavily_search")
                    .runLimit(chatAgentProperties.getSagMaxWebSearchCallsPerRun())
                    .threadLimit(chatAgentProperties.getSagMaxWebSearchCallsPerThread())
                    .exitBehavior(ToolCallLimitHook.ExitBehavior.END)
                    .build()
            )

            .interceptors(
                dashScopeCompatibilityInterceptor,
                tavilyToolInputFallbackInterceptor,
                ToolRetryInterceptor.builder()
                    .toolName("kb_search")
                    .maxRetries(1)
                    .initialDelay(200L)
                    .maxDelay(800L)
                    .jitter(true)
                    .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                    .build(),
                ToolRetryInterceptor.builder()
                    .toolName("tavily_search")
                    .maxRetries(2)
                    .initialDelay(200L)
                    .maxDelay(1200L)
                    .jitter(true)
                    .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                    .build(),
                ToolErrorInterceptor.builder().build()
            )
            .build();
    }
}
