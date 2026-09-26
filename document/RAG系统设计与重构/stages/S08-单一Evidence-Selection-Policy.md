# S08：单一 Evidence Selection Policy

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S08 的工程实施合同；具体执行结果、偏差和证据见 [records/S08.md](../records/S08.md)。

## 阶段设计依据

旧链路在 fusion、预算和最终选择中多次保留或淘汰 Source，正确候选进入 final-policy input 后仍可能因 coverage-first 被低分候选挤出。本阶段建立唯一 FinalEvidenceSelectionPolicy：统一 policyScore 为主权威，结构和文档多样性只作受控特征或同分 tie-break。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

## 2026-07-21 S18 终验后二次 REOPENED 与关闭（现行优先）

- `DATA_CONFIRMED`：S18 完整 48-case cohort 中仅 `Q-ROUTE-02 / 2353139017278603341` 形成当前项目的确定性 blocker。该轮 hard scope、Plan/request、Source window、rerank producer/result、Prompt/citation 和 O9 守恒均合法；正确直接 Source `2344517128129963233` 已进入唯一 final-policy input，随后被 `FILTERED_BY_FINAL_EVIDENCE_BUDGET` 淘汰。原 Q-ROUTE-07 目标 Source 的纯 `policyScore` 排名为 8，低于预算 6 的实际入选下限，普通相关性 top-N 同样无法保证选中，现行分类为 `OBSERVATION_ONLY`。
- `CODE_CONFIRMED`：Q-ROUTE-02 的正确直接 Source 对相关子问题 rerank relevance=`0.873878`、`policyScore=1.036378`，纯分数排名第 3。修复前冻结的 `FinalEvidenceSelectionPolicy.selectWithinBudget()` 在比较 `policyScore` 前先按 required-section、source-family 和 AUTO document coverage 选择候选，因此三个 top-6 外低分候选可仅凭 document coverage 先占有限预算。该行为是本项目确定性 transition，不是模型回答波动。
- 本轮不得把 `Q-ROUTE-05、Q-O2-02、Q-O8-07、Q-O8-08、Q-TABLE-04` 带入实现。它们的 scope、Plan/request、证据可见性、citation authority 和 O9 均未证明确定性违规，现行分类为 `OBSERVATION_ONLY`；确定性工程链路也不能保证 query understanding、多轮指代、表格问法解释和答案生成每轮完全正确。
- 本次 S08 实施按 改造计划与进度 完成 `REOPENED -> IN_PROGRESS -> HUMAN_ACTION_REQUIRED -> IN_PROGRESS -> DONE`。中性 fixture 固化“高相关合法直接 Source 不得仅因 AUTO document coverage 被低相关候选挤出”和“文档多样性仅在同分候选间生效”的目标不变量；实现只修改唯一 final policy 的 document diversity tie-break，不读取问题词、文档名、channel 名或答案，不改 scope、Plan、provider、Prompt、citation、MySQL/ES，也不承诺消除普通召回、reranker 模型或生成质量波动。
- `DATA_CONFIRMED`：用户重启后的唯一新 conversation/exchange=`mruowqchqhtoqxfh / 2353851157215977473` 严格高于事前水位 `2353139017278606057`。目标 Source identity=`CHUNK:2344517128129962851:2344517128129963233` 在相关子问题保持同一 rerank request/result candidate，rerank rank=`3`、score=`0.873216`，以 `policyScore=1.035716` 成为 final rank=`2`；Prompt manifest 将其渲染为 `[7]`，答案明确给出 NovaRAG 召回成功率下降 `15%` 的条件。`12/12` 合法 token 全部绑定、拒绝数为 `0`，explicit citation、binding snapshot、FINALIZE 与 archive identity 数组完全同序，`conservationStatus=CONSERVED`。两个子问题 observation persistence=`28/28、33/33`，全部 stage 成功且无 error/degradation；真实退出条件满足，不要求重跑 48 轮。

## 2026-07-21 真实 cohort reopen 勘误（现行优先）

