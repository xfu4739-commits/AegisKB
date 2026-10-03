# S03：Source Evidence 与 Context Only 硬分池

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S03 的工程实施合同；具体执行结果、偏差和证据见 [records/S03.md](../records/S03.md)。

## 阶段设计依据

数据库已经出现 Context Only 被标记为 selected 的反例，说明标签不足以形成权限边界。本阶段在候选模型、选择策略、Prompt 和归档前建立物理分池，Context 不得获得 reference ID 或进入最终 Source snapshot。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

## 2026-07-15 现行协议勘误（REOPENED 时优先）

S03 最初验收中使用的 `ACCEPTED_SUPPORT`、`repairCandidateIdentities` 和 citation-direction premise 属于已被 S02 删除的历史 repair/NLI 协议，只能在 `records/S03.md` 中作为当时证据读取，不得作为重开后的施工入口。现行链路是 Prompt rendered/reused Source -> `explicitCitationEligibleIdentities` -> explicit `[n]` token binding -> final source snapshot；S03 只拥有 Source/Context 物理分池和 identity capability 边界。

唯一目标：在任何 final selection 前物理分开 citation-capable Source Evidence 和 Context Only，并把 Graph/RAPTOR source 下钻语义收口。

前置阶段：S02。

必读：[改造计划与进度](../改造计划与进度.md) 中“问题基线与阶段归属”和“目标架构与权威边界”的对应条目。

核心文件：

- `rag/service/ContextExpansionPlanner.java`
- `rag/service/EvidenceBudgetAllocator.java`
- `rag/service/RagRetrievalEngine.java`
- `rag/model/SubQuestionEvidence.java`
- `rag/model/RagRetrievalContext.java`
- `rag/support/SearchReferenceMapper.java`
- `rag/support/EvidenceIdentityResolver.java`
- `EvidenceIdentityResolverTest.java`
- `EvidenceCandidateNormalizerTest.java`
- `EvidenceBudgetAllocatorTest.java`
- `RaptorRetrievalChannelTest.java`
- `GraphRagRetrievalChannelTest.java`

约束：

- Source Evidence 必须能回到 raw chunk、table row/cell 或有真实 source quote 的 KG evidence。
- Graph relation wrapper、community summary、RAPTOR summary 和未解析的 source parent 一律保持 Context Only。
- Context Only 不占 finalTopK、不获得 citation reference ID、不进入 `explicitCitationEligibleIdentities`、`CITATION_BINDING` 或 final citation；可进入独立 Prompt background。

实施清单：

- [x] 写混合候选、纯 Context Only、重复 identity 和 source 下钻失败的测试。
- [x] allocator 输入只允许 Source Evidence pool。
- [x] ContextExpansionPlanner 输出独立 context pool，不再 prepend 到 ranked source candidates。
- [x] RAPTOR `SOURCE_PARENT_BLOCK` 只有成功解析 raw source 才可升级为 Source Evidence。
- [x] GraphRAG 只有 quote 与真实 chunk 绑定成功才可升级为 Source Evidence。
- [x] keyword raw chunk 映射保留 `ORIGINAL_SNIPPET` / source-authored quote provenance，typed Graph wrapper 不得继承该 provenance 或 citation identity。
- [x] 改写允许 structure context 被 selected 的旧测试。
- [x] O9 分别记录 `BUDGET_SELECTED_SOURCE` 和 `PROMPT_RENDERED_CONTEXT`。

数据库验收：

~~~sql
SELECT COALESCE(SUM(context_only = 1 AND is_selected = 1), 0) AS context_selected,
       COALESCE(SUM(is_selected = 1
                    AND (context_only = 1
                         OR source_evidence_resolved = 0
                         OR COALESCE(citation_identity, '') = '')), 0) AS invalid_selected
FROM aegis_kb_chat_retrieval_result
WHERE exchange_id > :exchange_watermark;
~~~

两个结果都必须为 0。按 O8-01 -> O8-02 -> O8-03 的原始多轮顺序新跑“还有这个问题的处理步骤都有什么？”，确认 history 正确继承，结构背景可渲染，但不进入 source/citation 链。

若处理步骤正文由 keyword 通道进入 Source，还必须确认它携带 source-authored 原文和稳定 citation identity，并通过 Source manifest 取得唯一 reference ID；typed Graph wrapper 仍为 Context，不得借用 Source identity 或进入 explicit binding。

## 退出条件

- [x] 在 final evidence selection/allocator 前形成物理独立的 Source Evidence pool 与 Context Only pool；allocator 只消费 Source pool。
- [x] Context 不占 `finalTopK`、不获得 reference ID、不进入 `explicitCitationEligibleIdentities`、`CITATION_BINDING` 或 final citation，只能通过独立 background/disposition 进入 Prompt。
- [x] 使用修复后新启动的代码和事前 exchange watermark 之后的新 conversation/exchange 验收，未复用旧 cohort 宣布通过。
- [x] 新 cohort 合计和逐 exchange 均满足 `context_selected=0`、`invalid_selected=0`。
- [x] 每轮 `BUDGET_SELECTED_SOURCE == renderedSourceIdentities == explicitCitationEligibleIdentities`；Context identity 不混入 Source manifest、binding 或 final source snapshot。
- [x] Source 保留 source-authored 原文和稳定 identity；query echo、Context wrapper 和 typed Graph wrapper 不得获得 Source 引用能力。
- [x] `ElasticsearchDocumentKeywordSearchGatewayTest` 红绿 fixture 直接验证 keyword raw producer 的 `chunkText -> ORIGINAL_SNIPPET -> SearchReference.quoteText` 映射契约；修复后的真实新 cohort 独立验证 Source/Prompt/explicit-eligibility 链无 query echo 或 wrapper identity 污染。真实 cohort 不要求指定 keyword candidate 必须进入 Prompt。
- [x] Parent、Graph、RAPTOR wrapper 保持 Context；只有满足 raw chunk/真实 quote 绑定和 canonical citation identity 的 source projection 才可进入 Source pool。
- [x] 阶段定向测试、受影响回归、chat 全量、14 模块编译、静态双门禁和 `git diff --check` 均通过，完整证据记录在 `records/S03.md` 最新位置。
- [x] `O8-RD-PARSE-01`、`O8-RD-FINAL-01`、`O8-RD-FINAL-02` 已作为内部诊断路由给后续 owner；它们属于解析/投影、合法 Source 排序或答案完整度问题，不构成 S03 Source/Context identity 边界的退出阻塞项。Explicit binding 的实现回归归 S02。
