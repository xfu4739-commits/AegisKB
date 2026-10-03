package org.aegiskb.ai.manage.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.aegiskb.ai.chatagent.rag.model.RagRuntimeOptions;
import org.aegiskb.ai.manage.model.SystemConfigSnapshot;
import org.aegiskb.enums.BaseCode;
import org.aegiskb.exception.SuperAgentFrameException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Component
public class SystemConfigRegistry {

    public static final String NEW_CONVERSATION = "NEW_CONVERSATION";
    public static final String NEW_BUILD_TASK = "NEW_BUILD_TASK";
    public static final String RESTART_REQUIRED = "RESTART_REQUIRED";

    private final List<ConfigDefinition> definitions;
    private final Map<String, ConfigDefinition> definitionsByKey;

    public SystemConfigRegistry() {
        definitions = List.of(
            integer("ragRuntime.vectorTopK", "retrievalWindow", "向量召回数量", "每个子问题从向量通道最多召回的候选数量。", 1, 200, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getVectorTopK(), (o, v) -> o.getRagRuntime().setVectorTopK((Integer) v)),
            integer("ragRuntime.keywordTopK", "retrievalWindow", "关键词召回数量", "每个子问题从关键词通道最多召回的候选数量。", 1, 200, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getKeywordTopK(), (o, v) -> o.getRagRuntime().setKeywordTopK((Integer) v)),
            integer("ragRuntime.graphRagTopK", "retrievalWindow", "GraphRAG 召回数量", "每个子问题最多保留的 GraphRAG 候选数量。", 1, 100, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getGraphRagTopK(), (o, v) -> o.getRagRuntime().setGraphRagTopK((Integer) v)),
            integer("ragRuntime.graphRagMaxHops", "retrievalWindow", "GraphRAG 最大跳数", "关系检索允许扩展的最大图跳数。", 1, 5, "跳", NEW_CONVERSATION, o -> o.getRagRuntime().getGraphRagMaxHops(), (o, v) -> o.getRagRuntime().setGraphRagMaxHops((Integer) v)),
            integer("ragRuntime.raptorTopK", "retrievalWindow", "RAPTOR 摘要召回数量", "每个子问题最多保留的 RAPTOR 摘要数量。", 1, 100, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getRaptorTopK(), (o, v) -> o.getRagRuntime().setRaptorTopK((Integer) v)),
            integer("ragRuntime.raptorSourceChunkTopK", "retrievalWindow", "RAPTOR 来源块数量", "每条 RAPTOR 摘要最多展开的真实来源块数量。", 1, 50, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getRaptorSourceChunkTopK(), (o, v) -> o.getRagRuntime().setRaptorSourceChunkTopK((Integer) v)),
            integer("ragRuntime.candidateTopK", "retrievalWindow", "融合候选窗口", "各检索通道合并后进入候选窗口的最大数量。", 1, 500, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getCandidateTopK(), (o, v) -> o.getRagRuntime().setCandidateTopK((Integer) v)),
            integer("ragRuntime.rerankCandidateTopK", "retrievalWindow", "重排候选窗口", "最多送入重排阶段的候选数量。", 1, 500, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getRerankCandidateTopK(), (o, v) -> o.getRagRuntime().setRerankCandidateTopK((Integer) v)),
            integer("ragRuntime.finalTopK", "retrievalWindow", "最终证据窗口", "最终证据策略最多选择的来源数量。", 1, 50, "条", NEW_CONVERSATION, o -> o.getRagRuntime().getFinalTopK(), (o, v) -> o.getRagRuntime().setFinalTopK((Integer) v)),

            bool("ragRuntime.rerankEnabled", "execution", "启用重排", "决定新会话是否执行统一候选重排。", NEW_CONVERSATION, o -> o.getRagRuntime().isRerankEnabled(), (o, v) -> o.getRagRuntime().setRerankEnabled((Boolean) v)),
            duration("ragRuntime.channelTimeoutMs", "execution", "单通道超时", "单个检索通道超过该时长后按超时结果收口。", 100, 120000, NEW_CONVERSATION, o -> o.getRagRuntime().getChannelTimeoutMs(), (o, v) -> o.getRagRuntime().setChannelTimeoutMs((Long) v)),
            duration("ragRuntime.subQuestionTimeoutMs", "execution", "子问题总超时", "一个子问题的并行检索超过该时长后停止等待。", 100, 180000, NEW_CONVERSATION, o -> o.getRagRuntime().getSubQuestionTimeoutMs(), (o, v) -> o.getRagRuntime().setSubQuestionTimeoutMs((Long) v)),

            percentage("ragRuntime.minVectorSimilarity", "relevance", "向量最低相似度", "低于该比例的向量候选不会进入后续融合。", NEW_CONVERSATION, o -> o.getRagRuntime().getMinVectorSimilarity(), (o, v) -> o.getRagRuntime().setMinVectorSimilarity((Double) v)),
            percentage("ragRuntime.keywordRelativeScoreFloor", "relevance", "关键词相对分数下限", "关键词候选相对本通道最高分的最低保留比例。", NEW_CONVERSATION, o -> o.getRagRuntime().getKeywordRelativeScoreFloor(), (o, v) -> o.getRagRuntime().setKeywordRelativeScoreFloor((Double) v)),

            bool("ragRuntime.keywordChannelEnabled", "channels", "启用关键词通道", "决定新会话是否并行执行关键词检索。", NEW_CONVERSATION, o -> o.getRagRuntime().isKeywordChannelEnabled(), (o, v) -> o.getRagRuntime().setKeywordChannelEnabled((Boolean) v)),
            bool("ragRuntime.tableChannelEnabled", "channels", "启用表格通道", "决定新会话是否检索结构化表格证据。", NEW_CONVERSATION, o -> o.getRagRuntime().isTableChannelEnabled(), (o, v) -> o.getRagRuntime().setTableChannelEnabled((Boolean) v)),
            bool("ragRuntime.graphRagChannelEnabled", "channels", "启用 GraphRAG 通道", "决定新会话是否检索知识图谱证据。", NEW_CONVERSATION, o -> o.getRagRuntime().isGraphRagChannelEnabled(), (o, v) -> o.getRagRuntime().setGraphRagChannelEnabled((Boolean) v)),
            bool("ragRuntime.raptorChannelEnabled", "channels", "启用 RAPTOR 通道", "决定新会话是否检索层级摘要证据。", NEW_CONVERSATION, o -> o.getRagRuntime().isRaptorChannelEnabled(), (o, v) -> o.getRagRuntime().setRaptorChannelEnabled((Boolean) v)),

            decimal("ragRuntime.hybrid.vectorWeight", "fusion", "向量通道权重", "融合排序时向量通道分数的权重。", 0, 5, 0.05, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getVectorWeight(), (o, v) -> o.getRagRuntime().getHybrid().setVectorWeight((Double) v)),
            decimal("ragRuntime.hybrid.keywordWeight", "fusion", "关键词通道权重", "融合排序时关键词通道分数的权重。", 0, 5, 0.05, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getKeywordWeight(), (o, v) -> o.getRagRuntime().getHybrid().setKeywordWeight((Double) v)),
            decimal("ragRuntime.hybrid.tableWeight", "fusion", "表格通道权重", "融合排序时表格通道分数的权重。", 0, 5, 0.05, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getTableWeight(), (o, v) -> o.getRagRuntime().getHybrid().setTableWeight((Double) v)),
            decimal("ragRuntime.hybrid.graphRagWeight", "fusion", "GraphRAG 通道权重", "融合排序时 GraphRAG 通道分数的权重。", 0, 5, 0.05, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getGraphRagWeight(), (o, v) -> o.getRagRuntime().getHybrid().setGraphRagWeight((Double) v)),
            decimal("ragRuntime.hybrid.raptorWeight", "fusion", "RAPTOR 通道权重", "融合排序时 RAPTOR 通道分数的权重。", 0, 5, 0.05, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getRaptorWeight(), (o, v) -> o.getRagRuntime().getHybrid().setRaptorWeight((Double) v)),
            decimal("ragRuntime.hybrid.rankWeight", "fusion", "排名特征权重", "融合排序时通道内排名特征的权重。", 0, 5, 0.05, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getRankWeight(), (o, v) -> o.getRagRuntime().getHybrid().setRankWeight((Double) v)),
            decimal("ragRuntime.hybrid.originalScoreWeight", "fusion", "原始分数权重", "融合排序时保留原始通道分数的附加权重。", 0, 1, 0.01, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getOriginalScoreWeight(), (o, v) -> o.getRagRuntime().getHybrid().setOriginalScoreWeight((Double) v)),
            decimal("ragRuntime.hybrid.metadataBoostWeight", "fusion", "元数据加分权重", "标题、章节和关键词等元数据特征的加分权重。", 0, 1, 0.01, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getMetadataBoostWeight(), (o, v) -> o.getRagRuntime().getHybrid().setMetadataBoostWeight((Double) v)),
            decimal("ragRuntime.hybrid.maxMetadataBoost", "fusion", "元数据最大加分", "单条候选允许获得的元数据加分上限。", 0, 5, 0.05, "倍", NEW_CONVERSATION, o -> o.getRagRuntime().getHybrid().getMaxMetadataBoost(), (o, v) -> o.getRagRuntime().getHybrid().setMaxMetadataBoost((Double) v)),

            bool("chat.recommendationEnabled", "chatAgent", "启用推荐追问", "主回答完成后生成最多 3 个可继续追问的问题。", NEW_CONVERSATION, o -> o.getChat().isRecommendationEnabled(), (o, v) -> o.getChat().setRecommendationEnabled((Boolean) v)),
            integer("chat.maxModelCallsPerRun", "chatAgent", "单次模型调用上限", "单次对话运行允许的最大模型调用次数。", 1, 100, "次", RESTART_REQUIRED, o -> o.getChat().getMaxModelCallsPerRun(), (o, v) -> o.getChat().setMaxModelCallsPerRun((Integer) v)),
            integer("chat.maxModelCallsPerThread", "chatAgent", "会话模型调用上限", "同一会话线程累计允许的最大模型调用次数。", 1, 10000, "次", RESTART_REQUIRED, o -> o.getChat().getMaxModelCallsPerThread(), (o, v) -> o.getChat().setMaxModelCallsPerThread((Integer) v)),
            integer("chat.maxToolCallsPerRun", "chatAgent", "单次工具调用上限", "单次对话运行允许的最大外部工具调用次数。", 1, 100, "次", RESTART_REQUIRED, o -> o.getChat().getMaxToolCallsPerRun(), (o, v) -> o.getChat().setMaxToolCallsPerRun((Integer) v)),
            integer("chat.maxToolCallsPerThread", "chatAgent", "会话工具调用上限", "同一会话线程累计允许的最大外部工具调用次数。", 1, 10000, "次", RESTART_REQUIRED, o -> o.getChat().getMaxToolCallsPerThread(), (o, v) -> o.getChat().setMaxToolCallsPerThread((Integer) v)),
            integer("chat.historyPreviewTurns", "chatAgent", "推荐问题历史轮数", "生成推荐问题时最多回看的最近历史问答轮数。", 0, 50, "轮", NEW_CONVERSATION, o -> o.getChat().getHistoryPreviewTurns(), (o, v) -> o.getChat().setHistoryPreviewTurns((Integer) v)),
            duration("chat.recommendationTimeoutMs", "chatAgent", "推荐问题超时", "推荐问题生成超过该时长后按无推荐结果收口。", 100, 120000, NEW_CONVERSATION, o -> o.getChat().getRecommendationTimeoutMs(), (o, v) -> o.getChat().setRecommendationTimeoutMs((Long) v)),

            bool("rag.enabled", "ragOrchestration", "启用 RAG 前置编排", "进入最终执行器前执行历史加载、问题改写和文档检索规划。", NEW_CONVERSATION, o -> o.getRag().isEnabled(), (o, v) -> o.getRag().setEnabled((Boolean) v)),
            bool("rag.rewriteEnabled", "ragOrchestration", "启用问题改写", "对多轮问题做独立化改写并按需拆分子问题。", NEW_CONVERSATION, o -> o.getRag().isRewriteEnabled(), (o, v) -> o.getRag().setRewriteEnabled((Boolean) v)),
            integer("rag.rewriteHistoryTurns", "ragOrchestration", "改写历史轮数", "问题改写阶段最多回看的历史问答轮数。", 0, 50, "轮", NEW_CONVERSATION, o -> o.getRag().getRewriteHistoryTurns(), (o, v) -> o.getRag().setRewriteHistoryTurns((Integer) v)),
            bool("rag.rewriteOptions.enabled", "ragOrchestration", "启用改写模型参数覆盖", "为问题改写阶段单独覆盖模型采样参数。", NEW_CONVERSATION, o -> o.getRag().getRewriteOptions().isEnabled(), (o, v) -> o.getRag().getRewriteOptions().setEnabled((Boolean) v)),
            decimal("rag.rewriteOptions.temperature", "ragOrchestration", "改写温度", "改写阶段的采样温度，越低越保守。", 0, 2, 0.05, "", NEW_CONVERSATION, o -> o.getRag().getRewriteOptions().getTemperature(), (o, v) -> o.getRag().getRewriteOptions().setTemperature((Double) v)),
            percentage("rag.rewriteOptions.topP", "ragOrchestration", "改写 Top P", "改写阶段的核采样比例，越低越不易发散。", NEW_CONVERSATION, o -> o.getRag().getRewriteOptions().getTopP(), (o, v) -> o.getRag().getRewriteOptions().setTopP((Double) v)),
            bool("rag.rewriteOptions.thinking", "ragOrchestration", "启用改写思考模式", "通过 OpenAI 兼容协议为改写调用透传 thinking。", NEW_CONVERSATION, o -> o.getRag().getRewriteOptions().getThinking(), (o, v) -> o.getRag().getRewriteOptions().setThinking((Boolean) v)),
            integer("rag.maxSubQuestions", "ragOrchestration", "子问题拆分上限", "单轮最多拆分的子问题数量。", 1, 20, "个", NEW_CONVERSATION, o -> o.getRag().getMaxSubQuestions(), (o, v) -> o.getRag().setMaxSubQuestions((Integer) v)),
            bool("rag.graphRagQueryPlanEnabled", "ragOrchestration", "启用 GraphRAG 查询计划", "允许 LLM 提供受控的实体、关系、社区和跳数建议。", NEW_CONVERSATION, o -> o.getRag().getGraphRagQueryPlan().isEnabled(), (o, v) -> o.getRag().getGraphRagQueryPlan().setEnabled((Boolean) v)),
            percentage("rag.autoRouteRecommendationThreshold", "ragOrchestration", "文档推荐阈值", "只控制可降级的 top 文档推荐提示，不改变知识范围。", NEW_CONVERSATION, o -> o.getRag().getAutoRoute().getRecommendationThreshold(), (o, v) -> o.getRag().getAutoRoute().setRecommendationThreshold((Double) v)),

            integer("rag.parentEvidenceMaxChars", "ragContext", "单个父块证据预算", "回答阶段单个父块正文与命中子片段摘要的字符预算。", 100, 50000, "字符", NEW_CONVERSATION, o -> o.getRag().getParentEvidenceMaxChars(), (o, v) -> o.getRag().setParentEvidenceMaxChars((Integer) v)),
            integer("rag.planningHistoryMaxChars", "ragContext", "规划历史预算", "改写和检索规划阶段可使用的历史上下文字符数。", 100, 50000, "字符", NEW_CONVERSATION, o -> o.getRag().getPlanningHistoryMaxChars(), (o, v) -> o.getRag().setPlanningHistoryMaxChars((Integer) v)),
            integer("rag.answerHistoryMaxChars", "ragContext", "回答历史预算", "回答阶段可使用的最近问题和结构化历史字符数。", 100, 50000, "字符", NEW_CONVERSATION, o -> o.getRag().getAnswerHistoryMaxChars(), (o, v) -> o.getRag().setAnswerHistoryMaxChars((Integer) v)),
            integer("rag.totalEvidenceMaxChars", "ragContext", "总证据预算", "回答阶段全部证据允许占用的总字符数。", 100, 200000, "字符", NEW_CONVERSATION, o -> o.getRag().getTotalEvidenceMaxChars(), (o, v) -> o.getRag().setTotalEvidenceMaxChars((Integer) v)),
            integer("rag.perSubQuestionEvidenceMaxChars", "ragContext", "单子问题证据预算", "每个子问题允许占用的证据字符数。", 100, 100000, "字符", NEW_CONVERSATION, o -> o.getRag().getPerSubQuestionEvidenceMaxChars(), (o, v) -> o.getRag().setPerSubQuestionEvidenceMaxChars((Integer) v)),
            text("rag.noEvidenceReply", "ragContext", "无证据兜底文案", "知识问题没有合格证据时直接返回的文案。", 2000, NEW_CONVERSATION, o -> o.getRag().getNoEvidenceReply(), (o, v) -> o.getRag().setNoEvidenceReply((String) v)),
            bool("rag.historySummary.enabled", "ragContext", "启用长期会话摘要", "使用长期摘要与最近原文窗口压缩长会话。", NEW_CONVERSATION, o -> o.getRag().getHistorySummary().isEnabled(), (o, v) -> o.getRag().getHistorySummary().setEnabled((Boolean) v)),
            integer("rag.historySummary.keepRecentTurns", "ragContext", "保留最近原文轮数", "始终直接保留、不进入长期摘要的最近问答轮数。", 0, 50, "轮", NEW_CONVERSATION, o -> o.getRag().getHistorySummary().getKeepRecentTurns(), (o, v) -> o.getRag().getHistorySummary().setKeepRecentTurns((Integer) v)),
            integer("rag.historySummary.compressionBatchTurns", "ragContext", "增量压缩批次", "单次增量压缩最多推进的历史问答轮数。", 1, 50, "轮", NEW_CONVERSATION, o -> o.getRag().getHistorySummary().getCompressionBatchTurns(), (o, v) -> o.getRag().getHistorySummary().setCompressionBatchTurns((Integer) v)),
            integer("rag.historySummary.recentTranscriptMaxChars", "ragContext", "最近原文窗口预算", "最近原文问答窗口允许占用的最大字符数。", 100, 50000, "字符", NEW_CONVERSATION, o -> o.getRag().getHistorySummary().getRecentTranscriptMaxChars(), (o, v) -> o.getRag().getHistorySummary().setRecentTranscriptMaxChars((Integer) v)),
            integer("rag.historySummary.summaryMaxChars", "ragContext", "长期摘要预算", "长期会话摘要允许占用的最大字符数。", 100, 50000, "字符", NEW_CONVERSATION, o -> o.getRag().getHistorySummary().getSummaryMaxChars(), (o, v) -> o.getRag().getHistorySummary().setSummaryMaxChars((Integer) v)),

            integer("rag.raptorMaxClusterSize", "raptorBuild", "RAPTOR 簇节点上限", "每个摘要簇最多包含的下层节点数量。", 2, 50, "个", NEW_BUILD_TASK, o -> o.getRag().getRaptorMaxClusterSize(), (o, v) -> o.getRag().setRaptorMaxClusterSize((Integer) v)),
            integer("rag.raptorMaxLevels", "raptorBuild", "RAPTOR 摘要层数", "摘要树最多生成的层数。", 1, 8, "层", NEW_BUILD_TASK, o -> o.getRag().getRaptorMaxLevels(), (o, v) -> o.getRag().setRaptorMaxLevels((Integer) v)),
            bool("rag.raptorLlmSummaryEnabled", "raptorBuild", "启用 RAPTOR LLM 摘要", "允许 Python rag-tools 在构建阶段尝试模型摘要。", NEW_BUILD_TASK, o -> o.getRag().isRaptorLlmSummaryEnabled(), (o, v) -> o.getRag().setRaptorLlmSummaryEnabled((Boolean) v)),
            percentage("rag.raptorSummaryQualityFloor", "raptorBuild", "RAPTOR 质量下限", "低于该质量比例的摘要节点不会入库或建立索引。", NEW_BUILD_TASK, o -> o.getRag().getRaptorSummaryQualityFloor(), (o, v) -> o.getRag().setRaptorSummaryQualityFloor((Double) v)),

            bool("graphRag.build.leaseEnabled", "graphRagBuild", "启用 GraphRAG 构建租约", "关闭后 GraphRAG 构建不能发布结果，仅用于故障隔离。", NEW_BUILD_TASK, o -> o.getGraphRagBuild().isLeaseEnabled(), (o, v) -> o.getGraphRagBuild().setLeaseEnabled((Boolean) v)),
            integer("graphRag.build.leaseTtlSeconds", "graphRagBuild", "构建租约时长", "GraphRAG 构建任务持有租约的最长时间。", 30, 86400, "秒", NEW_BUILD_TASK, o -> o.getGraphRagBuild().getLeaseTtlSeconds(), (o, v) -> o.getGraphRagBuild().setLeaseTtlSeconds((Integer) v)),
            integer("graphRag.build.maxAttempts", "graphRagBuild", "构建尝试次数", "GraphRAG 构建发生可重试失败时允许的最大尝试次数。", 1, 10, "次", NEW_BUILD_TASK, o -> o.getGraphRagBuild().getMaxAttempts(), (o, v) -> o.getGraphRagBuild().setMaxAttempts((Integer) v)),
            duration("graphRag.build.retryBackoffMillis", "graphRagBuild", "构建重试退避", "GraphRAG 构建重试前的等待时间。", 0, 60000, NEW_BUILD_TASK, o -> o.getGraphRagBuild().getRetryBackoffMillis(), (o, v) -> o.getGraphRagBuild().setRetryBackoffMillis((Long) v)),

            bool("graphRag.extraction.enabled", "graphRagEnhancement", "启用 GraphRAG 受控抽取", "使用 LLM 生成受 Java 白名单与原文校验约束的图谱候选。", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().isEnabled(), (o, v) -> o.getGraphRagExtraction().setEnabled((Boolean) v)),
            integer("graphRag.extraction.batchChunkLimit", "graphRagEnhancement", "抽取批次块数", "单次 GraphRAG 抽取调用最多包含的 chunk 数。", 1, 20, "块", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getBatchChunkLimit(), (o, v) -> o.getGraphRagExtraction().setBatchChunkLimit((Integer) v)),
            integer("graphRag.extraction.batchTokenLimit", "graphRagEnhancement", "抽取批次 Token 预算", "单次 GraphRAG 抽取调用的估算 token 上限。", 256, 32000, "token", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getBatchTokenLimit(), (o, v) -> o.getGraphRagExtraction().setBatchTokenLimit((Integer) v)),
            integer("graphRag.extraction.maxEntitiesPerCall", "graphRagEnhancement", "单次实体上限", "单次受控抽取最多接受的实体候选数。", 1, 100, "个", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getMaxEntitiesPerCall(), (o, v) -> o.getGraphRagExtraction().setMaxEntitiesPerCall((Integer) v)),
            integer("graphRag.extraction.maxRelationsPerCall", "graphRagEnhancement", "单次关系上限", "单次受控抽取最多接受的关系候选数。", 1, 100, "个", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getMaxRelationsPerCall(), (o, v) -> o.getGraphRagExtraction().setMaxRelationsPerCall((Integer) v)),
            integer("graphRag.extraction.maxEvidencesPerCall", "graphRagEnhancement", "单次证据上限", "单次受控抽取最多接受的证据候选数。", 1, 100, "个", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getMaxEvidencesPerCall(), (o, v) -> o.getGraphRagExtraction().setMaxEvidencesPerCall((Integer) v)),
            integer("graphRag.extraction.maxQuoteChars", "graphRagEnhancement", "抽取引用长度", "单条受控抽取原文引用允许的最大字符数。", 20, 2000, "字符", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getMaxQuoteChars(), (o, v) -> o.getGraphRagExtraction().setMaxQuoteChars((Integer) v)),
            integer("graphRag.extraction.maxReasonChars", "graphRagEnhancement", "抽取理由长度", "单条受控抽取理由允许的最大字符数。", 20, 2000, "字符", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getMaxReasonChars(), (o, v) -> o.getGraphRagExtraction().setMaxReasonChars((Integer) v)),
            integer("graphRag.extraction.maxUnitTextChars", "graphRagEnhancement", "抽取单元文本长度", "结构化行拆成 advisor 单元时允许的最大字符数。", 50, 8000, "字符", NEW_BUILD_TASK, o -> o.getGraphRagExtraction().getMaxUnitTextChars(), (o, v) -> o.getGraphRagExtraction().setMaxUnitTextChars((Integer) v)),
            bool("graphRag.communityReport.enabled", "graphRagEnhancement", "启用社区报告增强", "允许 LLM 基于白名单实体、关系和证据生成社区报告建议。", NEW_BUILD_TASK, o -> o.getGraphRagCommunityReport().isEnabled(), (o, v) -> o.getGraphRagCommunityReport().setEnabled((Boolean) v)),
            bool("graphRag.entityResolution.enabled", "graphRagEnhancement", "启用实体消歧增强", "允许 LLM 基于受控候选提供实体合并建议。", NEW_BUILD_TASK, o -> o.getGraphRagEntityResolution().isEnabled(), (o, v) -> o.getGraphRagEntityResolution().setEnabled((Boolean) v)),

            bool("chunkEnrichment.enabled", "chunkEnrichment", "启用切块智能增强", "新构建任务使用 LLM 生成受控关键词和推荐问题。", NEW_BUILD_TASK, o -> o.getChunkEnrichment().isEnabled(), (o, v) -> o.getChunkEnrichment().setEnabled((Boolean) v)),
            integer("chunkEnrichment.maxKeywords", "chunkEnrichment", "关键词数量上限", "每个 chunk 最多保留的智能关键词数。", 1, 50, "个", NEW_BUILD_TASK, o -> o.getChunkEnrichment().getMaxKeywords(), (o, v) -> o.getChunkEnrichment().setMaxKeywords((Integer) v)),
            integer("chunkEnrichment.maxQuestions", "chunkEnrichment", "推荐问题数量上限", "每个 chunk 最多保留的智能问题数。", 1, 20, "个", NEW_BUILD_TASK, o -> o.getChunkEnrichment().getMaxQuestions(), (o, v) -> o.getChunkEnrichment().setMaxQuestions((Integer) v)),
            integer("chunkEnrichment.maxKeywordChars", "chunkEnrichment", "关键词长度上限", "单个智能关键词允许的最大字符数。", 2, 200, "字符", NEW_BUILD_TASK, o -> o.getChunkEnrichment().getMaxKeywordChars(), (o, v) -> o.getChunkEnrichment().setMaxKeywordChars((Integer) v)),
            integer("chunkEnrichment.maxQuestionChars", "chunkEnrichment", "推荐问题长度上限", "单个智能问题允许的最大字符数。", 2, 500, "字符", NEW_BUILD_TASK, o -> o.getChunkEnrichment().getMaxQuestionChars(), (o, v) -> o.getChunkEnrichment().setMaxQuestionChars((Integer) v)),
            integer("chunkEnrichment.batchChunkLimit", "chunkEnrichment", "增强批次块数", "单次关键词与问题增强调用最多包含的 chunk 数。", 1, 50, "块", NEW_BUILD_TASK, o -> o.getChunkEnrichment().getBatchChunkLimit(), (o, v) -> o.getChunkEnrichment().setBatchChunkLimit((Integer) v)),
            integer("chunkEnrichment.batchTokenLimit", "chunkEnrichment", "增强批次 Token 预算", "单次关键词与问题增强调用的估算 token 上限。", 256, 32000, "token", NEW_BUILD_TASK, o -> o.getChunkEnrichment().getBatchTokenLimit(), (o, v) -> o.getChunkEnrichment().setBatchTokenLimit((Integer) v)),

            integer("indexBuild.embeddingBatchSize", "indexBuild", "Embedding 批次大小", "单次 embedding 请求包含的 chunk 数，当前模型上限为 10。", 1, 10, "块", NEW_BUILD_TASK, o -> o.getIndexBuild().getEmbeddingBatchSize(), (o, v) -> o.getIndexBuild().setEmbeddingBatchSize((Integer) v)),
            integer("indexBuild.embeddingParallelism", "indexBuild", "Embedding 并发数", "索引构建阶段并发执行的 embedding 批次数。", 1, 16, "批", NEW_BUILD_TASK, o -> o.getIndexBuild().getEmbeddingParallelism(), (o, v) -> o.getIndexBuild().setEmbeddingParallelism((Integer) v)),
            integer("indexBuild.embeddingBatchMaxAttempts", "indexBuild", "Embedding 尝试次数", "单个 embedding 批次遇到瞬时网络失败时允许的最大尝试次数。", 1, 10, "次", NEW_BUILD_TASK, o -> o.getIndexBuild().getEmbeddingBatchMaxAttempts(), (o, v) -> o.getIndexBuild().setEmbeddingBatchMaxAttempts((Integer) v)),
            duration("indexBuild.embeddingBatchRetryBackoffMillis", "indexBuild", "Embedding 重试退避", "embedding 批次发生瞬时失败后再次尝试前的等待时间。", 0, 60000, NEW_BUILD_TASK, o -> o.getIndexBuild().getEmbeddingBatchRetryBackoffMillis(), (o, v) -> o.getIndexBuild().setEmbeddingBatchRetryBackoffMillis((Long) v)),
            bool("indexBuild.elasticsearchRefreshWait", "indexBuild", "等待 Elasticsearch 刷新", "构建写入后等待 refresh，使完成状态后的查询立即可见。", NEW_BUILD_TASK, o -> o.getIndexBuild().getElasticsearchRefreshWait(), (o, v) -> o.getIndexBuild().setElasticsearchRefreshWait((Boolean) v)),
            integer("indexBuild.progressLogLimit", "indexBuild", "进度日志返回数量", "前端轻量轮询接口单次返回的最新日志数量。", 1, 500, "条", NEW_BUILD_TASK, o -> o.getIndexBuild().getProgressLogLimit(), (o, v) -> o.getIndexBuild().setProgressLogLimit((Integer) v)),
            integer("indexBuild.executorPoolSize", "indexBuild", "构建线程池大小", "后台索引构建执行器的固定工作线程数。", 1, 32, "线程", RESTART_REQUIRED, o -> o.getIndexBuild().getExecutorPoolSize(), (o, v) -> o.getIndexBuild().setExecutorPoolSize((Integer) v)),
            integer("indexBuild.executorQueueCapacity", "indexBuild", "构建队列容量", "后台索引构建执行器允许排队的任务数量。", 0, 10000, "任务", RESTART_REQUIRED, o -> o.getIndexBuild().getExecutorQueueCapacity(), (o, v) -> o.getIndexBuild().setExecutorQueueCapacity((Integer) v)),

            integer("chunk.recursiveMaxChars", "chunking", "递归子块最大长度", "递归分块时单个子块允许的最大字符数。", 100, 8000, "字符", NEW_BUILD_TASK, o -> o.getChunk().getRecursiveMaxChars(), (o, v) -> o.getChunk().setRecursiveMaxChars((Integer) v)),
            integer("chunk.recursiveOverlapChars", "chunking", "递归子块重叠长度", "递归分块时相邻子块保留的重叠字符数。", 0, 7999, "字符", NEW_BUILD_TASK, o -> o.getChunk().getRecursiveOverlapChars(), (o, v) -> o.getChunk().setRecursiveOverlapChars((Integer) v)),
            integer("chunk.semanticMaxChars", "chunking", "语义子块最大长度", "语义分块时目标子块的最大字符数。", 100, 8000, "字符", NEW_BUILD_TASK, o -> o.getChunk().getSemanticMaxChars(), (o, v) -> o.getChunk().setSemanticMaxChars((Integer) v)),
            integer("chunk.semanticMinChars", "chunking", "语义子块最小长度", "语义分块时目标子块的最小字符数。", 80, 8000, "字符", NEW_BUILD_TASK, o -> o.getChunk().getSemanticMinChars(), (o, v) -> o.getChunk().setSemanticMinChars((Integer) v)),
            percentage("chunk.semanticSimilarityThreshold", "chunking", "语义合并阈值", "判断相邻文本是否继续合并为同一 chunk 的相似度比例。", NEW_BUILD_TASK, o -> o.getChunk().getSemanticSimilarityThreshold(), (o, v) -> o.getChunk().setSemanticSimilarityThreshold((Double) v)),
            integer("chunk.parentBlockMaxChars", "chunking", "父块最大长度", "回答阶段主要上下文父块允许的最大字符数。", 300, 20000, "字符", NEW_BUILD_TASK, o -> o.getChunk().getParentBlockMaxChars(), (o, v) -> o.getChunk().setParentBlockMaxChars((Integer) v)),
            integer("chunk.parentBlockOverlapChars", "chunking", "父块重叠长度", "父块递归裁切时保留的重叠字符数。", 0, 19999, "字符", NEW_BUILD_TASK, o -> o.getChunk().getParentBlockOverlapChars(), (o, v) -> o.getChunk().setParentBlockOverlapChars((Integer) v)),
            integer("chunk.parentSemanticMaxChars", "chunking", "父块语义最大长度", "父块语义切分时的最大字符数。", 300, 20000, "字符", NEW_BUILD_TASK, o -> o.getChunk().getParentSemanticMaxChars(), (o, v) -> o.getChunk().setParentSemanticMaxChars((Integer) v)),
            integer("chunk.parentSemanticMinChars", "chunking", "父块语义最小长度", "父块语义切分时的最小字符数。", 120, 20000, "字符", NEW_BUILD_TASK, o -> o.getChunk().getParentSemanticMinChars(), (o, v) -> o.getChunk().setParentSemanticMinChars((Integer) v)),
            bool("chunk.llmEnabled", "chunking", "启用大模型智能切块", "新构建任务允许使用大模型辅助切块。", NEW_BUILD_TASK, o -> o.getChunk().getLlmEnabled(), (o, v) -> o.getChunk().setLlmEnabled((Boolean) v)),
            integer("chunk.llmMaxChars", "chunking", "智能切块输入长度", "单次送给智能切块模型的最大字符数。", 300, 50000, "字符", NEW_BUILD_TASK, o -> o.getChunk().getLlmMaxChars(), (o, v) -> o.getChunk().setLlmMaxChars((Integer) v)),
            bool("chunk.recommendLlmWhenLowQuality", "chunking", "低质量时推荐智能切块", "文档质量较差时向用户推荐追加大模型智能切块。", NEW_BUILD_TASK, o -> o.getChunk().getRecommendLlmWhenLowQuality(), (o, v) -> o.getChunk().setRecommendLlmWhenLowQuality((Boolean) v))
        );
        LinkedHashMap<String, ConfigDefinition> byKey = new LinkedHashMap<>();
        definitions.forEach(definition -> byKey.put(definition.key(), definition));
        if (byKey.size() != definitions.size()) {
            throw new IllegalStateException("系统配置注册表存在重复键");
        }
        definitionsByKey = Map.copyOf(byKey);
    }