- `DATA_CONFIRMED`：第一轮 `Q-ROUTE-06 / 2352556344835334376`、`Q-O8-02 / 2352556344835334692`、`Q-O8-11 / 2352556344835335126` 的正确 raw Source 均已在合法 RetrievalPlan scope 内召回并进入唯一 final policy，随后被 `FILTERED_BY_FINAL_EVIDENCE_BUDGET` 淘汰。第一次修复后的第二轮中，`Q-ROUTE-06 / 2352951378747359233` 和 `Q-O8-02 / 2352951378747359329` 已通过；`Q-O8-11 / 2352951378747359375` 的正确四层正文仍在两个子问题中被 budget 淘汰。S09 的 ranking window、rerank producer 和 O9 identity/顺序均守恒。
- `CODE_CONFIRMED`：第一次修复已解决 source-family 重复占槽和 AUTO/DOCUMENT document coverage 边界，但旧的单一 `structureSectionIdentity` 仍按 `structureNodeId -> canonicalPath -> sectionPath` 三选一。同一逻辑 section 的 required heading 可能只有 `sectionPath`，raw body 同时带 `structureNodeId + sectionPath`，Graph 派生 Source 又通常只有 `sectionPath`；三者因此无法稳定相认，metadata 更少的同标题派生 Source 反而先占剩余预算。
- 本轮目标不变量是：每个候选保留全部可用且带 document scope 的稳定 section identity（node、canonical path、section path），required heading 与正文只要 identity 集合有交集就属于同一预算覆盖单元。字段缺失只降低结构增强能力，不重解释 query、不扩大 RetrievalPlan scope，也不改变 citation identity。
- 现行确定性预算序经本次二次 reopen 勘误为：合法 typed required heading 先占既有预算；与 required-section identity 集合相交的正文优先于无关普通候选；尚未覆盖的原文 chunk source family 优先；随后按 `policyScore` 降序；仅 `AUTO_DOCUMENT` 在同一 `policyScore`（`SCORE_EPSILON` 内）候选中以 document coverage 作 tie-break；最后按 input index 稳定排序。Plan 的 allowed document scope 只是硬边界，不产生跨分数的 round-robin 执行权。
- source family 和 section identity 集合都只影响既有 budget 内的覆盖，不改变 citation identity；table row/cell identity 始终独立。显式 `DOCUMENT` 不启用跨文档均衡。实现不读取 query、业务词、文档名、route candidate 或 channel 名，不扩大 scope，也不新增 reserve/bypass authority。
- 本轮只修复上述已由真实数据和通用 fixture 证明的预算选择错误，不承诺消除召回、rerank 或生成模型的全部质量波动。前两个场景不受第二次补丁影响且未重跑；重启后的 `Q-O8-11 / 2353056588266250241` 已通过：两个子问题都选择四层正文，Prompt、答案、显式引用和 final/archive identity 完整守恒，S08 真实退出条件已满足。

## 2026-07-15 现行 citation/final-reason 勘误（REOPENED 时优先）

S08 records 中的 repair/NLI 演进只作为历史读取。现行链路要求 Context 不进入 `explicitCitationEligibleIdentities`、`CITATION_BINDING` 或 final source snapshot；Source capability identity 只用于 final-policy 去重、Prompt reference、explicit binding 和 final citation。S11 后续新增的 `INCLUDED_BY_STRUCTURE_NAVIGATION_REQUIREMENT` 是两个合法 selected reason 之一；它与 `INCLUDED_BY_FINAL_EVIDENCE_POLICY` 都只能由唯一 `FINAL_EVIDENCE_POLICY` 产生，Structure Navigation 只提供 typed required provenance，不能自行选择或旁路 policy。

唯一目标：定义并用测试证明一个能够解释所有 final evidence 去向的选择策略，不在生产链路保留双决策。

前置阶段：S07。

必读：[S09 的 Q-ROUTE-03 冻结诊断](S09-Fusion-Rerank-Budget正式切流.md)，尤其是两个 candidate identity、TITLE 直接替换、双分数权威和测试缺口；不得继续把 vector/keyword Parent 与 RAPTOR Source 合成一个候选。

核心文件：

