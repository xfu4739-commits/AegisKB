-- 修复 Docker 首次 init 时 mysql 客户端字符集错误导致的分类中文乱码。
-- 可重复执行：按稳定键覆盖 group/category 文案，并修正 config_json 中的中文默认值。
-- 执行示例（仓库根目录）：
--   Get-Content -Encoding UTF8 sql/表数据/Mysql/repair_system_config_charset.sql |
--     docker exec -i aegis-kb-mysql mysql -uroot -proot --default-character-set=utf8mb4 aegis_kb

USE aegis_kb;

START TRANSACTION;

UPDATE aegis_kb_system_config_group SET
    group_label = '检索与排序',
    group_description = '控制召回、通道、阈值、融合和执行边界。',
    edit_time = NOW()
WHERE group_key = 'retrieval' AND status = 1;

UPDATE aegis_kb_system_config_group SET
    group_label = '对话与回答',
    group_description = '控制对话助手、RAG 编排和回答上下文。',
    edit_time = NOW()
WHERE group_key = 'conversation' AND status = 1;

UPDATE aegis_kb_system_config_group SET
    group_label = '文档与索引',
    group_description = '控制切块、内容增强、索引和 RAPTOR 构建。',
    edit_time = NOW()
WHERE group_key = 'documentBuild' AND status = 1;

UPDATE aegis_kb_system_config_group SET
    group_description = '控制知识图谱构建和 LLM 受控增强。',
    edit_time = NOW()
WHERE group_key = 'graphRag' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '召回与窗口',
    category_description = '控制各通道召回规模和候选裁剪窗口。',
    edit_time = NOW()
WHERE category_key = 'retrievalWindow' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '执行与超时',
    category_description = '控制重排开关和检索等待边界。',
    edit_time = NOW()
WHERE category_key = 'execution' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '相关性阈值',
    category_description = '控制低相关候选进入后续阶段的最低要求。',
    edit_time = NOW()
WHERE category_key = 'relevance' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '检索通道',
    category_description = '控制新会话允许调度的检索通道。',
    edit_time = NOW()
WHERE category_key = 'channels' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '融合与排序',
    category_description = '控制多通道特征在统一排序窗口中的权重。',
    edit_time = NOW()
WHERE category_key = 'fusion' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '对话助手',
    category_description = '控制推荐追问、调用保护、历史轮数和执行超时。',
    edit_time = NOW()
WHERE category_key = 'chatAgent' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = 'RAG 编排',
    category_description = '控制问题改写、拆问和受控查询计划。',
    edit_time = NOW()
WHERE category_key = 'ragOrchestration' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '上下文与回答',
    category_description = '控制历史、证据预算、长期摘要和回答文案。',
    edit_time = NOW()
WHERE category_key = 'ragContext' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '切块策略',
    category_description = '控制子块、父块和智能切块的全局默认值。',
    edit_time = NOW()
WHERE category_key = 'chunking' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '切块内容增强',
    category_description = '控制构建期智能关键词和问题生成。',
    edit_time = NOW()
WHERE category_key = 'chunkEnrichment' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = '索引构建',
    category_description = '控制 embedding 批次、并发、重试、刷新和执行器。',
    edit_time = NOW()
WHERE category_key = 'indexBuild' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = 'RAPTOR 构建',
    category_description = '控制摘要树规模、模型摘要和质量门槛。',
    edit_time = NOW()
WHERE category_key = 'raptorBuild' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = 'GraphRAG 构建',
    category_description = '控制构建租约、尝试次数和重试等待。',
    edit_time = NOW()
WHERE category_key = 'graphRagBuild' AND status = 1;

UPDATE aegis_kb_system_config_category SET
    category_label = 'GraphRAG 增强',
    category_description = '控制受控抽取、社区报告和实体消歧。',
    edit_time = NOW()
WHERE category_key = 'graphRagEnhancement' AND status = 1;

UPDATE aegis_kb_system_config
SET config_json = JSON_SET(
        config_json,
        '$.values."rag.noEvidenceReply"',
        '当前没有从当前文档中检索到足够证据，暂时不能给出可靠结论。你可以补充更具体的章节名、标题或关键词后再试。'
    ),
    edit_time = NOW()
WHERE id = 1 AND status = 1;

COMMIT;