    public List<ConfigDefinition> definitions() { return definitions; }

    public ConfigDefinition require(String key) {
        ConfigDefinition definition = definitionsByKey.get(key);
        if (definition == null) throw parameterError("不支持的配置项: " + key);
        return definition;
    }

    public SystemConfigSnapshot apply(SystemConfigSnapshot source, String key, JsonNode value) {
        SystemConfigSnapshot updated = copy(source);
        applyStoredValue(updated, require(key), value);
        validateSnapshot(updated);
        return updated;
    }

    public SystemConfigSnapshot copy(SystemConfigSnapshot source) {
        SystemConfigSnapshot copy = SystemConfigSnapshot.defaults();
        SystemConfigSnapshot actual = source == null ? SystemConfigSnapshot.defaults() : source;
        definitions.forEach(definition -> definition.setter().accept(copy, definition.getter().apply(actual)));
        return copy;
    }

    public void applyStoredValue(SystemConfigSnapshot target, ConfigDefinition definition, JsonNode value) {
        definition.setter().accept(target, parseValue(definition, value));
    }

    public Object read(SystemConfigSnapshot snapshot, ConfigDefinition definition) {
        return definition.getter().apply(snapshot);
    }

    public void validateSnapshot(SystemConfigSnapshot snapshot) {
        RagRuntimeOptions runtime = snapshot.getRagRuntime();
        if (runtime.getCandidateTopK() < runtime.getRerankCandidateTopK()) throw parameterError("candidateTopK 不能小于 rerankCandidateTopK。");
        if (runtime.getRerankCandidateTopK() < runtime.getFinalTopK()) throw parameterError("rerankCandidateTopK 不能小于 finalTopK。");
        if (runtime.getRaptorSourceChunkTopK() > runtime.getRaptorTopK()) throw parameterError("raptorSourceChunkTopK 不能大于 raptorTopK。");
        if (runtime.getChannelTimeoutMs() > runtime.getSubQuestionTimeoutMs()) throw parameterError("单通道超时不能大于子问题总超时。");
        if (snapshot.getChat().getMaxModelCallsPerThread() < snapshot.getChat().getMaxModelCallsPerRun()) throw parameterError("会话模型调用上限不能小于单次模型调用上限。");
        if (snapshot.getChat().getMaxToolCallsPerThread() < snapshot.getChat().getMaxToolCallsPerRun()) throw parameterError("会话工具调用上限不能小于单次工具调用上限。");
        if (snapshot.getRag().getPerSubQuestionEvidenceMaxChars() > snapshot.getRag().getTotalEvidenceMaxChars()) throw parameterError("单子问题证据预算不能大于总证据预算。");
        DocumentManageProperties.Chunk chunk = snapshot.getChunk();
        if (chunk.getRecursiveOverlapChars() >= chunk.getRecursiveMaxChars()) throw parameterError("递归子块重叠长度必须小于最大长度。");
        if (chunk.getSemanticMinChars() > chunk.getSemanticMaxChars()) throw parameterError("语义子块最小长度不能大于最大长度。");
        if (chunk.getParentBlockOverlapChars() >= chunk.getParentBlockMaxChars()) throw parameterError("父块重叠长度必须小于父块最大长度。");
        if (chunk.getParentSemanticMinChars() > chunk.getParentSemanticMaxChars()) throw parameterError("父块语义最小长度不能大于最大长度。");
    }