- `HybridFusionService.java`
- `RankFeatureService.java`
- `EvidenceBudgetAllocator.java`
- `RagRetrievalEngine.java`
- `FinalEvidenceSelectionPolicy.java`
- `ContextExpansionPlanner.java`
- `HybridFusionServiceTest.java`
- `FinalEvidenceSelectionPolicyTest.java`
- `EvidenceBudgetAllocatorTest.java`
- `RankFeatureServiceTest.java`
- `RagRetrievalEngineTest.java`

策略只允许消费：citation capability、统一相关性/rerank score、预算、稳定 source-family/structure metadata、Plan 模式、必要文档覆盖和 S07 通用质量特征。

约束：

- 本阶段的对比只放在测试/fixture 中，不在生产运行时增加长期 shadow adapter。
- S08 的目标 policy 不表达 route、GraphRAG、RAPTOR、structure reserve；当前生产旧 reserve 只作为 characterization 保留，并必须在 S09 一次切流时删除。
- 跨文档覆盖只允许在 `AUTO_DOCUMENT` 的既有合法 scope 内参与最终预算；显式 `DOCUMENT` 禁用该维度，任何覆盖逻辑都不得扩大或替代 Plan scope。

## 2026-07-14 开工事实核验与文档勘误

### 已确认事实

- `CODE_CONFIRMED`：当前生产链为 `fusion reserve -> rerank/window bypass -> Source/Context partition -> allocator 多类 reserve -> engine 事后 applicability/filtered reason`。`HybridFusionService` 仍追加 Graph 和 route reserve，并写 `FINAL_SELECTION_RESERVE_TYPE`；`RagRetrievalEngine` 仍执行 rerank/reserve window bypass；`EvidenceBudgetAllocator` 仍按 structure、route、RAPTOR、Graph 多轮替换。
- `CODE_CONFIRMED`：旧 allocator 的最弱候选比较读取 metadata `SCORE`，没有以 `RERANK_SCORE` 为唯一相关性权威；已存在测试允许低 rerank structure body 覆盖高 rerank direct Source。
- `CODE_CONFIRMED`：applicability 当前在 final evidence 选出后才由 engine 评估；未选 Source 没有权威 decision，O9 通过候选在哪一层消失来推断 filtered reason。旧测试允许已选证据同时带 `FILTERED_NOT_APPLICABLE_TO_TARGET_ENTITY`。
- `CODE_CONFIRMED`：S07 已创建 `HybridFusionServiceTest`，因此原“当前不存在”的说明已过时，本阶段已勘误核心文件和验证矩阵。
- `DATA_CONFIRMED`：S07 新代码 cohort 中正确 direct Source 仍可能在 final budget 丢失，并列章节 Source 仍可能被选；该现象由 S08 定义解释契约、由 S09 执行运行时切流，S08 不用旧 exchange 宣称回答端已修复。

### 文档差异、证据不足项与实际修改边界

- 原实施清单要求本阶段直接“改写与目标相反的旧测试预期”，但当前源码事实表明这些测试仍刻画唯一生产运行链；在 S08 未切流时提前改写只会隐藏现状或形成运行时双决策。勘误为：S08 建立目标 contract test、明确标出旧测试及 S09 删除/改写清单，S09 切流时同步删除旧断言。
- `CODE_CONFIRMED`：本阶段新增的 `FinalEvidenceSelectionPolicy` 没有 `src/main` 调用点；它是 S09 待切入的目标深模块，不是 shadow runner、compatibility facade 或第二个运行时 owner。当前生产仍只执行旧 allocator。
- `HYPOTHESIS`：S09 切流后的真实召回窗口、rerank 降级和 Prompt/citation 结果尚未产生新 cohort，不能由 S08 单元测试替代；这些运行时结论必须留给 S09 新代码复测。
- 实际生产修改边界仅为目标 Source-only policy、Context 独立 disposition、Context 清除遗留 Source reason/reserve，以及 O9 对既有 metadata 的纯投影；不改 parser、channel、fusion、rerank、allocator、Prompt 或 citation。

### 固化旧错误的测试与新增 fixture

