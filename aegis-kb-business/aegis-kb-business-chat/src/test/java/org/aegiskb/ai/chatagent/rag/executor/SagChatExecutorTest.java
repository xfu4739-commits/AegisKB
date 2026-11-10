package org.aegiskb.ai.chatagent.rag.executor;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.aegiskb.ai.chatagent.model.SearchReference;
import org.aegiskb.ai.chatagent.model.debug.ChatDebugTrace;
import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.aegiskb.ai.chatagent.rag.model.ConversationExecutionPlan;
import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.aegiskb.ai.chatagent.rag.model.RagPromptAssemblyResult;
import org.aegiskb.ai.chatagent.rag.model.RagRetrievalContext;
import org.aegiskb.ai.chatagent.rag.model.SubQuestionEvidence;
import org.aegiskb.ai.chatagent.rag.service.RagPromptAssemblyService;
import org.aegiskb.ai.chatagent.rag.service.RagRetrievalEngine;
import org.aegiskb.ai.chatagent.service.ObservedChatModelService;
import org.aegiskb.ai.chatagent.service.TaskInfo;
import org.aegiskb.ai.chatagent.support.ChatContextKeys;
import org.aegiskb.ai.chatagent.support.StreamEventWriter;
import org.aegiskb.ai.chatagent.tool.KbRetrievalSession;
import org.aegiskb.enums.ChatQueryMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S19-P4 目标不变量：SAG 两阶段执行分支。
 *
 * <p>循环内模型中间文本不得进入 answer 流；循环失败或预算耗尽时阶段2仍完成兜底；
 * 冻结会话在循环开始前写入 RunnableConfig context。</p>
 */
class SagChatExecutorTest {

    private RagRetrievalEngine ragRetrievalEngine;
    private RagPromptAssemblyService ragPromptAssemblyService;
    private StreamEventWriter streamEventWriter;
    private ObservedChatModelService observedChatModelService;
    private ReactAgent sagChatReactAgent;
    private SagChatExecutor executor;

    @BeforeEach
    void setUp() {
        ragRetrievalEngine = mock(RagRetrievalEngine.class);
        ragPromptAssemblyService = mock(RagPromptAssemblyService.class);
        streamEventWriter = mock(StreamEventWriter.class);
        observedChatModelService = mock(ObservedChatModelService.class);
        sagChatReactAgent = mock(ReactAgent.class);
        executor = new SagChatExecutor(
            ragRetrievalEngine,
            ragPromptAssemblyService,
            streamEventWriter,
            observedChatModelService,
            sagChatReactAgent
        );
    }

    @Test
    void modeIsSag() {
        assertThat(executor.mode()).isEqualTo(ExecutionMode.SAG);
    }

    @Test
    void loopTextNeverEntersAnswerStreamAndEmptyEvidenceFallsBackToNoEvidenceReply() throws Exception {
        TaskInfo taskInfo = taskInfo();
        when(sagChatReactAgent.stream(anyString(), any(RunnableConfig.class)))
            .thenReturn(Flux.<NodeOutput>just(mock(StreamingOutput.class), mock(StreamingOutput.class)));
        when(ragRetrievalEngine.finalizeSagContext(any(), anyList(), anyList(), anyList(), any()))
            .thenReturn(new RagRetrievalContext());

        List<String> answers = executor.execute(taskInfo).collectList().block();

        assertThat(answers).containsExactly("fixture 无证据兜底");
        verify(sagChatReactAgent).stream(eq("fixture agent question"), any(RunnableConfig.class));
        Object seeded = taskInfo.runnableConfig().context().get(ChatContextKeys.KB_RETRIEVAL_SESSION);
        assertThat(seeded).isInstanceOf(KbRetrievalSession.class);
        assertThat(((KbRetrievalSession) seeded).getFrozenPlan()).isSameAs(taskInfo.executionPlan().getRetrievalPlan());
    }

