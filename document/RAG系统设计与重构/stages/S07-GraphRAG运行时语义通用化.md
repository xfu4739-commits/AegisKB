# S07：GraphRAG 运行时语义通用化

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S07 的工程实施合同；具体执行结果、偏差和证据见 [records/S07.md](../records/S07.md)。

## 阶段设计依据

旧 GraphRAG 运行时使用特定关系名和当前样例语义决定证据质量，无法迁移到陌生领域。本阶段把关系、路径和 source lineage 转换为通用质量特征，禁止关系词、部门词和验收样例直接决定 Source 资格。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

## 2026-07-15 现行 citation 协议勘误（REOPENED 时优先）

S07 当时 records 中的 repair/NLI 过程仍是历史事实，但未来重开只使用现行 capability 链：未绑定 raw quote 的 Graph wrapper/Context 不进入 `explicitCitationEligibleIdentities`、`CITATION_BINDING` 或 final source snapshot；具备稳定 citation identity 的 Graph Source 才能经过唯一 final policy、Prompt reference 和 explicit binding。

唯一目标：移除 relation name 驱动的强弱、reserve 和回答约束，统一为与领域无关的 source/grounding/quality 特征。

前置阶段：S06。

必读：[改造计划与进度](../改造计划与进度.md) 中“问题基线与阶段归属”和“目标架构与权威边界”的对应条目。

核心文件：

- `EvidenceQualityFeatures.java`
- `EvidenceCandidateNormalizer.java`
- `EvidenceIdentityResolver.java`
- `RagChatExecutor.java`
- `GraphRagTypedChunkMetadataSupport.java`
- `DocumentKnowledgeServiceImpl.java`
- `GraphRagSearchServiceImpl.java`
- `GraphRagCrossDocumentIndexSupport.java`
- `GraphRagRetrievalChannel.java`
- `HybridFusionService.java`
- `EvidenceBudgetAllocator.java`
- `RankFeatureService.java`
- `prompt/rag-answer-system.st`
- `application.yaml`

通用特征示例：`sourceCapability`、`groundingConfidence`、`quoteCoverage`、`sourceKind`、`qualitySignals`。具体命名依现有模型确定，但只能有一个权威解释位置。

## 跨阶段冻结反例责任

