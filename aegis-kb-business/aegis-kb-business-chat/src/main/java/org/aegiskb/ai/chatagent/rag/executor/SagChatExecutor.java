package org.aegiskb.ai.chatagent.rag.executor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.aegiskb.ai.chatagent.model.trace.ConversationTraceStageCode;
import org.aegiskb.ai.chatagent.rag.model.ConversationExecutionPlan;
import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.aegiskb.ai.chatagent.rag.model.RagPromptAssemblyResult;
import org.aegiskb.ai.chatagent.rag.model.RagRetrievalContext;
import org.aegiskb.ai.chatagent.rag.model.RetrievalPlan;
import org.aegiskb.ai.chatagent.rag.service.RagPromptAssemblyService;
import org.aegiskb.ai.chatagent.rag.service.RagRetrievalEngine;
import org.aegiskb.ai.chatagent.rag.support.EvidenceBudgetTraceSnapshot;
import org.aegiskb.ai.chatagent.rag.support.ExecutorEventSupport;
import org.aegiskb.ai.chatagent.service.ConversationTraceRecorder;
import org.aegiskb.ai.chatagent.service.ObservedChatModelService;
import org.aegiskb.ai.chatagent.service.TaskInfo;
import org.aegiskb.ai.chatagent.support.ChatContextKeys;
import org.aegiskb.ai.chatagent.support.StreamEventWriter;
import org.aegiskb.ai.chatagent.tool.KbRetrievalSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SAG 两阶段执行分支。
 *
 * <p>阶段1：SAG Agent 在预算受控的循环中自主调用 kb_search / tavily_search 收集证据；循环内模型中间文本
 * 只作为 thinking/trace 事件，绝不进入 answer token 流。阶段2：把会话累积的 per-call rerank 窗口交给
 * {@link RagRetrievalEngine#finalizeSagContext}（final selection 唯一发生点），随后复用与 RETRIEVAL 完全一致的
 * 权威管线：Prompt manifest 组装 → rag_answer 流式生成 → 下游显式 citation binding。</p>
 *
 * <p>循环异常终止或预算耗尽时，阶段2仍基于已收集证据或空证据兜底回复完成，不向调用方抛出未捕获异常。</p>
 */
@Component
public class SagChatExecutor implements ConversationExecutor {

    private final RagRetrievalEngine ragRetrievalEngine;
    private final RagPromptAssemblyService ragPromptAssemblyService;
    private final StreamEventWriter streamEventWriter;
    private final ObservedChatModelService observedChatModelService;
    private final ReactAgent sagChatReactAgent;

    public SagChatExecutor(RagRetrievalEngine ragRetrievalEngine,
                           RagPromptAssemblyService ragPromptAssemblyService,
                           StreamEventWriter streamEventWriter,
                           ObservedChatModelService observedChatModelService,
                           @Qualifier("sagChatReactAgent") ReactAgent sagChatReactAgent) {
        this.ragRetrievalEngine = ragRetrievalEngine;
        this.ragPromptAssemblyService = ragPromptAssemblyService;
        this.streamEventWriter = streamEventWriter;
        this.observedChatModelService = observedChatModelService;
        this.sagChatReactAgent = sagChatReactAgent;
    }

    @Override
    public ExecutionMode mode() {
        return ExecutionMode.SAG;
    }

    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        ConversationExecutionPlan plan = taskInfo.executionPlan();
        RetrievalPlan frozenPlan = plan == null ? null : plan.getRetrievalPlan();
        if (frozenPlan == null) {
            return Flux.error(new IllegalStateException("SAG 模式缺少冻结 RetrievalPlan"));
        }

        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "正在进入 SAG 搜索循环，由模型自主决定知识库与联网检索。");

        ConversationTraceRecorder.StageHandle searchStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(
                ConversationTraceStageCode.SAG_SEARCH,
                mode().name(),
                "正在执行 SAG 搜索循环。",
                retrievalPlanSnapshot(frozenPlan)
            );

        KbRetrievalSession session = new KbRetrievalSession(frozenPlan);
        taskInfo.runnableConfig().context().put(ChatContextKeys.KB_RETRIEVAL_SESSION, session);

        AtomicBoolean loopDegraded = new AtomicBoolean(false);
        Mono<RagRetrievalContext> finalizeMono;
        try {
            finalizeMono = sagChatReactAgent.stream(resolveAgentQuestion(plan, frozenPlan), taskInfo.runnableConfig())
                .publishOn(Schedulers.boundedElastic())
                // 循环内模型中间文本一律丢弃，不作为 answer token 流出。
                .ignoreElements()
                .onErrorResume(error -> {
                    loopDegraded.set(true);
                    degradeSearchLoop(taskInfo, searchStage, session, error);
                    return Mono.empty();
                })
                .then(Mono.defer(() -> finalizeSagContext(taskInfo, searchStage, session, loopDegraded)));
        }
        catch (GraphRunnerException exception) {
            loopDegraded.set(true);
            degradeSearchLoop(taskInfo, searchStage, session, exception);
            finalizeMono = Mono.defer(() -> finalizeSagContext(taskInfo, searchStage, session, loopDegraded));
        }
        return finalizeMono.flatMapMany(context -> streamFromFinalizedContext(taskInfo, plan, context));
    }

    private String resolveAgentQuestion(ConversationExecutionPlan plan, RetrievalPlan frozenPlan) {
        if (StrUtil.isNotBlank(plan.getAgentQuestion())) {
            return plan.getAgentQuestion();
        }
        if (StrUtil.isNotBlank(plan.getOriginalQuestion())) {
            return plan.getOriginalQuestion();
        }
        return frozenPlan.getQuestionPlan().getNormalizedQuery();
    }

    private void degradeSearchLoop(TaskInfo taskInfo,
                                   ConversationTraceRecorder.StageHandle searchStage,
                                   KbRetrievalSession session,
                                   Throwable error) {
        String message = error == null ? "" : StrUtil.blankToDefault(error.getMessage(), error.getClass().getName());
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter,
            "SAG 搜索循环异常终止，将基于已收集证据继续生成。");
        session.getNotes().add("SAG 搜索循环异常终止：" + message + "，已降级为使用已收集证据继续生成。");
        if (taskInfo.traceRecorder() != null) {
            taskInfo.traceRecorder().failStage(
                searchStage,
                "SAG 搜索循环异常终止，降级为使用已收集证据。",
                message,
                Map.of("kbSearchCalls", session.getStageResults().size())
            );
        }
    }

    private Mono<RagRetrievalContext> finalizeSagContext(TaskInfo taskInfo,
                                                         ConversationTraceRecorder.StageHandle searchStage,
                                                         KbRetrievalSession session,
                                                         AtomicBoolean loopDegraded) {
        return Mono.fromCallable(() -> {
            if (taskInfo.traceRecorder() != null) {
                if (!loopDegraded.get()) {
                    taskInfo.traceRecorder().completeStage(
                        searchStage,
                        "SAG 搜索循环完成。",
                        Map.of(
                            "kbSearchCalls", session.getStageResults().size(),
                            "usedChannels", List.copyOf(session.getUsedChannels()),
                            "notes", List.copyOf(session.getNotes())
                        )
                    );
                }
                ConversationTraceRecorder.StageHandle finalizeStage = taskInfo.traceRecorder().startStage(
                    ConversationTraceStageCode.SAG_FINALIZE,
                    mode().name(),
                    "正在合并 kb_search 各次调用的证据窗口。",
                    Map.of("kbSearchCalls", session.getStageResults().size())
                );
                RagRetrievalContext context = ragRetrievalEngine.finalizeSagContext(
                    session.getFrozenPlan(),
                    List.copyOf(session.getStageResults()),
                    session.getUsedChannels(),
                    session.getNotes(),
                    taskInfo.traceRecorder()
                );
                taskInfo.traceRecorder().completeStage(
                    finalizeStage,
                    "SAG 证据合并与最终选择完成。",
                    Map.of(
                        "kbSearchCalls", session.getStageResults().size(),
                        "referenceCount", context.flattenReferences().size(),
                        "retrievalNotes", context.getRetrievalNotes() == null ? List.of() : context.getRetrievalNotes()
                    )
                );
                return context;
            }
            return ragRetrievalEngine.finalizeSagContext(
                session.getFrozenPlan(),
                List.copyOf(session.getStageResults()),
                session.getUsedChannels(),
                session.getNotes(),
                null
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<String> streamFromFinalizedContext(TaskInfo taskInfo,
                                                    ConversationExecutionPlan plan,
                                                    RagRetrievalContext context) {
        context.getRetrievalNotes().forEach(note -> ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, note));

        taskInfo.usedTools().addAll(context.getUsedChannels());
        taskInfo.debugTrace().setRetrievalNotes(new ArrayList<>(context.getRetrievalNotes()));
        taskInfo.debugTrace().setUsedChannels(new ArrayList<>(context.getUsedChannels()));

        ConversationTraceRecorder.StageHandle budgetStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(ConversationTraceStageCode.EVIDENCE_BUDGET, mode().name(), "正在组装证据与 Prompt 预算。", null);

        if (context.isEmpty()) {
            if (taskInfo.traceRecorder() != null) {
                taskInfo.traceRecorder().completeStage(
                    budgetStage,
                    "无 Source 证据，已完成空证据预算账本。",
                    EvidenceBudgetTraceSnapshot.build(context, null)
                );
            }

            ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "当前没有足够证据，直接返回无证据兜底回复。");
            return Flux.just(StrUtil.blankToDefault(plan.getNoEvidenceReply(), "当前没有足够证据支持明确回答。"));
        }

        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "证据整理完成，正在基于证据生成回答。");

        RagPromptAssemblyResult promptAssemblyResult = ragPromptAssemblyService.assemble(plan, context);
        taskInfo.setPromptAssemblyResult(promptAssemblyResult);
        String systemPrompt = promptAssemblyResult.getSystemPrompt();
        String userPrompt = promptAssemblyResult.getUserPrompt();
        taskInfo.debugTrace().setRagSystemPrompt(systemPrompt);
        taskInfo.debugTrace().setRagUserPrompt(userPrompt);
        if (taskInfo.traceRecorder() != null) {
            taskInfo.traceRecorder().completeStage(
                budgetStage,
                "证据预算与 Prompt 组装完成。",
                EvidenceBudgetTraceSnapshot.build(context, promptAssemblyResult)
            );
        }

        ConversationTraceRecorder.StageHandle answerStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(ConversationTraceStageCode.ANSWER_GENERATE, mode().name(), "正在基于证据生成回答。", null);
        return observedChatModelService.streamText("rag_answer", systemPrompt, userPrompt, taskInfo.traceRecorder())
            .doOnComplete(() -> {
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().completeStage(answerStage, "答案生成完成。", Map.of(
                        "firstResponseTimeMs", taskInfo.firstResponseTimeMs().get(),
                        "answerLength", taskInfo.answerBuffer().length()
                    ));
                }
            })
            .doOnError(error -> {
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().failStage(answerStage, "答案生成失败。", error.getMessage(), null);
                }
            });
    }

    private Map<String, Object> retrievalPlanSnapshot(RetrievalPlan frozenPlan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("retrievalPlan", frozenPlan);
        return snapshot;
    }
}