- `CODE_CONFIRMED`：`RagRetrievalEngineTest` 中 Graph/route/structure reserve、rerank/reserve window bypass 和“选中后标记 not applicable”断言，以及 `EvidenceBudgetAllocatorTest` 中 structure body protected 断言，均保护待 S09 删除的旧行为。
- 新增 legacy characterization：route document reserve 会越过 `candidateWindow`；旧 allocator 会让低 rerank structure reserve 覆盖高 rerank direct Source。二者只存在测试层，不进入新 policy。
- 新增目标 fixture：mixed pool 同时包含 direct raw Source、相似但不适用的并列 Source、source-bound 派生 Source 和 Context wrapper；分池后 Context 只获得 `BACKGROUND_AVAILABLE`，并清除 `FINAL_SELECTION_REASON` / `FINAL_SELECTION_RESERVE_TYPE`。
- 新增 Source-only contract fixture：每个输入候选恰有一个 policy-owned decision；覆盖能力不足 Source 的 fail-closed、applicability、citation identity 去重、统一 score precedence、budget、等分 document diversity、非有限 score 和 64 位 document ID 边界。Context 在调用 policy 前已经分池，不作为能力不足 Source fixture。fixture 不含验收文档名、业务章节号、旧 ID 或三问文本。

## 项目内数据流与候选方案审查

`DATA_CONFIRMED`：正确 Source 已经进入统一 final-policy input，说明召回、identity 和 rerank 并非该反例的最早 owner。`CODE_CONFIRMED`：旧 budget allocator 会先执行结构、source-family 和 AUTO document coverage，再在局部比较 score，因此低分候选可以挤出更高分直接 Source。

候选方案比较：

- 扩大 finalTopK 只能移动失败边界，不能消除多权威，拒绝。
- 为特定来源、文档或结构增加 reserve 会继续累积业务特例，拒绝。
- 只使用 rerank 模型单分数会丢失当前已经生产的 applicability、citation capability 和通用质量事实，拒绝。
- 在唯一 policy 中计算统一 `policyScore`，以其稳定降序作为主权威；结构、source family 和文档多样性只提供通用特征，AUTO document diversity 仅在同分候选间 tie-break。该方案被采用。

目标数据流：

```text
fusion Source candidates
-> stable identity and quality features
-> one ranking window
-> rerank result or explicit degradation
-> one policyScore
-> stable ordering
-> final budget
-> one selected/rejected decision per Source
```

Context 在进入 policy 前已经分池，只获得独立 background disposition。S08 可以先落目标深模块，S09 负责正式切流和删除旧 allocator；过渡期间不得生产双跑或影子决策。

## 跨阶段冻结反例责任

