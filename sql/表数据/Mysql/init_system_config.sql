-- 全局系统配置初始快照（schemaVersion=2，共 101 项）。
-- 请在 create_table_mysql.sql 建表完成后执行。本脚本可重复执行，已有 id=1 配置不会被覆盖。
-- 知识库级 retrieval_config_json / graph_rag_config_json / raptor_config_json 只保存差异项，空值自动继承本快照。
-- 总分类和小分类使用独立关系表维护，不写入 config_json，也不改变 schemaVersion。
-- 首次初始化时先执行 create_table_mysql.sql，再执行本脚本写入分类和默认配置。

USE aegis_kb;
    
START TRANSACTION;

INSERT IGNORE INTO aegis_kb_system_config_group
    (id, group_key, group_label, group_description, sort_order, create_time, edit_time, status)
VALUES
    (1, 'retrieval', '检索与排序', '控制召回、通道、阈值、融合和执行边界。', 10, NOW(), NOW(), 1),
    (2, 'conversation', '对话与回答', '控制对话助手、RAG 编排和回答上下文。', 20, NOW(), NOW(), 1),
    (3, 'documentBuild', '文档与索引', '控制切块、内容增强、索引和 RAPTOR 构建。', 30, NOW(), NOW(), 1),
    (4, 'graphRag', 'GraphRAG', '控制知识图谱构建和 LLM 受控增强。', 40, NOW(), NOW(), 1);

INSERT IGNORE INTO aegis_kb_system_config_category
    (id, group_key, category_key, category_label, category_description, sort_order, create_time, edit_time, status)
VALUES
    (101, 'retrieval', 'retrievalWindow', '召回与窗口', '控制各通道召回规模和候选裁剪窗口。', 10, NOW(), NOW(), 1),
    (102, 'retrieval', 'execution', '执行与超时', '控制重排开关和检索等待边界。', 20, NOW(), NOW(), 1),
    (103, 'retrieval', 'relevance', '相关性阈值', '控制低相关候选进入后续阶段的最低要求。', 30, NOW(), NOW(), 1),
    (104, 'retrieval', 'channels', '检索通道', '控制新会话允许调度的检索通道。', 40, NOW(), NOW(), 1),
    (105, 'retrieval', 'fusion', '融合与排序', '控制多通道特征在统一排序窗口中的权重。', 50, NOW(), NOW(), 1),
    (106, 'conversation', 'chatAgent', '对话助手', '控制推荐追问、调用保护、历史轮数和执行超时。', 10, NOW(), NOW(), 1),
    (107, 'conversation', 'ragOrchestration', 'RAG 编排', '控制问题改写、拆问和受控查询计划。', 20, NOW(), NOW(), 1),
    (108, 'conversation', 'ragContext', '上下文与回答', '控制历史、证据预算、长期摘要和回答文案。', 30, NOW(), NOW(), 1),
    (109, 'documentBuild', 'chunking', '切块策略', '控制子块、父块和智能切块的全局默认值。', 10, NOW(), NOW(), 1),
    (110, 'documentBuild', 'chunkEnrichment', '切块内容增强', '控制构建期智能关键词和问题生成。', 20, NOW(), NOW(), 1),
    (111, 'documentBuild', 'indexBuild', '索引构建', '控制 embedding 批次、并发、重试、刷新和执行器。', 30, NOW(), NOW(), 1),
    (112, 'documentBuild', 'raptorBuild', 'RAPTOR 构建', '控制摘要树规模、模型摘要和质量门槛。', 40, NOW(), NOW(), 1),
    (113, 'graphRag', 'graphRagBuild', 'GraphRAG 构建', '控制构建租约、尝试次数和重试等待。', 10, NOW(), NOW(), 1),
    (114, 'graphRag', 'graphRagEnhancement', 'GraphRAG 增强', '控制受控抽取、社区报告和实体消歧。', 20, NOW(), NOW(), 1);

INSERT INTO aegis_kb_system_config
    (id, config_version, config_json, create_time, edit_time, status)