    private Object parseValue(ConfigDefinition definition, JsonNode value) {
        if (value == null || value.isNull()) throw parameterError(definition.label() + "不能为空。");
        if ("BOOLEAN".equals(definition.valueType())) {
            if (!value.isBoolean()) throw parameterError(definition.label() + "必须是布尔值。");
            return value.booleanValue();
        }
        if ("STRING".equals(definition.valueType())) {
            if (!value.isTextual()) throw parameterError(definition.label() + "必须是文本。");
            String text = value.textValue();
            if (text == null || text.isBlank()) throw parameterError(definition.label() + "不能为空。");
            if (definition.maxLength() != null && text.length() > definition.maxLength()) throw parameterError(definition.label() + "不能超过 " + definition.maxLength() + " 个字符。");
            return text;
        }
        if (!value.isNumber()) throw parameterError(definition.label() + "必须是数字。");
        BigDecimal decimal = value.decimalValue();
        if ((definition.minValue() != null && decimal.compareTo(definition.minValue()) < 0)
            || (definition.maxValue() != null && decimal.compareTo(definition.maxValue()) > 0)) {
            throw parameterError(definition.label() + "超出允许范围。仅允许 "
                + definition.minValue().stripTrailingZeros().toPlainString() + " 到 "
                + definition.maxValue().stripTrailingZeros().toPlainString() + "。");
        }
        return switch (definition.valueType()) {
            case "INTEGER" -> {
                if (!value.isIntegralNumber() || !value.canConvertToInt()) throw parameterError(definition.label() + "必须是整数。");
                yield value.intValue();
            }
            case "LONG" -> {
                if (!value.isIntegralNumber() || !value.canConvertToLong()) throw parameterError(definition.label() + "必须是整数。");
                yield value.longValue();
            }
            case "DECIMAL" -> value.doubleValue();
            default -> throw parameterError("不支持的配置类型: " + definition.valueType());
        };
    }