必读：[S03 新代码真实 cohort 与阶段退出](../records/S03.md#2026-07-12-新代码真实-cohort-与阶段退出)。本阶段直接拥有 `O8-RD-FINAL-01`、`O8-RD-FINAL-02` 的 policy 解释能力：

- `DATA_CONFIRMED`：现象 raw Source `...2598` 在 O8-02 被过滤；正确 `14.1.3` raw Source `...2605` 在 O8-03 被过滤，而并列场景 `14.3.1` 的 `...2618` 被选中并支持了错误回答。
- 上游必须先形成物理独立的 Source/Context pool；policy 只接收 Source candidate pool，允许能力不足的 Source 输入并在接口内 fail closed，用统一相关性、S07 通用质量特征和适用性解释 direct raw Source、source-bound 派生 Source 与并列章节 Source 的去向。Context 不进入 policy input/decision，policy 不能依赖来源 reserve、TITLE-first 替换或样例词。
- 新增两层通用 fixture：上游 mixed fixture 同时提供高适用 raw Source、低适用但文本相似的并列 Source、source-bound 派生 Source、标题/Context wrapper，断言分池后 Context 只获得独立 background/disposition；Source-only policy fixture 断言唯一 decision owner 选择高适用 raw Source，并只为未选 Source 给出唯一 filtered reason。
- fixture 中不得出现验收文档名、业务章节号、旧 chunk ID 或三问文本。旧 ID 可保存在历史记录、冻结诊断和路由指针中，但不得进入 fixture、生产规则、新 task 定位或新 cohort 通过条件。
- 本阶段不修改 parser 产物（S10-S11），也不通过扩大 topK、直接选 Context 或增加来源特权制造通过；实际 fusion/rerank/budget 切流归 S09。

针对这两个内部诊断的阶段退出证据是：Source-only policy 测试能解释 `raw direct Source > similar parallel-section Source`，Context 则在 policy 外获得独立 background/disposition；同时保持 S03 的 Source/Context capability、source-authored quote 和 reference ID 不变量。回答端最终通过留给 S09 运行时复测和 S18 终验。

## 目标输入输出契约

### Fusion output -> rerank input

- 输入身份：每个物理 Source candidate 保留稳定 citation identity；Parent/Context wrapper 与 RAPTOR/Graph/raw Source 不得因文本、标题或来源相似被合成一个候选。
- 目标 fusion output：只合并通道结果和计算通用 rank feature，输出候选及 `HYBRID_SCORE`；不得追加 route/Graph/structure/RAPTOR final reserve，也不得写 final selection reason。
- rerank input：完整受控候选窗口及其稳定 identity；窗口只能由 RetrievalPlan 定义一次，不允许来源绕窗。

### Rerank output -> final evidence policy input

- rerank 成功时，每个输入候选输出 `RERANK_SCORE` 并保持 identity；失败时保留 fusion 顺序和 `HYBRID_SCORE`，记录 degraded，不恢复词法或来源 reserve。
- final policy 只接收 Source candidate pool；Context 不进入该接口。Source pool 中能力不足或 identity 尚未解析的候选由 policy fail closed，并获得 `FILTERED_NOT_CITATION_CAPABLE`。调用契约为 `select(List<Document> sourceCandidates, RetrievalPlan plan)`。
- 统一相关性 precedence 固定为 `RERANK_SCORE -> HYBRID_SCORE -> metadata SCORE -> Document.score -> 0`；命中较高层字段后即使非有限也归零，不回退到旧分数制造第二权威。

### Final evidence policy output

- `SelectionResult.selectedDocuments` 按最终 rank 排序；`decisions` 与输入候选按 index 一一对应。
- 每个 decision 显式包含 candidate index/id、citation identity、唯一 owner、selected/filtered disposition、唯一 reason、final rank、相关性值及来源、policy score、applicability status/reason。
- 非 citation-capable、`NOT_APPLICABLE`、重复 citation identity 和 budget 淘汰各有互斥 reason；普通 selected 使用 `INCLUDED_BY_FINAL_EVIDENCE_POLICY`，只有携带合法 typed structure-required provenance 且由同一 policy 选中的候选使用 `INCLUDED_BY_STRUCTURE_NAVIGATION_REQUIREMENT`。selected 必须有正 final rank，filtered 不得有 final rank。
- S07 `EvidenceQualityFeatures.selectionPriority(...)` 是唯一通用质量解释入口；relation name、channel、route、n-hop、mapping status 和 reserve marker 不进入 policy。
- 确定性选择序固定为：先按上述能力与 applicability 过滤；再按 citation identity 去重，同 identity 取 `policyScore` 更高者、完全同分取较小 input index；typed required heading 先占既有 budget；剩余候选依次按 required-section match、已选 source-family 数、`policyScore` 降序选择；仅对 `AUTO_DOCUMENT` 且 `policyScore` 在 `SCORE_EPSILON` 内同分的候选，再按已选 document 数升序打破平局，最后按 input index 升序保持稳定。
- `policyScore` 只能由 `EvidenceQualityFeatures.selectionPriority(relevanceScore)` 计算，当前冻结语义为 `relevanceScore + genericQualityScore * 0.25`；不得在调用方、O9 或 allocator 再解释质量和相关性。source family 只把锚定同一原文 chunk 的 citation 变体视为同一覆盖单元，table row/cell 不合并；document ID 直接按 `long` / decimal string 处理，禁止经 `double` 解释 64 位 identity。

### Context output

- `ContextExpansionPlanner` 是 Context disposition owner：当前统一投影 `contextDisposition=BACKGROUND_AVAILABLE`，并清除继承的 Source final reason/reserve。
- O9 只读取 `contextArtifact/contextDisposition`；不得从 Source 是否入选反推 Context filtered reason，Context 仍不得进入 `explicitCitationEligibleIdentities`、`CITATION_BINDING`、Prompt reference 或 final source snapshot。

## S09 删除清单与切流断言

生产删除/替换面：

- `HybridFusionService`：删除 Graph reserve、route document 追加和 fusion 写 `FINAL_SELECTION_RESERVE_TYPE`。
- `RagRetrievalEngine`：删除 `limitReserveCandidates`、`appendReserveWindowBypassCandidates`、structure/route bypass helpers、`appendRerankWindowBypassCandidates`、事后 `markEvidenceApplicability`，并删除 `resolveFilteredReason` 对 final top-K 消失位置的推断。
- 删除 `EvidenceBudgetAllocator`，由 `FinalEvidenceSelectionPolicy` 成为唯一 final evidence owner；`FINAL_SELECTION_REASON` 只允许是 policy decision 的纯投影。
- `DocumentKnowledgeServiceImpl`：删除 `STRUCTURE_ANCHOR_BYPASS_RESERVE_WINDOW` 和 `STRUCTURE_ANCHOR_BODY_CANDIDATE` final reserve marker。
- 删除配置链 `ChatRagProperties.reserveCandidateTopK`、`RagRuntimeOptions.reserveCandidateTopK`、`RetrievalPlan.postFusionReserveWindow`、`RetrievalPlanAssembler` 对应投影和 `KnowledgeBaseRuntimeConfigResolver` 对应字段。
- 删除无消费者 metadata key `FINAL_SELECTION_RESERVE_TYPE`、`STRUCTURE_ANCHOR_BYPASS_RESERVE_WINDOW`。

旧测试删除/改写面：

- `RagRetrievalEngineTest.finalEvidenceBudgetReservesQueryPlannedGraphRagRelationEvidence`
- `RagRetrievalEngineTest.structureAnchorEvidenceBypassesReserveWindowWithoutSelectingTitleContext`
- `RagRetrievalEngineTest.finalEvidenceMarksExcludedOnlyEvidenceAsNotApplicable`
- `RagRetrievalEngineTest.finalEvidenceBudgetCanReserveGraphRagEvidenceAfterRerankWindow`
- `RagRetrievalEngineTest.finalEvidenceBudgetReservesCrossDocumentCommunityEvidenceForCommunityReportQuestion`
- `RagRetrievalEngineTest.hybridCandidateBudgetReservesCrossDocumentCommunityEvidenceForCommunityReportQuestion`
- `RagRetrievalEngineTest.autoDocumentRouteCandidateSourceEvidenceBypassesCandidateAndRerankWindows`
- `EvidenceBudgetAllocatorTest.structureAnchorBodyCandidateIsProtectedAfterTitleContextIsFiltered`
- `EvidenceBudgetAllocatorTest.descendantStructureBodyCandidateIsProtectedWhenParentAnchorMatches`

S09 切流必须断言：新 policy 是唯一 `src/main` final evidence owner；每个 Source input 恰有一个显式 decision；Context 只走独立 disposition；rerank score 高的适用 direct Source 不会被低分来源 reserve 替换；运行时不再出现 reserve/bypass 配置、metadata 或 O9 reason 推断。

实施清单：

- [x] 把 Q-ROUTE-03 和各类 reserve/bypass 现状固化为失败/刻画 fixture。
- [x] 每个 Source candidate 输出唯一 selection decision 和 reason owner；Context disposition 由独立 Context owner 记录，不复用 final evidence filtered reason。
- [x] 明确定义 fusion output、rerank input/output、budget input/output 契约。
- [x] 标出与目标相反的旧测试及 S09 删除/改写面，不把旧 reserve 语义带入目标 policy。
- [x] 为 S09 生成删除清单和切流断言。

退出条件：策略测试能解释代表候选的保留/淘汰；没有生产双写；切流所需契约和删除清单完整。