    @Test
    void loopFailureStillFallsBackToNoEvidenceReply() throws Exception {
        TaskInfo taskInfo = taskInfo();
        when(sagChatReactAgent.stream(anyString(), any(RunnableConfig.class)))
            .thenReturn(Flux.error(new IllegalStateException("SAG loop failure")));
        when(ragRetrievalEngine.finalizeSagContext(any(), anyList(), anyList(), anyList(), any()))
            .thenReturn(new RagRetrievalContext());

        List<String> answers = executor.execute(taskInfo).collectList().block();

        assertThat(answers).containsExactly("fixture 无证据兜底");
        assertThat(taskInfo.thinkingSteps())
            .anySatisfy(step -> assertThat(step).contains("SAG 搜索循环"));
    }

    @Test
    void phase2StreamsAnswerFromMergedEvidenceOnly() throws Exception {
        TaskInfo taskInfo = taskInfo();
        when(sagChatReactAgent.stream(anyString(), any(RunnableConfig.class)))
            .thenReturn(Flux.<NodeOutput>just(mock(StreamingOutput.class)));

        RagRetrievalContext context = new RagRetrievalContext();
        context.setRetrievalQuestion("fixture normalized query");
        context.setUsedChannels(new ArrayList<>(List.of("FIXTURE_CHANNEL_ALPHA")));
        context.setRetrievalNotes(new ArrayList<>(List.of("fixture 合并窗口检索完成")));
        SubQuestionEvidence evidence = new SubQuestionEvidence();
        evidence.setSubQuestionIndex(1);
        evidence.setReferences(new ArrayList<>(List.of(new SearchReference())));
        context.setSubQuestionEvidenceList(new ArrayList<>(List.of(evidence)));
        when(ragRetrievalEngine.finalizeSagContext(any(), anyList(), anyList(), anyList(), any()))
            .thenReturn(context);

        RagPromptAssemblyResult assemblyResult = new RagPromptAssemblyResult(
            "fixture system prompt", "fixture user prompt", 0, 0, 0, List.of(), List.of(), List.of());
        when(ragPromptAssemblyService.assemble(any(), any())).thenReturn(assemblyResult);
        when(observedChatModelService.streamText(eq("rag_answer"), anyString(), anyString(), any()))
            .thenReturn(Flux.just("最终答案", "第二段"));

        List<String> answers = executor.execute(taskInfo).collectList().block();

        assertThat(answers).containsExactly("最终答案", "第二段");
        assertThat(taskInfo.promptAssemblyResult()).isSameAs(assemblyResult);
        assertThat(taskInfo.usedTools()).contains("FIXTURE_CHANNEL_ALPHA");
        assertThat(taskInfo.debugTrace().getUsedChannels()).containsExactly("FIXTURE_CHANNEL_ALPHA");
        assertThat(taskInfo.debugTrace().getRetrievalNotes()).containsExactly("fixture 合并窗口检索完成");
    }

    private TaskInfo taskInfo() {
        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .mode(ExecutionMode.SAG)
            .chatMode(ChatQueryMode.DOCUMENT)
            .agentQuestion("fixture agent question")
            .retrievalPlan(RetrievalPlanFixtures.singleQueryPlan("FIXTURE_CHANNEL_ALPHA"))
            .noEvidenceReply("fixture 无证据兜底")
            .build();
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId("sag-fixture-thread").build();
        return new TaskInfo(
            "fixture-conversation",
            1L,
            "fixture question",
            ChatQueryMode.DOCUMENT,
            "fixture-trace",
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 11, 10),
            "2026-11-10",
            plan,
            ChatDebugTrace.builder().build(),
            runnableConfig,
            null,
            Sinks.many().unicast().onBackpressureBuffer(),
            null,
            null,
            null,
            new ArrayList<>(),
            new ArrayList<>(),
            ConcurrentHashMap.newKeySet(),
            System.currentTimeMillis()
        );
    }
}
