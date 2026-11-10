package org.aegiskb.ai.chatagent.tool;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import lombok.extern.slf4j.Slf4j;
import org.aegiskb.ai.chatagent.model.debug.ChatDebugTrace;
import org.aegiskb.ai.chatagent.model.debug.ChatToolTrace;
import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionQuery;
import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionRequest;
import org.aegiskb.ai.chatagent.rag.model.RetrievalPlan;
import org.aegiskb.ai.chatagent.rag.model.RetrievalQuestionPlan;
import org.aegiskb.ai.chatagent.rag.service.RagRetrievalEngine;
import org.aegiskb.ai.chatagent.support.ChatContextKeys;
import org.aegiskb.ai.chatagent.support.SinkEmitHelper;
import org.aegiskb.ai.chatagent.support.StreamEventMetadata;
import org.aegiskb.ai.chatagent.support.StreamEventWriter;
import org.aegiskb.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * kb_search：SAG 搜索循环的内部知识库检索工具。
 *
 * <p>Agent 每次调用只提供查询文本；scope、channel、预算与排序特征一律沿用 ToolContext 中冻结的
 * {@link KbRetrievalSession} 的 RetrievalPlan，通过既有 {@link RetrievalExecutionRequest#compile} 契约投影为唯一
 * per-call execution request，执行 channel→fusion→rerank window 阶段（不做 final selection），并把阶段结果按调用
 * 顺序累积到会话，供 SAG 阶段2合并窗口后一次性做 final evidence selection。</p>
 */
@Slf4j
@Component
public class KbRetrievalTool {

    private static final int TEXT_PREVIEW_CHARS = 240;

    private final RagRetrievalEngine ragRetrievalEngine;
    private final StreamEventWriter streamEventWriter;

    public KbRetrievalTool(RagRetrievalEngine ragRetrievalEngine, StreamEventWriter streamEventWriter) {
        this.ragRetrievalEngine = ragRetrievalEngine;
        this.streamEventWriter = streamEventWriter;
    }

    public KbRetrievalToolResult search(KbRetrievalRequest request, ToolContext toolContext) {
        String rawQuery = request != null && StrUtil.isNotBlank(request.getQuery()) ? request.getQuery().trim() : "";
        if (StrUtil.isBlank(rawQuery)) {
            throw new IllegalArgumentException("query 不能为空");
        }
        KbRetrievalSession session = resolveSession(toolContext);
        int callIndex = session.nextCallIndex();

        ChatToolTrace toolTrace = registerToolTrace(toolContext, ChatToolTrace.builder()
            .toolName("kb_search")
            .status("RUNNING")
            .inputSummary(rawQuery)
            .build());
        markToolUsed(toolContext, "kb_search");
        publishThinking(toolContext, "📚 正在检索知识库: " + rawQuery);
        long startTime = System.currentTimeMillis();

        try {
            RetrievalPlan perCallPlan = derivePerCallPlan(session.getFrozenPlan(), rawQuery, callIndex);
            RetrievalExecutionRequest executionRequest = RetrievalExecutionRequest.compile(perCallPlan)
                .stream()
                .filter(candidate -> candidate.subQuestionIndex() == callIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("kb_search per-call execution request 缺失: callIndex=" + callIndex));

            RagRetrievalEngine.RetrievalStageResult stageResult = ragRetrievalEngine
                .executeRetrievalStagesAsync(executionRequest, perCallPlan, session.getUsedChannels(), session.getNotes())
                .get(perCallPlan.getSubQuestionTimeoutMs(), TimeUnit.MILLISECONDS);
            session.record(stageResult);

            KbRetrievalToolResult result = buildResult(rawQuery, callIndex, stageResult);
            completeToolTrace(toolTrace, result.candidateCount(), startTime);
            publishThinking(toolContext, "📚 知识库检索完成，候选 " + result.candidateCount() + " 条");
            return result;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failToolTrace(toolTrace, exception, startTime);
            throw new IllegalStateException("kb_search 执行被中断: callIndex=" + callIndex, exception);
        }
        catch (RuntimeException exception) {
            failToolTrace(toolTrace, exception, startTime);
            publishThinking(toolContext, "⚠️ 知识库检索失败: " + exception.getMessage());
            log.warn("kb_search 检索失败, callIndex={}, query={}", callIndex, rawQuery, exception);
            throw exception;
        }
        catch (Exception exception) {
            failToolTrace(toolTrace, exception, startTime);
            publishThinking(toolContext, "⚠️ 知识库检索失败: " + exception.getMessage());
            log.warn("kb_search 检索失败, callIndex={}, query={}", callIndex, rawQuery, exception);
            throw new IllegalStateException("kb_search 检索失败: callIndex=" + callIndex, exception);
        }
    }

    private KbRetrievalSession resolveSession(ToolContext toolContext) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        Object candidate = config == null ? null : config.context().get(ChatContextKeys.KB_RETRIEVAL_SESSION);
        if (candidate instanceof KbRetrievalSession session) {
            return session;
        }
        throw new IllegalStateException("kb_search 缺少冻结检索会话，无法执行");
    }

    /**
     * 由冻结 Plan 派生 per-call Plan：只替换 questionPlan 为单一 Agent 查询，其余 scope/channel/预算字段逐字段沿用。
     */
    private RetrievalPlan derivePerCallPlan(RetrievalPlan frozenPlan, String query, int callIndex) {
        RetrievalQuestionPlan questionPlan = RetrievalQuestionPlan.builder()
            .currentQuestion(query)
            .rewrittenQuestion(query)
            .normalizedQuery(query)
            .executionQueries(new ArrayList<>(List.of(RetrievalExecutionQuery.builder()
                .index(callIndex)
                .sourceQuestion(query)
                .normalizedQuery(query)
                .executionQuery(query)
                .contextHints(new ArrayList<>())
                .build())))
            .followUp(false)
            .historyInherited(false)
            .historyInheritanceSource("NONE")
            .inheritedContextAnchors(new ArrayList<>())
            .build();
        return frozenPlan.toBuilder()
            .questionPlan(questionPlan)
            .build();
    }

    private KbRetrievalToolResult buildResult(String query,
                                              int callIndex,
                                              RagRetrievalEngine.RetrievalStageResult stageResult) {
        List<Document> candidates = stageResult.rerankResult().candidates();
        List<KbRetrievalToolResult.Candidate> views = new ArrayList<>();
        for (Document candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            views.add(new KbRetrievalToolResult.Candidate(
                resolveDocumentName(candidate),
                resolveScore(candidate),
                clipText(candidate.getText(), TEXT_PREVIEW_CHARS)
            ));
        }
        return new KbRetrievalToolResult(query, callIndex, views.size(), List.copyOf(views));
    }

    private String resolveDocumentName(Document document) {
        Object value = document.getMetadata().get(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME);
        return value == null ? "" : String.valueOf(value);
    }

    private double resolveScore(Document document) {
        Object metadataScore = document.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
        if (metadataScore instanceof Number number) {
            return number.doubleValue();
        }
        Double score = document.getScore();
        return score == null ? 0D : score;
    }

    private String clipText(String value, int maxLength) {
        if (StrUtil.isBlank(value) || maxLength <= 0) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    @SuppressWarnings("unchecked")
    private void markToolUsed(ToolContext toolContext, String toolName) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return;
        }
        Object container = config.context().get(ChatContextKeys.USED_TOOLS);
        if (container instanceof Set<?> set) {
            ((Set<String>) set).add(toolName);
        }
    }

    @SuppressWarnings("unchecked")
    private void publishThinking(ToolContext toolContext, String content) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return;
        }
        Object sinkCandidate = config.context().get(ChatContextKeys.EVENT_SINK);
        StreamEventMetadata metadata = resolveMetadata(config);
        if (sinkCandidate instanceof Sinks.Many<?> sink) {
            SinkEmitHelper.emitNext((Sinks.Many<String>) sink, streamEventWriter.thinking(content, metadata));
        }
        Object stepsCandidate = config.context().get(ChatContextKeys.THINKING_STEPS);
        if (stepsCandidate instanceof List<?> list) {
            ((List<String>) list).add(content);
        }
    }

    private StreamEventMetadata resolveMetadata(RunnableConfig config) {
        if (config == null) {
            return null;
        }
        Object metadataCandidate = config.context().get(ChatContextKeys.EVENT_METADATA);
        if (metadataCandidate instanceof StreamEventMetadata metadata) {
            return metadata;
        }
        return null;
    }

    private ChatToolTrace registerToolTrace(ToolContext toolContext, ChatToolTrace trace) {
        if (trace == null) {
            return null;
        }
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return trace;
        }
        Object candidate = config.context().get(ChatContextKeys.DEBUG_TRACE);
        if (candidate instanceof ChatDebugTrace debugTrace) {
            debugTrace.getToolTraces().add(trace);
        }
        return trace;
    }

    private void completeToolTrace(ChatToolTrace toolTrace, int candidateCount, long startTime) {
        if (toolTrace == null) {
            return;
        }
        toolTrace.setStatus("COMPLETED");
        toolTrace.setReferenceCount(candidateCount);
        toolTrace.setDurationMs(Math.max(0L, System.currentTimeMillis() - startTime));
        toolTrace.setOutputSummary("知识库候选 " + candidateCount + " 条");
    }

    private void failToolTrace(ChatToolTrace toolTrace, Exception exception, long startTime) {
        if (toolTrace == null) {
            return;
        }
        toolTrace.setStatus("FAILED");
        toolTrace.setDurationMs(Math.max(0L, System.currentTimeMillis() - startTime));
        toolTrace.setErrorMessage(exception == null ? "" : StrUtil.blankToDefault(exception.getMessage(), ""));
    }
}