必读：[S03 新代码真实 cohort 与阶段退出](../records/S03.md#2026-07-12-新代码真实-cohort-与阶段退出)中的 `O8-RD-FINAL-01`、`O8-RD-FINAL-02` 数据证据。

- `DATA_CONFIRMED`：合法现象正文旧 chunk `...2598` 和正确处理步骤旧 chunk `...2605` 都曾被 finalTopK 过滤，同时 final 候选包含 Graph/RAPTOR 标题或其他章节 Source。
- `HYPOTHESIS`：Graph relation 名称、来源 reserve 或非通用质量信号可能影响候选竞争；S07 开工后必须沿 producer -> feature transition -> fusion consumer 核验，未经核验不得以这两个样例修改生产逻辑。
- 本阶段只拥有 Graph 候选质量特征的通用化：只有绑定真实 raw quote 并具 citation capability 的 Graph Source 才能进入 Source 竞争，其强弱只能由 source capability、grounding/coverage 等通用字段解释；未绑定 Source 的 Graph wrapper/community 保持 Context，走独立 background/disposition，不能因 relation 名、文档章节或来源标签获得隐式优先级。
- 本阶段不拥有 Markdown `TITLE` 产物修复（S10-S11），也不拥有最终保留/淘汰 policy 和窗口切流（S08-S09）。

本阶段 fixture 必须使用陌生 relation，同时构造 source-bound Graph Source、未绑定 Source 的 Graph Context 和通用 raw Source：断言 Context 根本不进入 Source policy/window，通用质量较低但已绑定 raw quote 的 Graph Source 也不会仅凭名称或来源挤压高质量 raw Source。不得硬编码验收问题、文档名、章节号或上述旧 chunk ID。退出记录要说明 `O8-RD-FINAL-01/02` 是否存在已确认的 S07 根因；即使不存在，也要给出代码和 O9 证据后再把实际关闭责任留给 S08-S09。

## 2026-07-13 新 cohort 事实勘误

- `DATA_CONFIRMED`：新 exchange `2330399089591599223 / 2330399089591599271 / 2330399089591599319` 中，直接 `graph-rag` channel 的 Graph Source 正文已是 raw quote；但 vector/hybrid 命中的 `GRAPH_ENTITY` 派生 chunk `...5450 / ...5529` 仍把整段 Graph wrapper 作为 `KG_QUOTE` Source 进入 Prompt。
- `CODE_CONFIRMED + DATA_CONFIRMED`：keyword gateway 已排除 Graph typed wrapper 的 `ORIGINAL_SNIPPET`，vector gateway 的 `DocumentKnowledgeServiceImpl.buildRetrievedDocument` 却仍无条件把 typed chunk wrapper 写为 `ORIGINAL_SNIPPET`；`EvidenceIdentityResolver` 因此错误授予 citation capability。这是 S07 的 provider 投影不一致，不是 S08-S09 的 final policy/window 问题。
- `DATA_CONFIRMED`：上述三个 exchange 的全部 O9 stage snapshot 与 exchange debug JSON 均未出现 `evidenceSourceCapability / evidenceGroundingConfidence / evidenceQuoteCoverage / evidenceSourceKind / evidenceQualitySignals / evidenceQualitySignalSource`。生产候选 metadata 已有 producer，但 `RAG_RETRIEVE` trace 没有投影 Source/Context Document 的质量字段，原“可直接观测”结论不成立。
- `DATA_CONFIRMED`：未绑定 quote 的 Graph `PARENT:*` 候选保持 `PROMPT_RENDERED_CONTEXT / OMITTED_BUDGET`，没有进入 Prompt Source 或 citation；本轮修复只收紧 Graph typed wrapper 的 Context 边界并补 O9，不修改 Markdown TITLE 产物和唯一 Source 窗口策略。

实施清单：

- [x] 陌生 relation type 的 characterization test。
- [x] fusion/budget/prompt/config 不再用具体 relation 名决定强弱。
- [x] relation type 仍可作为事实展示，不参与散落的业务特判。
- [x] 没有真实 raw quote、稳定 source identity 和 citation capability 的 Graph entity/relation/community/path wrapper 必须为 `CONTEXT_ONLY`；只有绑定真实 raw quote 的 Graph evidence 才能成为 Source，不保留低能力 Source、wrapper 解析或来源型升级兜底。
- [x] O9 记录 Source/Context 候选的通用质量特征及其来源。

退出条件：

- 静态门禁不再命中当前关系枚举驱动的排名、预算或 Prompt 主链路；陌生关系只凭通用质量字段正确工作。
- 真实 O9 的 `RAG_RETRIEVE.subQuestions[*].evidenceCandidates` 对每个 Source/Context 候选完整记录 `evidenceSourceCapability / evidenceGroundingConfidence / evidenceQuoteCoverage / evidenceSourceKind / evidenceQualitySignals / evidenceQualitySignalSource`。
- `GRAPH_ENTITY / GRAPH_RELATION / GRAPH_COMMUNITY` typed wrapper 和其他未绑定 quote 的 Graph wrapper 均为 `CONTEXT_ONLY`，不进入 Prompt Source、`explicitCitationEligibleIdentities`、`CITATION_BINDING` 或 final source snapshot。
- direct Graph Source 的 `textPreview`、Prompt Source 和 source evidence quote 必须一致且都是 raw quote，不包含用户问题、图谱路径或其他 query wrapper。
- 本阶段不修改或运行 Python 抽取；Python GraphRAG 候选生成语义继续由 S13-S14 负责。
