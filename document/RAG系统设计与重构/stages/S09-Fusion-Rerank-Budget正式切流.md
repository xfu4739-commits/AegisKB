# S09：Fusion/Rerank/Budget 正式切流

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S09 的工程实施合同；具体执行结果、偏差和证据见 [records/S09.md](../records/S09.md)。

## 阶段设计依据

旧 Q-ROUTE-03 暴露了 raw Source、Parent、TITLE metadata、rerank score 与 citation identity 分离后被多层窗口淘汰的问题。本阶段正式切流稳定 candidate identity、fusion 特征、唯一 ranking window、真实 rerank request/result 和 final policy transition，并删除旧 reserve/topK 决策。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

## 2026-07-15 现行 citation/final-reason 勘误（REOPENED 时优先）

S09 records 中的 repair/NLI 演进只作为历史读取。现行 Source capability identity 只用于 final-policy 去重、Prompt reference、`explicitCitationEligibleIdentities`、explicit binding 和 final citation；Context/wrapper 不进入 eligibility、`CITATION_BINDING` 或 final source snapshot。S11 后续增加的 `INCLUDED_BY_STRUCTURE_NAVIGATION_REQUIREMENT` 是两个合法 selected reason 之一；它与 `INCLUDED_BY_FINAL_EVIDENCE_POLICY` 都只能由唯一 `FINAL_EVIDENCE_POLICY` 产生，Structure Navigation 只生产 typed required provenance，不拥有第二次选择权威。

## Q-ROUTE-03：高 rerank 正确证据被淘汰

问题：`NovaRAG 检索服务降级时，按什么顺序处理？`

冻结数据：

- exchange：`2313953969052923334`
- 正确 raw chunk：`2313953762894489446`
- 正确内容：依次启用 FAQ 缓存、关闭高成本重排序、缩小检索范围、必要时人工坐席接管
- 配置：`candidateTopK=40`、`rerankCandidateTopK=24`、`reserveCandidateTopK=30`、`finalTopK=6`
- 计数：32 条 channel observation、24 个 fusion candidate、18 个 parent/rerank candidate、6 个 final reference

### 不能合并的两个候选身份

| 来源 | candidate identity | rank | hybrid/rerank | 证据资格 | 旧结果 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| vector + keyword | `PARENT:2313953762894489234:2313953762894489445` | vector=2，keyword=7 | hybrid=`2.192054`，rerank=`0.999836` | `context_only=1`，`source_evidence_resolved=0`，无 citation identity | `FILTERED_BY_FINAL_TOP_K` |
| RAPTOR | `RAPTOR_SOURCE:2313953762894493207:2313953762894489446` | RAPTOR=5 | hybrid=`1.054812`，rerank=`NULL` | `context_only=0`，`source_evidence_resolved=1`，可引用 | `FILTERED_BY_RERANK_CANDIDATE_TOP_K` |

两行共享 raw `chunk_id=2313953762894489446`，但不是同一个候选。`0.999836` 属于 Parent Context，不属于 RAPTOR Source。修复不能把 context-only Parent 直接塞进 Prompt，不能把两个候选合成一个身份，也不能把 Parent 分数投影给 Source；真实 Source 必须以自身 identity 和文本直接参加 Source-only rerank。

### 已确认的失败链路

~~~text
route scope 正确
-> vector/keyword/RAPTOR 都命中正确 raw chunk
-> fusion 把 hybrid score 写入 metadata[SCORE]
-> parent elevation 按 SCORE 选择 child metadata 并折叠身份
-> RAPTOR_SOURCE citation identity 没有合并到高分 Parent
-> reranker 把 Parent 排到第一，写入 RERANK_SCORE=0.999836
-> allocator 初始 finalTopK 包含该 Parent
-> structure body reserve 把它识别为 TITLE 并优先替换
-> 后续 reserve 继续改写集合，弱项比较仍读取旧 SCORE
-> 正确 Source Evidence 9446 未进入最终 6 条 reference
-> Prompt 只渲染错误 reference 1-5，模型看不到四步原文
~~~

直接触发已经由代码顺序和数据库交叉确认：

- 正确 Parent 文本长度为 80，以 `# 1.` 开头，并继承 `chunk_type=TITLE`；原始结构产物还把整段四步列表写入 `section_path`。
- `EvidenceBudgetAllocator.isTitleEvidence()` 优先相信 `TITLE/HEADING` metadata，另有“短文本且以 `#` 开头”的回退判断。
- `reserveStructureAnchorBodyCandidates()` 是初始 topK 后执行的第一个 reserve，先从 index 0 查找 title/summary 槽位，不比较 rerank 分数。
- 本轮 Parent 是 rerank rank 1；structure anchor 只在 rerank 后追加，queryType 是 `DOCUMENT_QA`，没有 structure navigation 前插，18 个候选也没有触发 reserve window 截断。
- 最终 reference 1、2 都写入 `REPLACED_TITLE_ONLY_WITH_BODY`。旧 O9 没保存 replacement-from/to identity，但结合确定的列表顺序，可确认正确 Parent 首先走了 title-first structure body replacement。

### 三类根因

1. 构建语义错误：完整有序列表被构建为 `TITLE`，使本来合理的“标题换正文”规则反向淘汰完整答案。
2. 身份折叠错误：`DocumentKnowledgeServiceImpl.elevateToParentBlocks()` 按 parent 分组，`buildParentEvidenceDocument()` 按旧 `SCORE` 复制 best child metadata，只专门合并 GraphRAG metadata，没有合并 RAPTOR Source citation identity。高相关性留在 Context Parent，可引用身份留在被折叠的 RAPTOR Source。
3. 双选择权威：`RagRerankService` 只写 `RERANK_SCORE/RERANK_RANK`；allocator 初选使用 rerank 列表顺序，后续类型 reserve 可以直接覆盖，`weakestReplaceableEvidenceIndex()` 使用的 `finalDocumentScore()` 又只读取旧 `metadata[SCORE]` 或 `Document.score`。

因此“rerank 分数算对但最终未被尊重”成立，但本例不能简化为“另一个分数较低所以落选”。这一次最先发生的是 TITLE 类型优先替换；即使修掉 TITLE，旧 SCORE 与 rerank 的双权威仍会在其他 reserve 场景制造错误。

### 最终证据与观测边界

最终 6 条 reference 依次是：

| final rank | identity/来源 | reason | 正确四步 |
| ---: | :--- | :--- | :--- |
| 1 | `CHUNK:...9234:...9398`，structure-anchor，修订记录 | `REPLACED_TITLE_ONLY_WITH_BODY` | 否 |
| 2 | `CHUNK:...5669:...5837`，structure-anchor，高风险变更 | `REPLACED_TITLE_ONLY_WITH_BODY` | 否 |
| 3 | `RAPTOR_SOURCE:...3204:...9404`，定级补充 | `SELECTED_TOP_RANK` | 否 |
| 4 | `CHUNK:...9234:...9387`，文档头 | `SELECTED_STRUCTURE_ANCHOR_BODY` | 否 |
| 5 | `KG_QUOTE:...3403:CHUNK:...2360`，跨文档弱关系 | `SELECTED_GRAPH_RAG_QUOTE` | 否 |
| 6 | `KG_QUOTE:...6155:CHUNK:...5837`，高风险变更 | `SELECTED_TOP_RANK` | 否 |

Prompt 因字符预算只渲染 final rank 1-5；正确 `9446` 在 final reference 中已不存在，故失败早于 Prompt budget。

`FILTERED_BY_RERANK_CANDIDATE_TOP_K` 不能按字面解释为真实排在第 25 名以后。本轮 rerank 输入只有 18 个，未发生真实 topK 截断；O9 从 raw candidate 回配 parent/rerank identity，parent elevation 折叠身份后匹配不到，也会由 `resolveFilteredReason()` 推断成该 reason。

### 测试缺口和后续约束