SELECT
    1,
    0,
    JSON_OBJECT(
        'schemaVersion', 2,
        'values', JSON_OBJECT(
            'ragRuntime.vectorTopK', 10,
            'ragRuntime.keywordTopK', 10,
            'ragRuntime.graphRagTopK', 5,
            'ragRuntime.graphRagMaxHops', 2,
            'ragRuntime.raptorTopK', 5,
            'ragRuntime.raptorSourceChunkTopK', 3,
            'ragRuntime.candidateTopK', 40,
            'ragRuntime.rerankCandidateTopK', 24,
            'ragRuntime.finalTopK', 6,
            'ragRuntime.rerankEnabled', TRUE,
            'ragRuntime.channelTimeoutMs', 12000,
            'ragRuntime.subQuestionTimeoutMs', 40000,
            'ragRuntime.minVectorSimilarity', 0.45,
            'ragRuntime.keywordRelativeScoreFloor', 0.35,
            'ragRuntime.keywordChannelEnabled', TRUE,
            'ragRuntime.tableChannelEnabled', TRUE,
            'ragRuntime.graphRagChannelEnabled', TRUE,
            'ragRuntime.raptorChannelEnabled', TRUE,
            'ragRuntime.hybrid.vectorWeight', 1,
            'ragRuntime.hybrid.keywordWeight', 1,
            'ragRuntime.hybrid.tableWeight', 1.2,
            'ragRuntime.hybrid.graphRagWeight', 1.1,
            'ragRuntime.hybrid.raptorWeight', 1.05,
            'ragRuntime.hybrid.rankWeight', 1,
            'ragRuntime.hybrid.originalScoreWeight', 0.08,
            'ragRuntime.hybrid.metadataBoostWeight', 0.04,
            'ragRuntime.hybrid.maxMetadataBoost', 1,
            'chat.recommendationEnabled', TRUE,
            'chat.maxModelCallsPerRun', 8,
            'chat.maxModelCallsPerThread', 40,
            'chat.maxToolCallsPerRun', 6,
            'chat.maxToolCallsPerThread', 30,
            'chat.historyPreviewTurns', 4,
            'chat.recommendationTimeoutMs', 3000,
            'rag.enabled', TRUE,
            'rag.rewriteEnabled', TRUE,
            'rag.rewriteHistoryTurns', 4,
            'rag.rewriteOptions.enabled', TRUE,
            'rag.rewriteOptions.temperature', 0.2,
            'rag.rewriteOptions.topP', 0.3,
            'rag.rewriteOptions.thinking', FALSE,
            'rag.maxSubQuestions', 4,
            'rag.graphRagQueryPlanEnabled', TRUE,
            'rag.autoRouteRecommendationThreshold', 0.55,
            'rag.parentEvidenceMaxChars', 2200,
            'rag.planningHistoryMaxChars', 1600,
            'rag.answerHistoryMaxChars', 1000,
            'rag.totalEvidenceMaxChars', 5200,
            'rag.perSubQuestionEvidenceMaxChars', 2200,
            'rag.noEvidenceReply', '当前没有从当前文档中检索到足够证据，暂时不能给出可靠结论。你可以补充更具体的章节名、标题或关键词后再试。',
            'rag.historySummary.enabled', TRUE,
            'rag.historySummary.keepRecentTurns', 4,
            'rag.historySummary.compressionBatchTurns', 6,
            'rag.historySummary.recentTranscriptMaxChars', 2200,
            'rag.historySummary.summaryMaxChars', 1400,
            'rag.raptorMaxClusterSize', 6,
            'rag.raptorMaxLevels', 3,
            'rag.raptorLlmSummaryEnabled', TRUE,
            'rag.raptorSummaryQualityFloor', 0.42,
            'graphRag.build.leaseEnabled', TRUE,
            'graphRag.build.leaseTtlSeconds', 6000,
            'graphRag.build.maxAttempts', 2,
            'graphRag.build.retryBackoffMillis', 500,
            'graphRag.extraction.enabled', TRUE,
            'graphRag.extraction.batchChunkLimit', 3,
            'graphRag.extraction.batchTokenLimit', 2800,
            'graphRag.extraction.maxEntitiesPerCall', 8,
            'graphRag.extraction.maxRelationsPerCall', 6,
            'graphRag.extraction.maxEvidencesPerCall', 8,
            'graphRag.extraction.maxQuoteChars', 180,
            'graphRag.extraction.maxReasonChars', 240,
            'graphRag.extraction.maxUnitTextChars', 420,
            'graphRag.communityReport.enabled', TRUE,
            'graphRag.entityResolution.enabled', TRUE,
            'chunkEnrichment.enabled', TRUE,
            'chunkEnrichment.maxKeywords', 8,
            'chunkEnrichment.maxQuestions', 4,
            'chunkEnrichment.maxKeywordChars', 32,
            'chunkEnrichment.maxQuestionChars', 40,
            'chunkEnrichment.batchChunkLimit', 6,
            'chunkEnrichment.batchTokenLimit', 2400,
            'indexBuild.embeddingBatchSize', 5,
            'indexBuild.embeddingParallelism', 1,
            'indexBuild.embeddingBatchMaxAttempts', 3,
            'indexBuild.embeddingBatchRetryBackoffMillis', 1200,
            'indexBuild.elasticsearchRefreshWait', TRUE,
            'indexBuild.progressLogLimit', 60,
            'indexBuild.executorPoolSize', 2,
            'indexBuild.executorQueueCapacity', 32,
            'chunk.recursiveMaxChars', 800,
            'chunk.recursiveOverlapChars', 120,
            'chunk.semanticMaxChars', 700,
            'chunk.semanticMinChars', 240,
            'chunk.semanticSimilarityThreshold', 0.18,
            'chunk.parentBlockMaxChars', 2200,
            'chunk.parentBlockOverlapChars', 180,
            'chunk.parentSemanticMaxChars', 1600,
            'chunk.parentSemanticMinChars', 480,
            'chunk.llmEnabled', FALSE,
            'chunk.llmMaxChars', 3500,
            'chunk.recommendLlmWhenLowQuality', TRUE
        )
    ),
    NOW(),
    NOW(),
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM aegis_kb_system_config
    WHERE id = 1
);

COMMIT;