    private static ConfigDefinition integer(String key, String category, String label, String description, int min, int max, String unit, String mode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter) {
        return definition(key, category, label, description, "INTEGER", "NUMBER", min, max, 1, 1, unit, null, mode, getter, setter);
    }

    private static ConfigDefinition duration(String key, String category, String label, String description, long min, long max, String mode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter) {
        return definition(key, category, label, description, "LONG", "DURATION", min, max, 100, 1, "毫秒", null, mode, getter, setter);
    }

    private static ConfigDefinition percentage(String key, String category, String label, String description, String mode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter) {
        return definition(key, category, label, description, "DECIMAL", "PERCENTAGE", 0, 1, 0.01, 100, "%", null, mode, getter, setter);
    }

    private static ConfigDefinition decimal(String key, String category, String label, String description, double min, double max, double step, String unit, String mode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter) {
        return definition(key, category, label, description, "DECIMAL", "NUMBER", min, max, step, 1, unit, null, mode, getter, setter);
    }

    private static ConfigDefinition bool(String key, String category, String label, String description, String mode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter) {
        return definition(key, category, label, description, "BOOLEAN", "CHECKBOX", null, null, null, 1, "", null, mode, getter, setter);
    }

    private static ConfigDefinition text(String key, String category, String label, String description, int maxLength, String mode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter) {
        return definition(key, category, label, description, "STRING", "TEXTAREA", null, null, null, 1, "", maxLength, mode, getter, setter);
    }

    private static ConfigDefinition definition(String key, String category, String label, String description, String valueType, String controlType, Number min, Number max, Number step, int displayScale, String unit, Integer maxLength, String mode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter) {
        return new ConfigDefinition(key, category, label, description, valueType, controlType, decimal(min), decimal(max), decimal(step), displayScale, unit, maxLength, mode, getter, setter);
    }

    private static BigDecimal decimal(Number value) { return value == null ? null : new BigDecimal(value.toString()); }
    private SuperAgentFrameException parameterError(String message) { return new SuperAgentFrameException(BaseCode.PARAMETER_ERROR.getCode(), message); }

    public record ConfigDefinition(
        String key, String categoryKey, String label, String description, String valueType, String controlType,
        BigDecimal minValue, BigDecimal maxValue, BigDecimal step, int displayScale, String unit, Integer maxLength,
        String effectiveMode, Function<SystemConfigSnapshot, Object> getter, BiConsumer<SystemConfigSnapshot, Object> setter
    ) { }
}