- `RagRerankServiceTest` 只验证 rerank 返回顺序和 metadata 写入，没有继续进入 allocator。
- `EvidenceBudgetAllocatorTest` 不构造 `RERANK_SCORE/RERANK_RANK`，并明确固化了低分 body 可以替换高分 TITLE 的行为。
- engine 的 reserve/bypass 测试没有构造 rerank 与旧 `SCORE` 反序，也没有覆盖同一 raw chunk 的 Parent Context 与 RAPTOR Source 两种 identity。
- `finalEvidencePolicyKeepsSameSectionBodyAfterRerank` 实际关闭 rerank，不能证明 rerank 后行为。

阶段归属：S03 解决 Source/Context 与下钻身份；S08-S09 解决单一选择权威，S09 生产完整 identity lineage、producer-owned Source ranking-window decision、direct Source score provenance 和唯一 reason owner；S10-S11 解决列表误建 `TITLE`；S16 对 S09 已生产的 window/lineage/decision 做最终守恒，并核对旧 reason、transition type、snapshot key 和生产符号全部退出；S17-S18 使用新产物、新 exchange 完成复合问题终验。

S09 只关闭 Q-ROUTE-03 的 identity/rerank/final-selection 切片，阶段退出必须同时满足：

- Source/Context 在受限 rerank window 前物理分池；Parent Context 与真实 Source identity 始终独立，不能只有 Context Parent 获得有效高分。
- 真实 Source 以自身 candidate identity 直接参加 Source-only rerank；禁止 Parent/Context score projection，最终 decision 的 score provenance 必须指向 decision 自身 candidate 的实际 score producer。
- 正确四步原文进入 `BUDGET_SELECTED_SOURCE` 和 Prompt。
- 高置信直接答案不被低相关 structure/Graph/diversity 背景覆盖。
- O9 在固定 `EVIDENCE_BUDGET` stage 对账 Source partition、逐候选 ranking-window disposition、Source-only rerank 输入、完整 identity lineage、direct score provenance 和唯一 policy reason；新 cohort 只允许冻结的新 reason/transition 枚举，snapshot 不含旧 reserve/bypass key，静态扫描确认旧 producer/consumer 为 0。

Q-ROUTE-03 的跨阶段最终关闭不属于 S09 的 `DONE` 条件：S10-S11 修复四步列表误建 `TITLE` 和整段列表写入 `section_path`，S17 使用新 task 重建并验收新产物，S18 使用新 exchange 同时验证 parser、identity、rerank、final selection、Prompt 和 citation 全部条件。S09 可以使用现有产物证明新链不再因 `TITLE` reserve/replacement 淘汰高相关 Source；不得越界修改 parser，也不得因旧产物仍是 `TITLE` 而阻止 S09 退出。

禁止用“只扩大 topK”“直接引用 context-only Parent”“只改一个 score getter”“回答碰巧正确”或旧 exchange 宣布修复。

## O8-RD-FINAL-01 / O8-RD-FINAL-02：合法正文被 finalTopK 淘汰

必读：[S03 新代码真实 cohort 与阶段退出](../records/S03.md#2026-07-12-新代码真实-cohort-与阶段退出)。冻结的新代码 exchange 为原因 `2326901233866022913`、现象 `2326901233866022961`、处理步骤 `2326901233866023009`；它们只用于回溯证据，S09 的新验收必须使用新 exchange。

- `DATA_CONFIRMED`：现象 raw Source 旧 chunk `2313953728534752598` 已完成 source identity 解析，rerank score=`0.060471`，但在 O8-02 被 `FILTERED_BY_FINAL_TOP_K`。
- `DATA_CONFIRMED`：正确 `14.1.3` raw Source 旧 chunk `2313953728534752605` 的 rerank score=`0.549843`，但在 O8-03 被过滤；并列场景 `14.3.1` 的旧 chunk `2313953728534752618` 反而入选并支持了错误回答。
- S03 已证明三轮均为 `context_selected=0 / invalid_selected=0`，Source 保留 source-authored quote 和稳定 citation identity；Prompt rendered/reused Source identities 等于 `explicitCitationEligibleIdentities`，Context 不进入 binding/final snapshot。S09 不得重开或放松这些边界，也不得让 Context、query echo 或 wrapper 获得引用资格。
- 原因正文旧 chunk `2313953728534752602` 的 `TITLE` 产物归 S10-S11；S09 不修改 parser、chunk type 或 structure projection。

本阶段必须先用 characterization/O9 固化当前事实顺序：现有实现是在 capped rerank 后才执行 Source/Context partition；不得把“Source-only rerank”冒充 S03 已完成事实。目标架构强制把 Source/Context 物理分池前移到唯一受限 rerank window 之前；事实核验只决定迁移位置、lineage 保存和 fail-closed 降级方式，不决定是否迁移。随后增加不含验收词、业务章节号和旧 ID 的端到端 fixture，覆盖 mixed upstream、fusion/rerank identity transition、物理分池、S08 Source-only policy、policy decision/O9 纯投影和 Prompt：

- 高适用 raw Source、文本相似但场景错误的并列 Source、source-bound 派生 Source、标题/Context wrapper 可以出现在上游 mixed 输入，但进入 final evidence policy 的只能是 Source candidate pool。Source pool 中能力不足候选由 policy fail closed；Context 不进入 policy input/decision。
- Context 只获得独立 background/disposition，不获得 final evidence filtered reason；错误并列 Source 获得唯一可解释的 Source filtered reason。
- 唯一受限的 evidence rerank input window 只消费 Source pool。Context 如需评分，只能由独立 background/disposition 流程处理，不构成第二个 evidence rerank window，也不能占用 Source 配额。
- 增加通用目标 fixture：上游 mixed pool 同时包含高分 Parent Context 和同一经过验证的 raw/source lineage 下的可引用 Source。fixture 必须证明两者 identity 物理独立、Context 不进入 rerank window、Source 以自身 identity/text 获得 `RERANK_SCORE`；禁止 score projection，禁止依据文本、标题、`contains` 或业务词把两个候选合成一个身份。
- 正确 raw Source 最终进入 Prompt，并保持 S03 的 `context_selected=0 / invalid_selected=0`、source-authored quote、citation capability 和 eligibility 不变量。

自动工作完成后，按人工边界记录 exchange watermark，由用户在新会话按 O8-01 -> O8-02 -> O8-03 原顺序产生真实 exchange，AI 只读验收：

- O8-02 的现象原文进入 `BUDGET_SELECTED_SOURCE` 和 Prompt，并支持最终回答。
- O8-03 的 `14.1.3` 正确步骤进入 Prompt/final citation，`14.3.1` 不得替代。
- 三轮继续满足 `context_selected=0 / invalid_selected=0`，且 rendered/reused Source、`explicitCitationEligibleIdentities`、explicit binding 和 final source snapshot 按现行关系守恒。

新 task 的候选必须按 source span、section path 和内容语义定位，旧 chunk ID 只能用于对照历史失败，不能进入运行时规则或新产物验收条件。


唯一目标：fusion 只融合，rerank 只有一个 Source evidence 窗口，`FinalEvidenceSelectionPolicy` 是唯一 final evidence 语义 owner，并关闭 Q-ROUTE-03 的 identity/rerank/final-selection 切片。

前置阶段：S08。


必读：本文件的“Q-ROUTE-03：高 rerank 正确证据被淘汰”。运行时修复必须同时验证 Source identity、统一 rerank/final policy 和 filtered reason，不能只调大 topK 或改一个 score getter。

## S08 冻结切流契约

本节是 S09 唯一施工入口的一部分，不依赖下一会话回读已完成的 S08 阶段正文：

- policy 调用契约固定为 `select(List<Document> sourceCandidates, RetrievalPlan plan)`。输入只能是 Source candidate pool；Context 必须在调用前物理分池。能力不足或 citation identity 未解析的 Source 仍可作为输入，由 policy fail closed 为 `FILTERED_NOT_CITATION_CAPABLE`。
- S08 numeric precedence 保持为同一 Source candidate 内的 `RERANK_SCORE -> HYBRID_SCORE -> metadata SCORE -> Document.score -> 0`，不读取其他 identity 的分数；命中较高层非有限值时归零，不回退到较低层分数。S09 对该 contract 做受控状态约束：`RERANK_STATUS=SUCCESS` 时必须存在 `RERANK_SCORE`；`RERANK_STATUS=FAILED` 时禁止消费残留 `RERANK_SCORE`，从 `HYBRID_SCORE` 开始降级；未请求 rerank 时同样从 `HYBRID_SCORE` 开始。该约束不引入新 score source 或第二 precedence。
- 确定性选择序固定为：能力与 applicability 过滤；按 citation identity 去重，同 identity 取 `policyScore` 更高者、完全同分取较小 input index；随后按 policy score 使用 final budget，`abs(scoreDelta) <= 1e-9` 才进入同分桶，同分桶内依次按当前 document 已选数量和 input index 升序选择。
- `policyScore` 只能由 `EvidenceQualityFeatures.selectionPriority(relevanceScore)` 计算，冻结语义为 `relevanceScore + genericQualityScore * 0.25`。relation name、channel、route、n-hop、mapping status、reserve marker 和 O9 reason 不得进入 policy。
- `SelectionResult.decisions` 与 policy 输入按 index 一一对应，包含 candidate index/id、citation identity、唯一 owner、disposition/reason、final rank、relevance/score source、policy score 和 applicability。Context 不出现在该数组。
- Source 相关性只有一条合法路径：policy 解析该 Source candidate 自身的 direct score。Parent/Context score projection、跨 identity relevance 对象、projected metadata key 和第二个 score resolver 均禁止；若真实 Source 没有进入 Source-only rerank，必须修复窗口/lineage producer 或 fail closed，不能增加投影 adapter。S09 的 rerank status gate 和类型化 provenance 是对 S08 contract 的受控硬化，不扩展或重写 numeric score authority。
- `is_selected`、`BUDGET_SELECTED_SOURCE`、`FINAL_SELECTION_REASON` 和 O9 decision 只能是 `SelectionResult` 的纯投影。持久化或观测层可以写存储字段，但不得重新选择、补 reason、改 rank 或解释质量。

## 必须删除的旧运行链

生产代码和配置必须一次删除，不允许保留只读 compatibility snapshot、pass-through allocator 或 runtime shadow：

- 删除 `HybridFusionService` 的 Graph reserve、route document 追加和 `FINAL_SELECTION_RESERVE_TYPE` 写入。
- 删除 `RagRetrievalEngine` 的 `limitReserveCandidates`、`appendReserveWindowBypassCandidates`、structure/route bypass helpers、`appendRerankWindowBypassCandidates`、事后 `markEvidenceApplicability`，以及 `resolveFilteredReason` 对候选消失位置的推断。
- 删除 `EvidenceBudgetAllocator`；不得保留同名 facade、wrapper 或仅转调 `FinalEvidenceSelectionPolicy` 的兼容类。
- 删除 `DocumentKnowledgeServiceImpl` 的 `STRUCTURE_ANCHOR_BYPASS_RESERVE_WINDOW` 和 `STRUCTURE_ANCHOR_BODY_CANDIDATE` final reserve producer。
- 删除完整配置链：`ChatRagProperties.reserveCandidateTopK`、`RagRuntimeOptions.reserveCandidateTopK`、`RetrievalPlan.postFusionReserveWindow`、`RetrievalPlanAssembler` 投影和 `KnowledgeBaseRuntimeConfigResolver` 对应字段。
- 删除 metadata key `FINAL_SELECTION_RESERVE_TYPE`、`STRUCTURE_ANCHOR_BYPASS_RESERVE_WINDOW` 及全部 producer/consumer。`FINAL_SELECTION_REASON` 只保留为 policy decision 的纯投影。

必须删除或改写的旧测试方法：

- `RagRetrievalEngineTest.finalEvidenceBudgetReservesQueryPlannedGraphRagRelationEvidence`
- `RagRetrievalEngineTest.structureAnchorEvidenceBypassesReserveWindowWithoutSelectingTitleContext`
- `RagRetrievalEngineTest.finalEvidenceMarksExcludedOnlyEvidenceAsNotApplicable`
- `RagRetrievalEngineTest.finalEvidenceBudgetCanReserveGraphRagEvidenceAfterRerankWindow`
- `RagRetrievalEngineTest.finalEvidenceBudgetReservesCrossDocumentCommunityEvidenceForCommunityReportQuestion`
- `RagRetrievalEngineTest.hybridCandidateBudgetReservesCrossDocumentCommunityEvidenceForCommunityReportQuestion`
- `RagRetrievalEngineTest.autoDocumentRouteCandidateSourceEvidenceBypassesCandidateAndRerankWindows`
- `EvidenceBudgetAllocatorTest.structureAnchorBodyCandidateIsProtectedAfterTitleContextIsFiltered`
- `EvidenceBudgetAllocatorTest.descendantStructureBodyCandidateIsProtectedWhenParentAnchorMatches`

## S09 O9 增量契约

S09 必须在固定 `EVIDENCE_BUDGET` stage 生产稳定字段，禁止创建临时或“等价” stage；S16 只做最终守恒，不负责补生产投影：

`CODE_CONFIRMED` 聚合粒度：`RetrievalExecutionRequest.compile()` 为每个 execution query 生成独立请求，`RagRetrievalEngine.retrieveSingleSubQuestionAsync()` 在每个请求内分别完成 channel、fusion、rerank、partition 和 final selection；全部 `SubQuestionEvidence` join 后，`RagChatExecutor` 才统一组装 Prompt，并为整个 exchange 写一条 `EVIDENCE_BUDGET`。S09 保留这一稳定边界：rerank/final policy 继续按 sub-question 独立执行，单条 exchange 级 snapshot 使用 `subQuestions[]` 分组承载账本。不得把 per-sub-question ID 提升成虚假的 exchange 全局 ID，也不得在 exchange 顶层放置无法区分归属的 candidate/rerank/transition/decision 数组。

本阶段冻结四种不能混用的身份：

- `candidateId` 是一个物理运行时候选始终存在的身份，在候选 producer 创建候选时分配，最迟必须在该候选进入下一个 fusion、parent/context transition、Source/Context partition、rerank 或 policy 边界前存在；在 `(exchangeId, subQuestionIndex)` 内唯一且在该物理候选生命周期内稳定。它不得由列表 index、rerank 排名或 citation capability 临时推导；producer 产生新的物理候选时必须分配新的 `candidateId`，并在发生 identity/基数变化时用 transition 连接前后候选。
- `lineageIdentity` 是每个候选始终存在、仅用于 lineage/O9 对账的非能力身份，不授予 citation capability。`identityResolutionStatus` 是封闭枚举：citation-capable Source 使用 `CITATION_RESOLVED` 且 `lineageIdentity=CITATION:<citationIdentity>`，Context 使用 `CONTEXT_RESOLVED` 且 `lineageIdentity=CONTEXT:<contextIdentity>`，尚无合法 citation/context identity 的候选使用 `CANDIDATE_FALLBACK` 且 `lineageIdentity=CANDIDATE:<candidateId>`；三种值都不能留空，fallback 不能在同一 sub-question block 内复用其他未解析候选的值。
- `citationIdentity` 是可空、承载引用能力的 Source identity；只有已通过 S03 capability 边界的非空值才能用于 policy citation 去重、Prompt reference、`explicitCitationEligibleIdentities`、explicit binding 和 final citation。`lineageIdentity` 或 `CANDIDATE_FALLBACK` 不得替代它，也不得驱动 policy score、selection、citation dedupe、reason 或 `is_selected`。citation identity 未解析的 Source 仍可被观测和参加 lineage transition，但必须由 policy fail closed。
- `requestCandidateId` 只是在一次 rerank 协议请求内的 correlation ID，与稳定运行时 `candidateId` 一一映射；协议适配可以按外部接口要求生成它，但不得把它、请求顺序或结果 rank 回写成新的 `candidateId`。

每个 `subQuestions[]` block 对应且只对应一个 `RetrievalExecutionRequest`。`subQuestionIndex` 必须为正数，在 snapshot 内唯一并与 execution request index 集合完全相等；block 按 index 升序持久化，内部 candidate/transition/request ID 的唯一性、集合守恒和外键解析都只能在该 block 内判断。当前 `RagChatExecutor.selectedSourceIdentities(context).distinct()` 形成的 exchange 顶层 `BUDGET_SELECTED_SOURCE` 不能继续作为 O9 权威；权威集合是每个 block 的 `budgetSelectedSourceIdentities`，数据库 retrieval row 的 `BUDGET_SELECTED_SOURCE` 状态也必须带原 `sub_question_index` 对账。`evidenceLineage.rerankInputEvidenceIdentities` 只保留 evidence 语义维度，不能单独证明哪个物理候选进入模型请求；候选级证明必须来自该 block 内 Source/Context partition、ranking-window decision、实际 rerank request 和实际 result 四个 producer 边界分别产生的账本：

~~~text
schemaVersion
subQuestionCount
subQuestions[] = {subQuestionIndex, executionQuery, evidenceLineage, rerankExecution, sourceRankingWindowDecisions, sourceRankingWindowCandidates, rerankRequestCandidates, rerankResultCandidates, identityTransitions, finalEvidenceDecisions}
subQuestions[].evidenceLineage.contextDispositionCandidateIds
subQuestions[].evidenceLineage.sourcePartitionCandidateIds
subQuestions[].evidenceLineage.finalEvidencePolicyInputCandidateIds
subQuestions[].evidenceLineage.budgetSelectedSourceIdentities
subQuestions[].rerankExecution = {requested, attempted, status, reason, failureStage}
subQuestions[].sourceRankingWindowDecisions[] = {candidateId, inputRank, windowRank, cap, disposition, reason, orderingSource}
subQuestions[].sourceRankingWindowCandidates[] = {candidateId, lineageIdentity, citationIdentity, identityResolutionStatus, lane}
subQuestions[].rerankRequestCandidates[] = {requestCandidateId, candidateId}
subQuestions[].rerankResultCandidates[] = {requestCandidateId, candidateId, resultStatus, rerankScore, rerankRank}
subQuestions[].identityTransitions[] = {transitionId, fromCandidates, toCandidates, transitionType, stage, inputCardinality, outputCardinality}
subQuestions[].identityTransitions[].fromCandidates[] = {candidateId, lineageIdentity, identityResolutionStatus}
subQuestions[].identityTransitions[].toCandidates[] = {candidateId, lineageIdentity, identityResolutionStatus}
subQuestions[].finalEvidenceDecisions[] = {candidateIndex, candidateId, citationIdentity, disposition, owner, reason, finalRank, relevanceScore, scoreSource, scoreProvenance, policyScore, applicabilityStatus, applicabilityReason}
subQuestions[].finalEvidenceDecisions[].scoreProvenance = {kind, stage, sourceCandidateId, normalization, rerankStatus}
~~~

- Source/Context partition producer 必须按稳定 fusion Source 顺序写出 `sourcePartitionCandidateIds`；该集合与 `sourceRankingWindowDecisions[].candidateId` 按基数和 ID 完全相等，每个 Source partition candidate 恰有一条由唯一 ranking-window owner 当场产生的 decision。两个数组都按该顺序持久化；各自 `FOR ORDINALITY` 从 1 连续递增，decision 数组序号、按 candidate ID 关联到的 partition 数组序号和 `inputRank` 三者必须相等。不能由 O9、executor 或 SQL 根据后续集合差重建顺序或 reason。
- `CODE_CONFIRMED`：当前 `RetrievalPlan.validateForExecution()` 已拒绝 `rerankWindow <= 0` 或 `rerankWindow > candidateWindow`，`RetrievalPlanAssembler.assemble()` 在返回前调用该校验。S09 必须保留这一 assembly fail-fast 边界并增加 `rerankWindow=0`、负值 fixture；非法 cap 不得进入 window owner、不得被解释成“Source 非空但 included 为空”的运行状态。
- 唯一 ranking-window owner 固定使用 `RetrievalPlan.rerankWindow` 作为 Source cap，`orderingSource` 只能是 `FUSION_INPUT_ORDER`。它只是稳定 fusion Source 顺序的前缀截取器，不得重排、重算 score、打破同分、按 citation capability 过滤或进行第二次选择。cap 无论 `rerankRequested` 为 true 或 false 都要应用；`rerankRequested` 只控制是否调用 rerank transport，不能选择另一窗口或跳过 cap。所有 decision 的 `cap` 都必须等于所属 plan 的 `rerankWindow`。

正常 window build 的逐行真值表是双向约束，四列组合必须同时成立：

| `inputRank` 条件 | `disposition` | `reason` | `windowRank` |
| :--- | :--- | :--- | :--- |
| `inputRank <= cap` | `INCLUDED` | `INCLUDED_IN_SOURCE_RANKING_WINDOW` | `inputRank` |
| `inputRank > cap` | `FILTERED` | `FILTERED_BY_SOURCE_RANKING_WINDOW_CAP` | `null` |

- 上表每一行都使用“当且仅当”：正常 build 时 `includedCount = min(sourcePartitionCount, cap)`；不得过滤前 cap 内候选、纳入 cap 后候选再重排 `windowRank`，也不得出现 `FILTERED_BY_SOURCE_RANKING_WINDOW_BUILD_FAILURE`。正常 build 必须满足 `sourcePartitionCandidateIds = INCLUDED IDs union FILTERED IDs` 且两者不相交。
- `INCLUDED` decision candidate ID 集合必须与同 block 的 `sourceRankingWindowCandidates[].candidateId`、`finalEvidencePolicyInputCandidateIds` 和 `finalEvidenceDecisions[].candidateId` 按基数、顺序和集合完全相等。`sourceRankingWindowCandidates[].lane` 只能是 `SOURCE`；`candidateId`、`lineageIdentity`、`identityResolutionStatus` 不得为空，非 citation-capable Source 的 `citationIdentity` 允许为空。window owner 不得因 `citationIdentity` 为空提前淘汰 `inputRank <= cap` 的 Source；它必须进入 final policy，再由唯一 policy owner 写 `FILTERED_NOT_CITATION_CAPABLE`。`FILTERED` decision candidate ID 与 ranking candidate、rerank request/result、policy input 和 final decision 集合必须全部不相交。窗口淘汰 reason 与 final policy reason 是两个 owner 的封闭枚举；窗口 filtered candidate 不得被重标成 `FILTERED_BY_FINAL_EVIDENCE_BUDGET`。

异常和空输入真值表固定为：

| 场景 | window decisions | included/ranking/policy/final | `rerankExecution` |
| :--- | :--- | :--- | :--- |
| Source 非空、build failure、`rerankRequested=true` | 所有 Source 均为 `FILTERED/FILTERED_BY_SOURCE_RANKING_WINDOW_BUILD_FAILURE/windowRank=null` | 全部为空 | `FAILED_BEFORE_REQUEST/WINDOW_BUILD_FAILED/WINDOW_BUILD` |
| Source 非空、build failure、`rerankRequested=false` | 所有 Source 均为 `FILTERED/FILTERED_BY_SOURCE_RANKING_WINDOW_BUILD_FAILURE/windowRank=null` | 全部为空 | `NOT_REQUESTED/PLAN_DISABLED/NONE` |
| Source 为空、`rerankRequested=true` | partition 与 decisions 均为空 | 全部为空 | `SKIPPED_EMPTY_SOURCE_WINDOW/EMPTY_SOURCE_WINDOW/NONE` |
| Source 为空、`rerankRequested=false` | partition 与 decisions 均为空 | 全部为空 | `NOT_REQUESTED/PLAN_DISABLED/NONE` |

- build failure 的 decision 仍必须保存与 partition ordinality 一致的 `inputRank`、合法正 cap 和 `FUSION_INPUT_ORDER`；不得混入 `INCLUDED` 或 `FILTERED_BY_SOURCE_RANKING_WINDOW_CAP`。只有 Source partition 本身为空时，window decision 和后续集合才可全部为空。`contextDispositionCandidateIds` 由同一 sub-question partition 的 Context 输出边界产生，与该 block 的 Source partition、window decision、ranking candidate、rerank request/result、policy input/final decision candidate ID 交集必须全部为 0。
- `rerankRequestCandidates` 只在完整请求 payload 已原子交给 transport gateway、`attempted` 变为 `true` 时产生；在此之前的序列化、配置、client 初始化、circuit breaker 或校验失败不得留下半份 request ledger。`requestCandidateId` 和 `candidateId` 在 block 内都唯一并一一映射。`rerankResultCandidates` 必须在实际解析响应时产生，`(requestCandidateId, candidateId)` 必须原样引用同 block request ledger，不得从 `RERANK_SCORE` metadata、最终排序或 policy decision 反推。`resultStatus` 是封闭枚举 `FINITE`、`NON_FINITE_TO_ZERO`、`UNUSABLE`；`FINITE` 保存有限 `rerankScore`，`NON_FINITE_TO_ZERO` 保存 JSON 可表达的 `rerankScore=0` 并由 status 保留归零事实，两者是可消费结果；`UNUSABLE` 的 `rerankScore/rerankRank` 必须为空且不得进入 policy 的 `RERANK_SCORE` 分支。

`CODE_CONFIRMED`：当前 `src/main` 对 `ChatRagProperties.isRerankEnabled()` 的唯一直接业务调用在 `RagRetrievalEngine.applyRerank()`，`RagRuntimeOptions` 尚未承载该值；这正是待删除的第二决策点。目标白名单固定为：`ChatRagProperties.isRerankEnabled()` 只允许由 `RagRuntimeOptions.from(ChatRagProperties)` 在 runtime-options 归一化边界读取并保存为 `rerankEnabled`；`RagRuntimeOptions.isRerankEnabled()` 只允许由 `RetrievalPlanAssembler.assemble()` 读取一次并冻结为 `RetrievalPlan.rerankRequested`。normalized `rerankEnabled` 的默认值及 `RagRuntimeOptions.from(null)` 必须保持当前 `ChatRagProperties` 的默认语义 true，不能因 Java boolean/builder 默认值静默变成 false。`KnowledgeBaseRuntimeConfigResolver` / `KnowledgeBoundaryResolver` 可以调用该归一化边界或传递已经解析的 options，但不得另行解释 rerank intent。getter/field 定义不计作 consumer；`RagRetrievalEngine`、executor/orchestrator、`RagRerankService`、`FinalEvidenceSelectionPolicy`、O9/store 和其他下游对上述两个 ambient getter 的调用必须为 0，只能读取已建好的 plan 或其纯投影。

`rerankExecution` 是每个 sub-question 唯一的类型化 rerank transport 执行结果。`rerankExecution.requested` 必须是 `RetrievalPlan.rerankRequested` 的纯投影：`requested=true` 当且仅当计划要求 rerank，`requested=false` 当且仅当 `status=NOT_REQUESTED`。`attempted=true` 当且仅当完整 payload 已交给 transport gateway。状态判定顺序固定为 plan intent -> 正常 Source window 是否为空 -> pre-request 准备 -> transport attempt -> 完整 response 校验，先命中的终态唯一：

| `status` | `requested` | `attempted` | window / `finalEvidenceDecisions[].scoreProvenance.rerankStatus` | request/result ledger | `reason` / `failureStage` |
| :--- | :---: | :---: | :--- | :--- | :--- |
| `NOT_REQUESTED` | false | false | cap/window decision 严格服从上述正常、build-failure 或空 Source 真值表；included/policy/final 集合相等，所有现存 `finalEvidenceDecisions[].scoreProvenance.rerankStatus=NOT_REQUESTED` | 均为空 | `PLAN_DISABLED` / `NONE` |
| `SKIPPED_EMPTY_SOURCE_WINDOW` | true | false | Source partition、window decision、ranking/policy/final 集合都必须为空 | 均为空 | `EMPTY_SOURCE_WINDOW` / `NONE` |
| `FAILED_BEFORE_REQUEST` | true | false | `WINDOW_BUILD_FAILED` 时已有 Source 全部获得 window filtered decision 且 included/policy/final 为空；其他 pre-request 失败时 included/policy/final 集合相等，所有 `finalEvidenceDecisions[].scoreProvenance.rerankStatus=FAILED` | 均为空 | 受控失败 reason / `WINDOW_BUILD` 或 `PRE_REQUEST` |
| `FAILED_AFTER_REQUEST` | true | true | window decision 完整，included/policy/final 非空且集合相等，所有 `finalEvidenceDecisions[].scoreProvenance.rerankStatus=FAILED` | request candidate 集合等于完整 included window；result 为空或只含 `UNUSABLE` | 受控失败 reason / `TRANSPORT` 或 `RESPONSE_VALIDATION` |
| `SUCCESS` | true | true | window decision 完整，included/policy/final 非空且集合相等，所有 `finalEvidenceDecisions[].scoreProvenance.rerankStatus=SUCCESS` | request/result candidate 集合都等于完整 included window，每个 request ID 恰有一个可消费 result | `NONE` / `NONE` |

`reason` 是封闭枚举：正常终态只允许 `NONE`、`PLAN_DISABLED`、`EMPTY_SOURCE_WINDOW`；`FAILED_BEFORE_REQUEST` 只允许 `WINDOW_BUILD_FAILED`、`PROVIDER_UNAVAILABLE`、`CIRCUIT_OPEN`、`INVALID_CONFIGURATION`、`CLIENT_INITIALIZATION_FAILED`、`REQUEST_VALIDATION_FAILED`、`CANCELLED`；`FAILED_AFTER_REQUEST` 只允许 `TRANSPORT_ERROR`、`TIMEOUT`、`PROVIDER_ERROR`、`INVALID_RESPONSE`、`PARTIAL_RESULT`、`CANCELLED`。`failureStage` 只允许 `NONE`、`WINDOW_BUILD`、`PRE_REQUEST`、`TRANSPORT`、`RESPONSE_VALIDATION`。映射固定为：`WINDOW_BUILD_FAILED -> WINDOW_BUILD`；provider/circuit/config/client/validation 原因 `-> PRE_REQUEST`；transport/timeout/provider error `-> TRANSPORT`；invalid/partial response `-> RESPONSE_VALIDATION`；`CANCELLED` 在 request handoff 前只能是 `WINDOW_BUILD/PRE_REQUEST`，handoff 后只能是 `TRANSPORT`。正常三态的 failureStage 必须为 `NONE`。当 plan 禁用 rerank 但 window build 失败时，`rerankExecution` 仍为 `NOT_REQUESTED/PLAN_DISABLED`，失败事实由每个 Source 的 window decision 独立保存；当 plan 请求 rerank 时，同一失败映射为 `FAILED_BEFORE_REQUEST/WINDOW_BUILD_FAILED/WINDOW_BUILD`。部分返回即使含数值也必须整批标成 `UNUSABLE` 后 fail closed，或直接使阶段失败；只有 Source partition 本身为空才能使用 `SKIPPED_EMPTY_SOURCE_WINDOW`，不能把 build failure 或 cap 过滤后的空 included set 冒充空 Source，也不得利用空集等价标成 `SUCCESS`。O9 汇总层不得根据是否存在 `RERANK_SCORE` 推断状态、reason、attempted、window disposition 或补造 request/result 行。

正常无证据回答也必须先完成唯一 `EVIDENCE_BUDGET` snapshot，再返回 no-evidence reply，以保存 `SKIPPED_EMPTY_SOURCE_WINDOW` 或 `NOT_REQUESTED` block；不得像当前 `RagChatExecutor.context.isEmpty()` 分支一样在 stage 之前提前返回。若 task cancellation 直接终止整个 exchange、没有进入 fail-closed 回答路径，则只允许留下明确 failed/cancelled 的 retrieval trace，不属于“已完成回答 exchange”的 `EVIDENCE_BUDGET` 守恒集合；若取消后仍继续降级回答，则必须按实际 attempt 边界记录 `FAILED_BEFORE_REQUEST` 或 `FAILED_AFTER_REQUEST/CANCELLED`。
- 每个 block 内 selected decision 的 citation identity 集合等于该 block 的 `budgetSelectedSourceIdentities`；Context candidate/context identity 与同 block policy input/decision 的交集为 0。不同 block 可合法出现相同 citation identity，Prompt 层通过带 `subQuestionIndex` 的 manifest 保存输入归属，不得在 per-sub-question 守恒前先做 exchange 级 `distinct`。
- `identityTransitions` 是每个 sub-question 内 fusion dedupe、parent elevation 和 context expansion 的通用 lineage 账本，不是 score projection 数组。允许的 `transitionType` 仅为 `FUSION_DEDUPLICATION`、`PARENT_ELEVATION`、`CONTEXT_EXPANSION`，对应 `stage` 仅为 `FUSION`、`PARENT_ELEVATION`、`CONTEXT_EXPANSION`；新增类型必须先更新本契约，不得写任意字符串。
- transition 由实际 producer 在改变 identity 或基数时产生，`EVIDENCE_BUDGET` 只按 sub-question 聚合投影，不得事后猜测。`transitionId` 在所属 block 内唯一，即数据库语义键为 `(exchangeId, subQuestionIndex, transitionId)`；`inputCardinality/outputCardinality` 分别等于 `fromCandidates/toCandidates` 元素数，各端 endpoint 的 `candidateId` 和 `lineageIdentity` 非空，candidate ID 在数组内唯一，`identityResolutionStatus` 只能使用上述三项。已解析 lineage identity 允许在不同 candidate ID 间重复以真实表达 dedupe/elevation；`CANDIDATE_FALLBACK` 必须按 candidate ID 唯一，fusion 不得因为 citation identity 为空而把两个 fallback 候选去重。端点必须出现在同 block 对应 producer 输入/输出 lineage 中。只有该 sub-question 的所有 producer 都未改变 identity 和基数时数组才允许为空，是否 direct rerank 与数组能否为空无关。
- `scoreSource` 是封闭枚举 `RERANK_SCORE`、`HYBRID_SCORE`、`SCORE`、`DOCUMENT_SCORE`、`NONE`；`scoreProvenance.kind` 只允许 `DIRECT_CANDIDATE_SCORE`、`ZERO_FALLBACK`，`stage` 只允许 `RERANK`、`FUSION`、`RETRIEVAL`、`FINAL_EVIDENCE_POLICY`，`normalization` 只允许 `NONE`、`NON_FINITE_TO_ZERO`、`MISSING_TO_ZERO`，`rerankStatus` 只允许 `SUCCESS`、`FAILED`、`NOT_REQUESTED`。
- rerank orchestration 必须按所属 sub-question 的 execution/request/result ledger，在每个 policy input Source 上显式产生且只产生一个 decision `rerankStatus`：execution `SUCCESS` 映射 `SUCCESS`，`FAILED_BEFORE_REQUEST/FAILED_AFTER_REQUEST` 映射 `FAILED`，`NOT_REQUESTED` 映射 `NOT_REQUESTED`；`SKIPPED_EMPTY_SOURCE_WINDOW` 因 policy input 为空而没有 decision。缺失或未知状态是 schema violation，policy/O9 投影层不得把“缺 key”猜成 `NOT_REQUESTED`。
- 每条 decision 的 `scoreProvenance.sourceCandidateId` 必须等于 `decision.candidateId`。`RERANK_SCORE` 对应 `DIRECT_CANDIDATE_SCORE/RERANK/SUCCESS`；`HYBRID_SCORE` 与 `SCORE` 对应 `DIRECT_CANDIDATE_SCORE/FUSION`；`DOCUMENT_SCORE` 对应 `DIRECT_CANDIDATE_SCORE/RETRIEVAL`；`NONE` 对应 `ZERO_FALLBACK/FINAL_EVIDENCE_POLICY/MISSING_TO_ZERO`。rerank 失败时较低层 direct score 或 `NONE` 必须记录 `FAILED`，未请求时记录 `NOT_REQUESTED`；`SUCCESS` 不得与 `NONE` 或较低层 score source 组合。有限 direct score 使用 `normalization=NONE`；较高层非有限值保持 `DIRECT_CANDIDATE_SCORE` 和原 `scoreSource/stage`、写 `NON_FINITE_TO_ZERO` 并将 relevance 归零，不得回退。禁止 projected `scoreSource`，`scoreProvenance` schema 中也不得出现 `transitionId`。
- `RERANK_SCORE/SUCCESS` decision 必须在所属 block 按同一 candidate ID 找到唯一 request/result pair；result `FINITE` 对应 provenance `normalization=NONE`，result `NON_FINITE_TO_ZERO` 对应 provenance `normalization=NON_FINITE_TO_ZERO` 和 `relevanceScore=0`。`FAILED/NOT_REQUESTED` decision 不得引用可消费 result，也不得保留可被 policy 消费的 `RERANK_SCORE`。这些是同 sub-question request/result ledger 与 score provenance 的外键完整性，不由 O9 汇总层自行填充。
- `RagRetrievalEngine` 必须把各 producer 账本和 `SelectionResult` 原样封装进所属 `SubQuestionEvidence`；`RagChatExecutor`、store/mapper 和 stage snapshot 只按 `subQuestionIndex` 分组投影，不得 flatten、跨组去重、重算 execution state，或从候选在哪一层消失推断 owner/reason。

旧链退出必须使用存在性检查加具体路径/符号断言，不定义不存在的通用 `events[]`：

- `$.subQuestions[*].finalEvidenceDecisions[*].reason` 必须存在且只允许 `INCLUDED_BY_FINAL_EVIDENCE_POLICY`、`INCLUDED_BY_STRUCTURE_NAVIGATION_REQUIREMENT`、`FILTERED_NOT_CITATION_CAPABLE`、`FILTERED_NOT_APPLICABLE`、`FILTERED_DUPLICATE_SOURCE_IDENTITY`、`FILTERED_BY_FINAL_EVIDENCE_BUDGET`；两个 included reason 都只能由唯一 `FINAL_EVIDENCE_POLICY` 产生，后者还必须有合法 typed structure-required provenance，不得由 Structure Navigation 旁路选择。不是仅用 `RESERVE/BYPASS/REPLACED_*` 模糊匹配。
- `$.subQuestions[*].sourceRankingWindowDecisions[*].reason` 必须存在且只允许 `INCLUDED_IN_SOURCE_RANKING_WINDOW`、`FILTERED_BY_SOURCE_RANKING_WINDOW_CAP`、`FILTERED_BY_SOURCE_RANKING_WINDOW_BUILD_FAILURE`；window reason 与 final policy reason 必须按各自 JSON path 分开验证，不能互相代写或用集合差补造。
- `$.subQuestions[*].identityTransitions[*].transitionType` 必须属于上述三项 allowlist；replacement、reserve、bypass 和 score projection 均没有合法 transition type。
- `EVIDENCE_BUDGET.snapshot_json` 必须满足下列完整表达式；执行前先验证 snapshot、`subQuestionCount/subQuestions`、每个 block 的 `rerankExecution`、`sourcePartitionCandidateIds`、`sourceRankingWindowDecisions`、ranking/request/result 三个 candidate ledger、`finalEvidenceDecisions` 和 `identityTransitions` key 存在，并确认这些 ledger 不在 exchange 顶层重复出现，禁止缺 key 或错误层级产生 0 行 false green：

~~~sql
JSON_CONTAINS_PATH(
  snapshot_json,
  'one',
  '$**.finalSelectionReserveType',
  '$**.structureAnchorBypassReserveWindow',
  '$**.postFusionReserveWindow',
  '$**.reserveCandidateTopK'
) = 0
AND JSON_SEARCH(
  snapshot_json,
  'one',
  'STRUCTURE_ANCHOR_BODY_CANDIDATE'
) IS NULL
~~~

- `src/main` 精确扫描必须确认 `EvidenceBudgetAllocator`、`FINAL_SELECTION_RESERVE_TYPE`、`STRUCTURE_ANCHOR_BYPASS_RESERVE_WINDOW`、`STRUCTURE_ANCHOR_BODY_CANDIDATE`、`reserveCandidateTopK`、`postFusionReserveWindow`、`limitReserveCandidates`、`appendReserveWindowBypassCandidates`、`appendRerankWindowBypassCandidates`、`markEvidenceApplicability`、`resolveFilteredReason` 十一个符号的定义和引用均为 0。`STRUCTURE_ANCHOR_BODY_CANDIDATE` 当前是 `finalSelectionReserveType` 的旧值，不是独立 JSON key，因此用上述 `JSON_SEARCH` 做值扫描，不虚构第五个 forbidden path。

### Vector/keyword provider 拓扑复核

S05 因当前 `DocumentKnowledgeService` 的独立 vector/keyword gateway 暂时保留两个内部 provider；本阶段拥有 fusion/rerank 正式切流，必须结合当前源码、运行数据和 当前 vector/keyword 数据流决定是否收口为单一 hybrid retrieval 边界。不能因为两个类已经存在或测试易复用就默认长期保留。

无论最终收口为一个 hybrid provider，还是经事实核验后保留两个内部 provider，每个 execution query 都只能有一个 `RetrievalExecutionRequest` 和一个业务语义权威。内部 provider 只能投影/执行同一 request，禁止双 request、重复解释 query/scope/filter/route/channel/window/rank feature，禁止叠加 pass-through service、薄 wrapper 或多层 adapter。若保留两个 provider，必须记录 `CODE_CONFIRMED` / `DATA_CONFIRMED` 理由、稳定责任边界和长期保留或退出条件。

实施清单：

- [x] `HybridFusionService` 只做 weighted merge、dedupe、score parts 和统一 rank feature。
- [x] 为每个物理候选建立唯一、稳定、非 index 派生的 `candidateId`，唯一性范围固定为所属 `(exchangeId, subQuestionIndex)`；按本文件冻结 `lineageIdentity/citationIdentity/identityResolutionStatus/requestCandidateId` 的职责，删除 `RagRerankService`、policy、normalizer 等边界各自临时生成不兼容 candidate ID 的路径。
- [x] 将 Source/Context 物理分池前移到唯一受限 evidence rerank input window 之前，该窗口只消费 Source pool；Context background/disposition 不构成第二个 evidence rerank window，禁止 candidate/rerank/final 各层绕窗。partition producer 按稳定 fusion Source 顺序记录完整 `sourcePartitionCandidateIds` 和 Context disposition candidate；唯一 ranking-window owner 固定使用 assembly 已校验为正数的 `RetrievalPlan.rerankWindow`，无论 rerank 是否请求都严格按本文件真值表截取前缀并为每个 Source 当场写 decision。不得调包重排或按 citation capability 提前过滤；cap 淘汰、build failure 和 final policy filtered reason 必须由各自 owner 分开产生。
- [x] 建立 Parent Context 高相关性与可引用 Source 物理独立的结果契约：Context 不进入 rerank window，Source 必须以自身 identity/text 直接 rerank；禁止 projected score 和跨 identity score resolver，分别在每个 sub-question 的真实 ranking/request/result 边界保存候选级账本，并保存通用 `identityTransitions` 与 direct decision `scoreProvenance`。
- [x] 在 `RagRuntimeOptions.from(ChatRagProperties)` 唯一读取并归一化 `ChatRagProperties.isRerankEnabled()`，由 `RetrievalPlanAssembler` 唯一读取 normalized option 并写入权威 `RetrievalPlan.rerankRequested`；rerank orchestration 删除 ambient config/runtime option 二次决策。增加 `RerankIntentBoundaryTest` 和精确 consumer scan 固化两段白名单，并增加本文件冻结的类型化 execution result；`requested` 只投影 plan 字段，原子 request handoff 决定 `attempted`，五种 execution status、reason、failureStage 和 ledger 基数必须按状态表一次产生。
- [x] 删除 `postFusionReserveWindow` 及 `reserveCandidateTopK` 完整配置链，不保留只读快照或第二窗口字段。
- [x] 删除 `EvidenceBudgetAllocator`，将 `FinalEvidenceSelectionPolicy` 接入唯一生产路径；`is_selected/BUDGET_SELECTED_SOURCE/FINAL_SELECTION_REASON` 只按 policy result 投影。
- [x] 删除 engine/fusion/structure 中全部来源 reserve、替换、bypass、事后 applicability 和 filtered reason 推断，以及两个 reserve metadata key。
- [x] Context 只走独立 background/disposition；Context 不进入 policy decision、`is_selected`、`explicitCitationEligibleIdentities`、`CITATION_BINDING`、Prompt reference 或 final source snapshot。
- [x] 每个 `SubQuestionEvidence` 保存其 producer 已产生的完整 O9 block；固定 exchange 级 `EVIDENCE_BUDGET` stage 只按 `subQuestionIndex` 分组聚合为 `subQuestions[]`，并在顶层保存 `subQuestionCount`。不得 flatten 后丢失 scope，也不得在顶层复制 window/candidate/rerank/transition/decision ledger；为每个 Source partition candidate 保存唯一 window owner/disposition/reason，为每个 included policy input 保存唯一 final owner/reason、rank、direct score source/provenance、policy score 和 applicability，补 per-sub-question candidate ID 集合守恒测试。
- [x] 调整 no-evidence 路径：即使全部 Source window 为空，也先写入包含所有 sub-question execution 状态和空集合的唯一 `EVIDENCE_BUDGET` snapshot，再返回兜底回答。按 nested reason/transition allowlist、四个 forbidden JSON path、marker value 和十一个静态符号验证旧链为 0。
- [x] 删除或改写本文件列出的九个旧测试方法；allocator 类删除后同步删除 `EvidenceBudgetAllocatorTest`，不得为了保留测试入口留下 facade。
- [x] 完成 vector/keyword provider 拓扑复核，明确收口为 hybrid retrieval 或保留两个内部 provider，并把已确认理由、单一 request/语义权威边界和退出计划写入 `records/S09.md`。

开工 characterization 矩阵，只允许在修改生产代码前运行并记录旧链事实，不是退出门禁：

~~~bash
mvn -pl aegis-kb-business/aegis-kb-business-chat -am \
  -DfailIfNoTests=false \
  -Dtest=HybridFusionServiceTest,RagRetrievalEngineTest,EvidenceBudgetAllocatorTest,RankFeatureServiceTest,RagRerankServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

切流退出矩阵不得包含已删除的 allocator 测试：

~~~bash
mvn -pl aegis-kb-business/aegis-kb-business-chat -am \
  -DfailIfNoTests=false \
  -Dtest=FinalEvidenceSelectionPolicyTest,SourceRankingWindowTest,HybridFusionServiceTest,RagRetrievalEngineTest,RankFeatureServiceTest,RagRerankServiceTest,RagPromptAssemblyServiceTest,RagChatExecutorTest,RetrievalPlanAssemblerTest,KnowledgeBaseRuntimeConfigResolverTest,RerankIntentBoundaryTest,DocumentKnowledgeServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

S09 完成时实际执行的历史定向命令尚未包含 `SourceRankingWindowTest`，其结果必须保持记录为 `73/73`，不得因本次文档补强追溯改写。该类当时由 chat 全量 `279/279` 覆盖；上述命令是补强后的固定矩阵，后续真实补跑的命令、数量和退出码必须另行追加到 `records/S09.md`。

`RerankIntentBoundaryTest` 是必须存在的架构退出入口，并与精确 source consumer scan 共同断言：`ChatRagProperties.isRerankEnabled()` 的唯一调用者是 `RagRuntimeOptions.from(ChatRagProperties)`，normalized `RagRuntimeOptions.isRerankEnabled()` 的唯一调用者是 `RetrievalPlanAssembler.assemble()`；getter/field 定义排除后，`RagRetrievalEngine`、executor/orchestrator、rerank service、final policy 和 store/O9 的调用数均为 0。该门禁与十一个旧符号零扫描分开，不能把合法配置归一化 getter 全局禁掉。行为 fixture 必须证明：ambient config=true 但已建 plan=false 时不发送 request 且状态为 `NOT_REQUESTED`；plan=true 后再把 ambient config 改为 false 仍按冻结 plan 进入其余四态；反向修改 normalized options 或 properties 也不能覆盖已建 plan。

退出前必须另有 policy decision/O9 边界断言，至少覆盖：两个 sub-question 可使用相同 candidate ID 但在独立 block 内无冲突；`subQuestionCount/index` 与 execution request 集合相等；所有 window/candidate/request/result/transition/decision 外键和集合差都按 block 计算。正常 build fixture 必须逐行断言 `inputRank <= cap <=> INCLUDED <=> INCLUDED_IN_SOURCE_RANKING_WINDOW <=> windowRank=inputRank` 以及 `inputRank > cap <=> FILTERED <=> FILTERED_BY_SOURCE_RANKING_WINDOW_CAP <=> windowRank=null`，并断言 `includedCount=min(sourcePartitionCount,cap)`、build-failure reason 为 0；必须加入“过滤 rank 1、纳入 rank cap+1 后重排”的负例，证明只做集合/基数比较不能通过。build-failure fixture 必须让所有已有 Source 仅获得 build-failure filtered reason，不能混入 included/cap reason；空 Source fixture 必须分别覆盖 requested=true 的 `SKIPPED_EMPTY_SOURCE_WINDOW` 和 requested=false 的 `NOT_REQUESTED`。另覆盖 `rerankRequested=false` 仍应用 cap、`rerankWindow=0/-1` 在 assembly fail fast、`lane=SOURCE` 不与 window/final disposition 混用，以及 `citationIdentity=null` 且 `inputRank<=cap` 的 Source 仍进入 policy 并由 policy 写 `FILTERED_NOT_CITATION_CAPABLE`。五种 execution status 与 requested/attempted/reason/failureStage/request/result 基数必须一一对应，其中单独覆盖请求前失败、请求后整体失败、部分结果 fail closed、正常未请求和完整成功；所有 final decision 状态断言都使用 `finalEvidenceDecisions[].scoreProvenance.rerankStatus` 完整字段。no-evidence 回答仍有唯一完整 snapshot，O9 不从 score metadata 或集合差反推账本/reason。其余继续覆盖 selected citation identity、Parent/Source 物理独立、三类 identity 与 fallback、通用 lineage transition 基数、direct/degraded/non-finite/missing score provenance、投影层不推断 reason，以及 nested window/final reason 与 transition allowlist、四个 forbidden JSON path、marker value 和十一个静态符号；可落在 `FinalEvidenceSelectionPolicyTest`、`RagChatExecutorTest`、`RagRetrievalEngineTest`、`RagRerankServiceTest`、`RetrievalPlanAssemblerTest`、`RerankIntentBoundaryTest` 或职责更准确的新 boundary test 中。

运行时验收：新跑 Q-ROUTE-01 至 Q-ROUTE-07，重点记录 Q-ROUTE-03 的 identity/rerank/final-selection 切片。正确步骤原文必须进入 `BUDGET_SELECTED_SOURCE` 和 Prompt；不得只停留在 raw result 或 Context Parent 的 rerank 高分。S09 不重建文档，不以旧产物已经脱离 `TITLE` 或 `section_path` 已修正作为通过条件。

数据库断言：正确 chunk 对应新 exchange 的 `is_selected=1`；先验证每个适用 exchange 恰有一条 `EVIDENCE_BUDGET`、`subQuestionCount` 与 `subQuestions[]`/execution request index 集合相等、block key 完整，再用 nested `JSON_TABLE` 按 `(exchange_id, subQuestionIndex)` 展开账本。`sourcePartitionCandidateIds[*]` 和 `sourceRankingWindowDecisions[*]` 必须分别使用 `FOR ORDINALITY`，按 candidate ID 关联后断言两个数组序号与 `inputRank` 三者相等；不能只比较 ID 集合。正常 build 逐行验证 inputRank/cap 与 disposition/reason/windowRank 的双向真值表、`includedCount=min(partitionCount,cap)` 和 build-failure reason=0；build failure 验证所有行只使用 build-failure reason、windowRank 全空且下游全空；空 Source 按 requested 值验证 `SKIPPED_EMPTY_SOURCE_WINDOW` 或 `NOT_REQUESTED`。随后证明 included 等于 Source ranking window、policy input 和 final decision，filtered 与 request/result/policy/final decision 全不相交。Context disposition candidate 与 Source partition/window/ranking/request/result/policy 集合交集均为 0，request/result correlation 完整，execution 五状态与 requested/attempted/reason/failureStage 和数组基数一致；不得跨 block 对 candidate ID 做 `DISTINCT` 后再比较。每组 selected decision identity 与该组 `budgetSelectedSourceIdentities` 相等；Source decision 的 `sourceCandidateId` 等于同组自身 candidate ID，score source/provenance 枚举与实际 producer stage、rerank ledger、归零语义一致。最后分别断言 nested window reason、final decision reason 和 transition type 只属于各自 allowlist、四个 forbidden JSON path 不存在、`STRUCTURE_ANCHOR_BODY_CANDIDATE` value 不存在；不得查询不存在的 event 数组、错误顶层 path 或仅比较 evidence identity/集合差制造 0 行。

退出条件补充：`FinalEvidenceSelectionPolicy` 是唯一 `src/main` final evidence 语义 owner；旧 allocator、reserve/bypass、配置链、metadata key、structure body marker、九个旧测试和 reason 推断全部退出。每个 Source partition candidate 有唯一 producer-owned window decision；正常 build 的 decision 是 `inputRank/cap` 真值表的纯函数，window owner 不再拥有重排或第二选择自由度。每个 included policy input 有唯一 final decision，Context 只走独立 disposition；citation identity 可空 Source 不得在 window capability gate 被提前过滤，window cap filtered reason 也不得伪装成 final budget reason。稳定 candidate ID、Source partition/window decision、Source-only direct rerank 的 ranking/request/result 独立账本、五状态 execution result、通用 identity lineage、类型化 score provenance 和 S09 O9 增量字段完成生产投影；单条 exchange snapshot 只以 `subQuestions[]` 分组承载 per-sub-question 账本，并通过组内 ordinality、真值表、partition/included/filtered、candidate ID、外键和状态集合守恒，不能只用 evidence identity、自报 rerank status、集合差、空集等价或跨组 `distinct` 证明通过。rerank intent 的两段 consumer 白名单、ambient config 不可覆盖 plan 的行为测试和独立架构门禁全部通过；非正 cap 在 assembly fail fast。正常 no-evidence 回答也有完整 snapshot。projected score、跨 identity score resolver 和不存在的通用 event 契约均不存在。vector/keyword 拓扑已有基于当前源码和数据的明确结论；运行链路不存在双 request、重复 query/scope 解释或多层 adapter。若保留两个内部 provider，二者只执行同一 `RetrievalExecutionRequest`，fusion 仍是唯一合并与 score-parts owner，rerank/final policy 不因 provider 拆分产生第二权威。四步列表仍为旧 `TITLE` 或旧 `section_path` 尚未修正不阻止 S09 `DONE`，其 parser/build 修复和复合问题最终关闭严格归 S10-S11、S17-S18。

