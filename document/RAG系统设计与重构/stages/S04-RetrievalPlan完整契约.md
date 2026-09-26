# S04：RetrievalPlan 完整契约

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S04 的工程实施合同；具体执行结果、偏差和证据见 [records/S04.md](../records/S04.md)。

## 阶段设计依据

普通引号、章节和 route/entity 建议曾在 transition 中被无条件强化为 Provider 硬过滤，导致正常召回通道被清空。由此推导“建议不等于授权”：用户明确范围是 hard scope，结构、route、entity 等默认是 soft hints，只有 RetrievalPlanAssembler 能执行字段级强化。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

> 2026-07-21 分类勘误（现行优先）：S18 的 `Q-O8-07 / 2353139017278604147` 与 `Q-O8-08 / 2353139017278604192` 没有证明 S04 确定性契约失效。两轮 advisor/structure anchor 保持 advisory，hard scope、Plan/request、证据可见性、citation authority 和 O9 守恒均合法；问题表现来自 LLM negative-boundary 判断、多轮指代和答案生成波动，确定性工程链路同样不能保证。原拟议的“第四次 S04 reopen / history negative-boundary lineage”归因取消，不新增实现、fixture 或人工复测；S04 保持 `DONE`。

唯一目标：让 RetrievalPlan 完整表达实际执行所需信息，并对“上游 suggested hint -> provider-facing hard constraint”建立可审计的唯一授权边界。2026-07-12 首次实施时不提前宣称它已经是全链路唯一运行权威；以下 reopen 内容均为已经完成的历史施工证据，不是当前继续点。

> 2026-07-20 的 S18 新 exchange 先后推翻了本阶段三个旧结论：`sectionAnchors` 与 `sectionPathHints` 不能天然等价；route/entity suggestion 不能未经字段级 eligibility 升级；route confidence 不能决定 hard scope cardinality。三次 reopen 的确定性修复和关闭证据继续保留；不得根据后续模型理解/生成质量观察再次扩张 S04。

前置阶段：S03。


核心文件：

- `rag/model/RetrievalPlan.java`
- `rag/model/RetrievalQuestionPlan.java`
- `rag/model/RetrievalChannelPlan.java`
- `rag/service/QueryUnderstandingService.java`
- `rag/service/RetrievalPlanAssembler.java`
- `rag/service/DocumentRetrieveRequestFactory.java`
- `rag/service/KnowledgeRoutePlanner.java`
- `manage/model/DocumentRetrieveFilters.java`
- `manage/service/impl/KnowledgeRouteServiceImpl.java`
- `manage/service/keyword/ElasticsearchDocumentKeywordSearchGateway.java`
- `manage/service/KnowledgeRouteIndexService.java`
- `manage/service/DocumentProfileService.java`
- `QueryUnderstandingServiceTest.java`
- `RetrievalPlanAssemblerTest.java`
- `DocumentRetrieveRequestFactoryTest.java`
- `RagRetrievalEngineTest.java`
- `KnowledgeRouteServiceImplTest.java`。

## 2026-07-12 开工事实勘误

- `CODE_CONFIRMED`：当前源码已不存在名为 `keywordEntityAssist` 的活动方法，route candidate 分数由 embedding semantic score、`KnowledgeRouteIndexService` 的 ES score 和 topic relation score 组成。
- `CODE_CONFIRMED`：`KnowledgeRouteServiceImpl.lexicalScore` 是无调用死方法；`tokenize -> expandChineseNgrams` 仍被用于空判定和 reason，`buildReason` 仍用本地 `contains` 解释命中，embedding 异常日志仍声称退回本地词面匹配。因此本阶段责任是删除这些残余解释路径和误导日志，并用 route index 的结构化可用性/降级结果取代，而不是把不存在的活动 `keywordEntityAssist` 当作当前 score producer。
- `CODE_CONFIRMED`：`ChatPreparationOrchestratorTest.autoDocumentVeryLowConfidenceStillAsksClarification` 正在保护低置信提前澄清的旧行为，必须先改为目标失败测试；`RagRetrievalEngineTest` 还允许只有 channel selection 的不完整 Plan 进入执行，必须补 fail-fast 契约。

契约至少包含：

- normalized query 和 per-sub-question execution query。
- knowledgeBase/document/task scope。
- structured anchors、metadata filters 和 route source。
- 每 channel 的 enabled、weight、topK、timeout、budget。
- candidate window、唯一 rerank window、final evidence budget。
- 通用 rank features。
- table/graph/raptor controlled plan。

实施清单：

- [x] 用测试固化当前模式：当前文档、自动知识、多文档、follow-up、structure/table/graph/raptor。
- [x] 2026-07-12 首次范围内完成 `QueryUnderstanding`、`DocumentQuestionRouter`、route candidate source、allowed documents 和历史继承的结构化 Plan 投影；2026-07-20 新证据证明普通引号产生的 `sectionAnchors` 仍可越权成为 hard filter，本项不能作为 reopened 退出证据。
- [x] 用 O8-11 回归“上一题文本串入当前题”的历史污染，当前问题、rewrite 和 follow-up context 的边界必须可解释。
- [x] `KnowledgeRouteServiceImpl` 删除无调用 `lexicalScore`、本地 `tokenize/expandChineseNgrams` 和 `buildReason.contains` 残余；route 的词面 score 与命中解释只来自 route index 的 BM25/ES 标准结果。
- [x] route 只输出 KB 内 candidate documents/scopes/topics、confidence 和 features；top1 只用于展示或高置信提示，不是最终文档决定。
- [x] route 低置信时扩大 allowed candidate pool 并保留多通道召回，不能用 top1 或默认澄清提前截掉正确文档。
- [x] semantic route 不可用时只允许降级到 `KnowledgeRouteIndexService` 的 ES/BM25；ES 也不可用时，在已选 KB 硬边界内保守扩大到受上限控制的 allowed documents，并记录 degraded trace。
- [x] embedding 异常后只降级到 route index；route index 也不可用时按 KB 硬边界有界扩池并显式记录 degraded；为 `No/ov/va/aR/RA` 一类子串误命中增加失败测试。
- [x] assembler 成为 history/navigation/query understanding 的唯一解释位置；位置已经集中，但 2026-07-20 新证据证明 suggested section anchor 升级为 provider hard filter 的授权不变量不完整。
- [x] Plan snapshot 能逐字段表达旧路径实际执行值。
- [x] 缺字段和非法 scope fail fast，不靠 channel 猜默认值。
- [x] 明确构建期 enrichment 配置和运行时 RetrievalPlan 参数的边界；Plan 只能消费已持久化的 document/chunk profile 特征，不能读取构建规则重新解释查询。
- [x] 暂不删除 channel 旧接口，明确记录待 S05 迁移项。

2026-07-12 首次退出条件：Plan 单测覆盖当时列出的字段、默认值和边界；route top1 不截断最终候选，低置信扩池有失败测试；route score/reason 不再包含本地词面匹配残余；O9 能保存完整可序列化 snapshot；没有把旧对象简单嵌入 Plan 充当完成。该条件当时未覆盖“弱 section hint 不得升级为 hard filter”的负向授权边界。

## 2026-07-12 首次关闭证据（已被 2026-07-20 新证据部分推翻）

- `CODE_CONFIRMED`：`RetrievalPlan` 已逐字段拥有 query、scope、typed anchor、metadata filter、route candidate/source/features、五通道参数、candidate/rerank/final window、rank features 和受控 table/graph/raptor intent；`validateForExecution()` 对缺 query、非法 scope、重复/非法 channel 和倒置窗口 fail fast。
- `CODE_CONFIRMED`：`RetrievalPlanAssembler.AssemblyInput` 是 history/navigation/query understanding 的单一解释入口；Plan 模型未嵌入 `HistoryPlanningContext`、`QueryUnderstandingResult`、`DocumentNavigationDecision`、`KnowledgeRouteDecision` 或 `ConversationExecutionPlan`。
- `CODE_CONFIRMED`：历史自然文本不进入 execution query；follow-up 只继承最终证据的 typed identity。`DocumentNavigationDecision.retrievalPlan` 已删除。
- `CODE_CONFIRMED`：route 本地 `lexicalScore`、tokenizer/ngram 和 `buildReason.contains` 残余已删除；ES route index 返回结构化 available/unavailable，双依赖不可用时只在已选 KB 的 allowed documents 内有界扩池。
- `CODE_CONFIRMED`：低置信有分 top1 保留原 score/source/features/confidence，同时补足 allowed candidate pool；top1 只投影为高置信 hint，不截断完整 document scope。
- `CODE_CONFIRMED`：RAG_RETRIEVE start/complete/failure O9 snapshot 保存完整 Plan；`DocumentRetrieveRequestFactory` 和 `RagRetrievalEngine` 已消费 Plan 投影及 timeout，但五个 channel 旧接口仍按阶段边界保留给 S05，不能据此宣称 Plan 已是全链路唯一运行权威。
- 自动验证：S04 定向 `50/50`、chat 全量 `238/238`、14 模块主源码编译、门禁自测、正式门禁、静态契约检查和 `git diff --check` 全部通过。
- 正式门禁逐项审阅后共有 `1934` 个 finding，S04 debt owner 为 `0`；`answerExpectation/NORMAL_QA` 两项保留为 S15 债务，构建期 document profile 本地词表十项保留为 S16 债务，没有批量伪装为 allowed。
- 只读 exchange watermark 仍为 `2326901233866023009`；本阶段未执行真实上传、解析、重建或聊天，也不关闭 `O8-RD-PARSE-01`、`O8-RD-FINAL-01/02`。
- 完整红绿灯、命令、退出码、baseline 分类和风险见 [S04 执行记录](../records/S04.md)。

## 2026-07-20 REOPENED 事实勘误

### 已确认事实

- `DATA_CONFIRMED`：S18 的两个独立新 exchange `2349349172496310552`（Q-ROUTE-07）和 `2349349172496311534`（Q-O3-01）稳定复现同一失败。两轮均把普通事实问题中被引号引用的回答说法“先试一下再说”写入 `sectionAnchors`，最终错误回答文档没有暂停发布规则。
- `DATA_CONFIRMED`：S17 active chunks `2344517128129963207`、`2344517128129963208` 分别明确给出五类不允许进入生产的条件，以及“风险已知但未处理时默认暂停发布”；产物和索引存在，但两轮均未进入 accepted candidate。
- `DATA_CONFIRMED`：错误 Plan 使 vector、keyword、table、graph-rag 的 recall/accepted 均为 `0`；只有 RAPTOR 返回六条无关候选。该现象发生在 fusion、rerank、final evidence 和 citation 之前。
- `CODE_CONFIRMED`：`QueryUnderstandingService.deterministicFallback()` 会把所有 2-40 字的引号文本加入 `sectionAnchors`；仅出现引号虽不足以把 `queryType` 变为 `STRUCTURE_NAVIGATION`，anchors 仍被保留，且 `validate()` 无区别合并 fallback/advisor anchors。
- `CODE_CONFIRMED`：引号抽取启发式早于 S04 已存在；S04 commit `552c6516f60ca297f084bf1f80dcfef817b181a8` 新增的 `RetrievalPlanAssembler.buildMetadataFilters()` 将全部 `sectionAnchors` 无条件映射为 `metadataFilters.sectionPathHints`，把旧的弱语义提示固化为权威 hard filter。
- `CODE_CONFIRMED`：`DocumentRetrieveRequestFactory` 只投影 execution request 中已经授权的 filter；ES keyword gateway 把 `sectionPathHints` 放入 `bool.filter`，MySQL 路径生成 `section_path LIKE` 条件。provider 忠实执行了错误 Plan，没有在外层重新解释业务语义。
- `CONFIRMED`：最早 owner 是 S04 的 RetrievalPlan filter-authority 契约，不是 S05 provider 迁移、S09 final evidence、S17 构建产物、ES 数据或日志/查询口径。S04 修复完成前不得叠加重开更晚 owner。

### 文档差异与证据不足

- 原“`QueryUnderstanding` 只产出受控语义”结论过宽；当前事实只支持“语义已结构化”，不支持“所有结构化字段都已获得正确执行权限”。
- 原“assembler 成为唯一解释位置”方向仍正确，但唯一位置不等于授权规则已经正确；S04 缺少 suggested anchor 到 hard constraint 的显式 eligibility invariant。
- 原定向测试主要保护章节号、中文章节、引号章节标题、相邻/父子章节等正例，没有保护普通事实引语、错误 advisor anchor、低置信 structure advice 和结构增强零命中的负向边界。
- `RetrievalPlanAssemblerTest.completeContractCapturesQueriesScopesFiltersChannelsWindowsAndRankFeatures` 直接断言 `DOCUMENT_QA + sectionAnchors=["3.2"] -> sectionPathHints=["3.2"]`。明确章节号可能仍有合法 hard-filter 资格，但该测试没有表达资格来源，当前形状会固化“任意 sectionAnchors 都可升级”的旧错误，必须拆成 characterization 与目标不变量测试。
- 当时证据尚不足以宣称 entity/year/document hint 存在同一故障；第一次 reopen 因此只修改 section 授权。后续主动源码审计已经确认 entity exclusion 的可达硬化链，见文末第二次 reopen；`yearHints` 仍无 hard consumer，document hint 固定为空，不能把后来的 entity 证据倒灌为其他字段也有问题。

## REOPENED 项目内数据流与方案审查

### 项目内已确认数据流

1. `KnowledgeBoundaryResolver` 和会话知识范围快照生产明确的 KB/document hard scope。
2. `QueryUnderstandingService`、`KnowledgeRoutePlanner` 和结构导航组件只能生产建议、候选和置信度，不能直接写入 Provider hard filter。
3. `RetrievalPlanAssembler` 是 suggestion-to-constraint 的唯一 transition，负责字段级 eligibility、冲突处理、fail-closed 和最终授权。
4. `DocumentRetrieveRequestFactory` 与各 Provider 只投影并执行 Plan；普通召回结果不能因可选结构增强没有命中而被清空。
5. O9 必须同时记录上游 suggestion 和 Plan 最终授权结果，使每次强化都能逐字段对账。

### 候选方案比较

- “所有结构化字段直接下推为 filter”实现简单，但会让上游模型或启发式误判同时清空多个正常 Provider，拒绝。
- “Provider 零召回后自行移除 filter 再重试”会形成第二个 scope authority，并掩盖错误 Plan，拒绝。
- “所有建议永不强化”会破坏用户明确章节导航和字段过滤，拒绝。
- 采用字段级授权：用户显式范围始终是 hard scope；确定性章节语法或满足置信度、operation 和结构定位契约的建议可由 assembler 强化；其他信号只保留为 soft hint 或可观测增强。

### 本项目稳定边界

- Java 继续拥有 RetrievalPlan、document/task identity、五类 Provider、O9、final evidence 和 explicit citation binding。
- request factory 与 Provider 保持无语义薄投影，不新增第二次解释、失败后删 filter 或放宽 scope 的 adapter。
- 可选增强必须可降级，降级只能回到同一合法 hard scope 内的普通召回，不能扩大用户范围。

## REOPENED 目标契约与修改边界

### Producer、transition、consumer

- Producer：`QueryUnderstandingService` 可以产生 suggested section anchor，但普通引号本身不证明结构导航意图。确定性 fallback 只有在完整章节导航语法中，才可把引号标题作为结构 anchor；advisor 输出仍需受控校验。
- Transition / 唯一 authority：`RetrievalPlanAssembler` 只能在合法 `StructureNavigationIntent`、白名单 operation、确定性结构语法或足够置信度，以及满足当前结构定位契约时，把结构信号升级为 provider-facing hard section constraint。
- Consumer：`DocumentRetrieveRequestFactory`、`RetrievalExecutionRequest` 和五个 provider 只投影/执行 Plan，不重新解释 anchor，也不在零召回后自行移除 filter、扩大 scope 或发起第二套检索。

### 必须保持的不变量

- `sectionAnchors` 是 suggested semantic signal，不天然等于 `metadataFilters.sectionPathHints`。
- 普通 `DOCUMENT_QA` 中的引号、术语、原文摘录和对比说法不得仅因文本形状产生 hard section filter。
- 明确 `13.1`、`3.2`、中文“第十三章/第几节”，以及引号章节标题配合“属于哪个章节/下一节/同级章节”等完整导航语法必须继续工作。
- 低置信、缺少合法 intent、operation 非法、冲突或未满足稳定结构定位契约的 hint 应 fail closed 为“不产生 hard filter”；当前已选 KB/document/task 硬 scope 不得因此放宽。
- 弱结构信号如需保留，只能进入普通 query、结构 intent 或可观测软增强；结构增强无结果不能清空同一合法 scope 内的普通候选。
- O9 必须能区分“上游建议的 anchor”和“RetrievalPlan 最终授权的 hard filter”。若现有 Query Understanding stage 与 Plan snapshot 已足以逐字段对账，则补守恒测试；否则只增加满足该区分所需的最小通用观测，不新建重复权威。
- 禁止在生产代码、Prompt 或配置中加入冻结问题短语、暂停发布、文档名、部门/系统词、`contains` 规则、词表、黑名单或 O9 reason 驱动的例外。

### 实际修改边界

- 允许修改：`QueryUnderstandingService` 的通用结构语法/anchor 生产边界、`RetrievalPlanAssembler` 的 hard-filter eligibility、必要的最小 Plan/O9 投影，以及对应单元/集成测试。
- 默认不修改：ES/MySQL/provider 的 filter 执行语义、S05 execution request 迁移、S09 final policy、S17 构建产物和数据库/ES 数据。只有新源码事实证明边界不符时，先勘误本节再扩展。
- 旧 exchange/chunk 只证明旧问题存在；目标测试使用中性、通用结构语法 fixture，新代码通过必须由新代码、新 exchange 和新的 O9 证据证明。

## REOPENED Characterization 与目标红灯

- [x] 普通事实问题包含任意引号文本：结果为 `DOCUMENT_QA`，没有合法 `StructureNavigationIntent`，不得产生 `sectionPathHints`。
- [x] advisor 返回 `DOCUMENT_QA` 但错误携带 `sectionAnchors`：Plan 不得授权 hard section filter。
- [x] advisor 返回 `STRUCTURE_NAVIGATION`，但缺少合法 intent、顶层或 intent 置信不足、operation 缺失/非法：fail closed 为不产生 hard filter，并保留普通检索 scope。
- [x] 明确章节号 `13.1`、`3.2` 和中文章节引用继续产生符合契约的结构约束。
- [x] 引号章节标题配合明确“属于哪个章节/下一小节/同级章节”继续形成合法导航 intent；仅有同一引号标题而无导航语法时不得获得 hard-filter 权限。
- [x] 现有相邻章节、父章节、直接子章节和章节枚举测试不回归。
- [x] request factory 只投影 RetrievalPlan 已授权的 filter；provider 不出现第二套语义判断或零召回放宽分支。
- [x] O9 对 suggested anchors 与 authorized filters 可逐字段对账；两个修复后 exchange 的 Plan/request reconciliation 均无差异，非法升级计数为 `0`。

## REOPENED 实施清单

- [x] 完成上述开工事实核验，并在 [S04 执行记录](../records/S04.md) 记录 `CONFIRMED` 等级、文档差异、证据不足、固化旧错误的测试、新 fixture 和实际修改边界。
- [x] 先运行并保存 characterization 与目标红灯，再修改生产实现。
- [x] 修正 Query Understanding 的普通引号/完整结构导航边界，同时保留合法结构导航正例。
- [x] 在 RetrievalPlanAssembler 建立 suggested anchor -> hard section constraint 的唯一 eligibility 校验。
- [x] 保持 request factory/provider adapter 为薄投影，不增加兜底重试或业务语义。
- [x] 补齐必要的最小 O9 守恒与 Plan/request 对账测试。
- [x] 运行 S04 定向测试、受影响结构导航与五 channel 回归、chat 模块回归、编译/静态门禁和 `git diff --check`；精确结果和唯一预存非 S04 全量错误见执行记录。
- [x] 退出需要真实运行验证；已冻结 HEAD、PID、代码/Prompt/config hash 和 exchange/document/task 水位，并由用户在重启后执行两个全新独立会话。AI 只读验收 exchange `2349864293693931521/2349864293693931555` 的 Plan、request、channel recall、正确 chunks、答案与 O9 均通过。

## 2026-07-20 REOPENED 运行时关闭证据

- `DATA_CONFIRMED`：普通事实问答 `2349864293693931521` 为 `DOCUMENT_QA`，Query Understanding `sectionAnchors=[]`，Plan/request `sectionPathHints=[]`，reconciliation `differences=[]`；错误 navigation top hit 仅保留为 `SOFT structureAnchor`，未升级为 provider hard filter。
- `DATA_CONFIRMED`：该轮恢复 vector=`10`、keyword=`1`、raptor=`6`；chunk `2344517128129963207` 成为最终 Source，chunk `2344517128129963208` 由 vector/keyword 两路 gate-passed 召回为 Context，答案完整列出五类暂停条件和默认暂停原则。
- `DATA_CONFIRMED`：结构导航 `2349864293693931555` 为 `STRUCTURE_NAVIGATION/PARENT_SECTION`，Plan/request 均保留 `5.3` 与标题 hint，确定性结构结果 current=`5.3 不允许进入生产的情形`、parent=`五、发布前准备要求`；答案与结构树一致。
- `DATA_CONFIRMED`：两轮均为 9 个成功 stage、无 stage error；Prompt rendered Source identities 等于 eligibility，explicit citation identities 是有序子集，binding/FINALIZE/archive identity 与顺序完全相等，`conservationStatus=CONSERVED`，`context_only=1 AND is_selected=1` 为 `0`。
- 自动关闭验证：S04 九类矩阵 `74/74`、受影响结构/request/provider 回归 `28/28`、14 模块编译、正式静态门禁和两个 diff check 全部通过。完整命令、ID、数组和非阻断观察见 [S04 执行记录](../records/S04.md)。

## REOPENED 退出条件

- 普通 `DOCUMENT_QA` 引号文本、错误 advisor anchor、低置信或非法 structure advice 均不能产生 provider-facing hard section filter。
- 合法章节号、中文章节、引号章节标题加明确导航动作、相邻/父子章节等结构能力全部保持绿色。
- RetrievalPlanAssembler 是 hard-filter eligibility 的唯一 authority；request factory/provider 没有新增语义修复、零召回删 filter、二次检索或 scope 放宽。
- 已选 KB/document/task 硬边界在所有降级路径中保持不变；结构增强失败不清空同一合法 scope 内的普通候选。
- O9 能对账 suggested anchors、authorized filters、execution request 和 channel 结果，不再把上游建议与最终执行权限混为同一事实。
- 生产代码、Prompt、配置和通用 fixture 不含验收短语、文档名或业务词补丁。
- S04 定向测试、受影响回归、编译/静态门禁、`bash scripts/rag-audit-gate.sh` 和 `git diff --check` 全部通过，命令、退出码和测试数量写入 `records/S04.md`；chat 全量不得出现相对开工 committed HEAD 新增的 failure/error。
- `CODE_CONFIRMED`：本次 S04 开工 HEAD 的 `MybatisRetrievalObserveStoreTest` 已读取 `sql/Mysql/migrations/V20260719__retrieval_result_citation_identity_hash.sql`，但同一 HEAD 不包含该文件，因此 chat 全量固定存在唯一 `NoSuchFileException`；`create_table_mysql.sql` 已有 `MEDIUMTEXT + citation_identity_hash`。该 successor-owned 基线差异必须如实记录，但不属于 S04 section-filter 回归，也不得在本阶段越权补迁移或修改其测试。
- 使用修复后固定版本产生的新真实 exchange，分别证明普通引号不会清空正确召回、合法结构导航仍然工作；旧 exchange 不得作为新代码通过证据。
- 只有上述条件和 `records/S04.md` 证据全部完成后，才允许按 改造计划与进度 的 active reopen 协议原子将 S04 改回 `DONE`、恢复 `currentStage=S18` 并清空 `activeReopen`；随后停止，不在同一会话继续 S18。

## 2026-07-20 第二次 REOPENED：统一 suggestion-to-constraint 授权

### 开工确认与文档勘误

- `CODE_CONFIRMED`：`KnowledgeRouteServiceImpl.route()` 只生产最多五个 scope/topic/document candidate、score、confidence、source 和 feature；`KnowledgeRoutePlanner.plan()` 才是 candidate 能否成为 document/task execution scope 的业务 transition。当前 Planner 只要候选非空就把全部候选写入 `routedDocumentIds/routedTaskIds`，`shouldAskClarification(...)` 只检查空列表，`shouldAllowLowConfidenceMultiDocumentRetrieval(...)` 只写日志/O9，不拥有执行授权。
- `CODE_CONFIRMED`：当时把 `confidence >= 0.55`、`[0.45,0.55)` 和 `< 0.45` 分别解释为 top1、多文档 execution scope 与澄清。这只是 route score 配置，不是用户 scope 授权；S18 exchange `2350185557247680875` 已证明该解释会把显式多文档问题静默收窄为 top1。本历史结论已由下方“第三次 REOPENED”勘误，不再是当前契约或退出依据。
- `CODE_CONFIRMED`：`QueryUnderstandingResult.targetEntities/excludedEntities -> RetrievalPlanAssembler.metadataFilters -> EvidenceApplicabilityService -> FinalEvidenceSelectionPolicy` 是可达的 Source 排除链。当前只有总体 confidence、长度和去重，没有当前轮 grounding、受控 negative-boundary intent 或独立 authorization；普通 advisor suggestion 因此可产生 `FILTERED_NOT_APPLICABLE`。
- `CODE_CONFIRMED`：`targetEntityHints/excludedEntityHints` 没有 provider consumer；它们只被 execution request 重复投影/对账，再由 final policy 从 Plan metadata filters 读取。把 final-evidence 授权塞在 provider-facing filters 中同时泄漏职责并制造第二种字段含义。
- `CONFIRMED`：第一次 reopen 已完成的 section eligibility、Table/Graph catalog/schema 对账、普通通道并行、channel/rerank 降级、final evidence 单一权威和 explicit citation fail closed 均保持有效；本次不重做、不回退。
- `HYPOTHESIS`：route profile 内容可能有质量波动；`yearHints` 未来若出现 hard consumer 可能需要独立授权。当前没有足够证据删除 profile route 或修改 year 行为，两者不进入生产改造。

### 第二次重开的项目内证据与设计取舍

主动审计确认，route candidate、entity suggestion 和 section hint 虽然来源不同，却共享同一类风险：建议字段在没有独立授权 producer 时被 transition 强化为排除性约束。用户或业务选择形成不可突破的 allowed scope；route candidate 默认 advisory，只能形成可降级的 hint 或排序特征。候选方案包括“继续为每个字段增加局部阈值”“由 Provider 自行纠错”和“统一字段级 eligibility”。前两种方案会复制决策或让阈值承担其无法证明的业务权限，因此选择由 Plan 前的 eligibility 统一授权，并让 request factory/Provider 只做薄投影。

### 字段级 eligibility 与唯一 authority

| 字段 | 默认权限 | 升级条件 | 唯一 authority | 失败语义 |
| :--- | :--- | :--- | :--- | :--- |
| 用户选择的 KB/document/task | HARD_ALLOWED_SCOPE | 稳定业务 ID 与当前 selection snapshot 对账 | `KnowledgeBoundaryResolver` / `RetrievalPlan.validateForExecution()` | fail closed，不得扩出 allowed scope |
| route document candidate | ADVISORY | 稳定 document/task ID 且位于 allowed scope 时，只允许成为推荐 hint/排序特征；score/confidence 永远不是 scope-cardinality 授权 | `RetrievalPlan` 保存候选，消费方只能按 advisory 契约使用 | route 不可用时关闭推荐增强，在既有 hard allowed scope 内继续普通召回 |
| section anchor | ADVISORY | 第一次 reopen 已建立的确定性结构语法或合法 structure intent/confidence/operation | `RetrievalPlanAssembler` | 不产生 hard section filter，普通召回继续 |
| target/excluded entity | ADVISORY | 顶层 confidence 达标、受控 `NEGATIVE_BOUNDARY`、target/excluded 都非空且互斥、每个实体都由当前轮原问题明确 grounding | `RetrievalPlanAssembler` | applicability plan 保持 advisory，final policy 不得排除 Source |
| year hint | ADVISORY | 当前没有 hard consumer | 无 | 只保留现状，不新增执行权限 |
| Table/Graph advice | ADVISORY | 现有 schema/catalog/operation/query grounding 契约 | 各自既有 planner | 非法 plan 拒绝增强，普通通道继续 |

### 目标模型与修改边界

- `KnowledgeRoutePlanner` 必须把 scope authority 与 route advice 分离：`DOCUMENT` 模式由显式 document/task 形成唯一执行范围；`AUTO_DOCUMENT` 模式由所选 KB selection snapshot 中全部 ready document/task 形成执行范围。route 只产生候选和可选 top recommendation hint，任何 confidence 区间都不得改变 scope cardinality；route 失败只关闭 hint，不能阻断同一合法范围内的普通召回。
- `RetrievalPlan` 新增独立的 evidence applicability plan，显式携带 advisory/authorized 状态、target/excluded entities、source 和 reason。`RetrievalMetadataFilters` 与 `RetrievalExecutionRequest.Filters` 删除没有 provider consumer 的 target/excluded 字段；不保留 boolean compatibility flag 或双写模型。
- `EvidenceApplicabilityService` 只消费 Plan 已授权的 applicability contract。ADVISORY、缺失或非法 contract 返回 `APPLICABLE_UNKNOWN`；只有 `AUTHORIZED_EXCLUSION` 才能产生 `NOT_APPLICABLE`。`FinalEvidenceSelectionPolicy` 仍是唯一 Source 选择权威。
- Query Understanding Prompt 只定义 entity 字段的 advisory 含义和显式 target/excluded 边界；Java eligibility 仍是最终授权，不把 Prompt confidence 当单独执行权。
- 允许修改：`KnowledgeRoutePlanner`、必要的 `KnowledgeRouteService` trace 投影、Plan/applicability 模型、`RetrievalPlanAssembler`、`EvidenceApplicabilityService`、`FinalEvidenceSelectionPolicy`、query-understanding Prompt、对应测试与最小 O9。默认不修改 route score producer、provider 查询语义、fusion/rerank、S17 产物、数据库 schema 或 ES 数据。

### Characterization、目标红灯与实施清单

- [x] 历史实现曾按 route confidence 把候选编译为 top1/多文档/澄清；S18 已证明这组测试固化了错误语义，不能再作为退出依据。
- [x] AUTO_DOCUMENT 的高/中/低 confidence、FAILED/null/exception route 都保持完整 KB ready scope；候选五条上限不能定义完整 execution scope。该项最终由第三次 reopen 的统一 hard-scope 契约关闭。
- [x] route trace 和 Plan snapshot 明确区分 candidate pool、recommendation hint、hard allowed scope 与 authorized execution scope；MySQL `selected_document_id` 不得记录未获 hard scope 权限的 advisor top1。
- [x] DOCUMENT 模式继续只授权用户显式选择的单一 document/task，且 Plan 对 scope、task 和 recommendation hint 做一致性 fail-closed 校验。
- [x] 普通或低置信 target/excluded suggestion、缺少 `NEGATIVE_BOUNDARY`、实体未在当前原问题 grounding、target/excluded 冲突时，applicability 保持 ADVISORY，Source 不得收到 `FILTERED_NOT_APPLICABLE`。
- [x] 明确且 grounded 的 negative-boundary fixture 形成 `AUTHORIZED_EXCLUSION`，仅此时允许排除只支持 excluded entity 的 Source；target Source 与未知 Source 继续遵循既有 applicable/fail-open 语义。
- [x] provider-facing metadata filters 和 execution request 不再携带 target/excluded hard-filter 字段；Graph intent 的 advisory entity 建议保持现有独立契约。
- [x] Plan/O9 序列化同时显示 Query Understanding suggestions、applicability authorization 和最终 filters；request reconciliation 继续为零差异，provider 不增加二次语义判断。
- [x] 先保存中立红灯，再实现；运行 S04 矩阵、Evidence/Final policy 回归、execution request/provider 回归、chat 全量、14 模块编译、正式门禁和 diff check。
- [x] 固定修复后 HEAD/PID/hash/watermark，由用户产生全新 route 与 entity boundary exchange；AI 只读核验后才能关闭第二次 reopen。旧 exchange 只证明旧问题存在。

### 第二次 REOPENED 首轮运行只读核验

- `DATA_CONFIRMED`：entity boundary exchange `2350185557247680557`（conversationId=`mrsxf4id1xacq8a4`）通过。Query Understanding confidence=`0.91`，当前原问题明确 grounding target=`L4 数据访问日志`、excluded=`L3 数据访问日志` 并产生 `NEGATIVE_BOUNDARY`；Plan 唯一授权 `AUTHORIZED_EXCLUSION`。provider request 不含 `targetEntityHints/excludedEntityHints`，Plan/request reconciliation `differences=[]`。
- `DATA_CONFIRMED`：该文档把 L3/L4 同置于 chunk `2344516612733892611` 的一个表格 Source；该 Source 同时支持 target，故保持 `APPLICABLE` 而不是错误过滤。答案为 `L4 数据访问日志的保存期限为 2 年 [6]。`；9 个 stage 全部 `COMPLETED`，无 `FAILED/RUNNING/SKIPPED`，selected Context=`0`。
- `DATA_CONFIRMED`：该轮 Prompt rendered Source identities 与 `explicitCitationEligibleIdentities` 六项完全相等；合法 `[6]` 唯一绑定 `CHUNK:2344516612733892183:2344516612733892611`，binding/FINALIZE/archive 三方 identity 完全相等，`conservationStatus=CONSERVED`，无 rejected token。该 case 不需要重测。
- `DATA_CONFIRMED`：route exchange `2350185557247680513`（conversationId=`mrsxe0b8gt4zadfp`）没有命中目标负例。真实 confidence=`0.5311`，五个候选均为正分、稳定 document/task identity 且位于 allowed scope；Planner 按既定 `[0.45,0.55)` 契约正确产生 `MULTI_DOCUMENT`，Plan/request scope 完全相等并执行检索。MySQL `selected_document_id=NULL` 也符合多文档语义。该 exchange 是无效验收刺激，不是代码回归，不得据此改阈值或修改生产实现。
- `DATA_CONFIRMED`：替代问题 `compare O7` 已按当前 MySQL、65 条 ES route index 和当前配置的 `text-embedding-v4` 只读复算。ES 只命中当前 KB 的唯一 scope 与 topic `运营制度跨文档总结`，document lexical hit=`0`；前三个正分 document score=`19.2000/18.8000/15.2329`，动态 confidence=`0.4465 < 0.45`。预期 Planner 为 `CLARIFICATION_REQUIRED`、authorized document/task scope 为空、route trace `selected_document_id=NULL`，且不进入 `RAG_RETRIEVE`。
- 上述水位之后，用户已用同一修复版本创建全新独立 AUTO_DOCUMENT 会话 `mrsyckoymidga16n` 并原样提问 `compare O7`；最终只读结果见下一节。该 exchange 通过后，本清单最后一项才勾选并关闭 S04。

### 第二次 REOPENED 最终运行关闭证据

- `DATA_CONFIRMED`：替代 route exchange/trace=`2350185557247680607/2350185557247680610`，严格大于冻结水位且精确属于 conversationId=`mrsyckoymidga16n`；question/rewrite 均为 `compare O7`，模式=`AUTO_DOCUMENT`，只选择 KB `2344516406575456257`，未选择文档。
- `DATA_CONFIRMED`：实际 route confidence=`0.4465 < 0.45`，与动态预期一致；candidate pool 非空且 count=`5`，前三条正分 score=`19.2000/18.8000/15.2291`，后两条为 allowed-scope 零分扩展。AUTO ROUTE O9 为 `authorizationMode=CLARIFICATION_REQUIRED`、`authorizedDocumentIds=[]`、`authorizedTaskIds=[]`、`clarificationRequired=true`、`topDocumentId/name` 为空；MySQL route trace `selected_document_id=NULL`。
- `DATA_CONFIRMED`：系统只返回三文档范围澄清，execution mode=`CLARIFICATION`。该 exchange 没有 `RAG_RETRIEVE/EVIDENCE_BUDGET/ANSWER_GENERATE` stage，channel execution=`0`、retrieval result=`0`；七个实际 stage 全为 `COMPLETED`，无 failed/running/skipped。
- `DATA_CONFIRMED`：澄清答案无合法 citation token，CITATION_BINDING 的 rendered/eligible/explicit/source snapshot identities 全为空，parsed/bound/rejected=`0/0/0`、`conservationStatus=CONSERVED`；FINALIZE 与 archive source snapshot 同为空数组。日志只有 request、rewrite skipped、65 条 route index refresh、confidence=`0.4465` 和空 binding，相关区间无 WARN/ERROR/Exception。
- `CONFIRMED`：结合已通过 entity exchange `2350185557247680557`，route candidate 与 entity suggestion 两条统一 authorization 契约均获得修复后真实运行证据。S04 定向 `134/134`、14 模块编译、正式静态门禁和 diff checks 的自动证据继续有效；本次人工复测没有修改代码、Prompt、配置、document/task 或索引数据。
- 最终水位：exchange=`52/2350185557247680607`、route trace=`209/2350185557247680610`，document/task/task-log 仍为 `31/2344517128129962851`、`62/2344517128129963155`、`877/2344517162489704292`。S04 已按 active reopen 协议原子恢复为 `DONE`，currentStage 恢复 `S18`，activeReopen 清空；本会话不进入 S18。

> 第三次 REOPENED 勘误：以上 `compare O7 -> CLARIFICATION_REQUIRED` 只记录第二次 reopen 版本的真实历史行为，不再是当前预期。当前契约下，低 confidence 只关闭 recommendation，不能清空 KB ready hard scope，也不能阻断普通召回；`compare O7` 应为 `KNOWLEDGE_BASE_ALLOWED_SCOPE`、六组 document/task scope、`clarificationRequired=false`、`selected_document_id=NULL`。

### 第二次 REOPENED 退出条件

- 上述 route 与 entity eligibility fixture 全部绿色，且没有验收问题、文档名、业务词、`contains` 词表、黑名单或 O9 reason 驱动的分支。
- RetrievalPlan 继续是唯一语义解释点；selection snapshot 生产 hard allowed scope，route service/Planner 只生产候选和推荐 hint，request/provider 只投影，final evidence policy 只消费 Plan 已授权 applicability。
- advisor/route/channel 失败只能降低对应增强能力；只要显式业务 scope 非空，普通召回必须继续。失败不得扩大或收窄 allowed scope、删除 filter 重试、恢复旧规则或启用第二套决策。
- 阶段定向、受影响回归、编译、静态门禁和真实新 exchange 证据全部写入 `records/S04.md`；完成后按 active reopen 协议原子恢复 `S18 / IN_PROGRESS` 并停止。

## 2026-07-20 第三次 REOPENED：route advice 与 hard scope 分权

### 新证据与文档差异

- `DATA_CONFIRMED`：S18 修复后 exchange `2350185557247680875` 的 route confidence=`0.5773`，候选同时覆盖多个相关文档；当前轮 Query Understanding 为 `COMPARE + NEGATIVE_BOUNDARY`，但 `KnowledgeRoutePlanner.authorizeRouteScope()` 仅凭 `confidence >= 0.55` 生成 `CONFIDENT_TOP_DOCUMENT`，导致其他合法文档在进入 provider 前被排除。
- `CODE_CONFIRMED`：`KnowledgeRoutePlanner` 在 Query Understanding 之前运行，因此它看不到结构化的 compare/negative-boundary intent。若在该层解析原问题关键词来弥补授权，会产生第二套 query parser 和样例规则，违反单一 Plan authority。
- `CODE_CONFIRMED`：`KnowledgeRouteServiceImpl.route()` 只返回最多五个候选，而 selection snapshot 保存全部 ready document/task。候选上限天然不能证明 hard allowed scope 完整性。
- `CONFIRMED`：前两次 S04 已正确完成的 section eligibility、entity applicability authorization、provider 薄投影、O9 对账和 citation 守恒继续保留；本次只修正 route scope 权限，不回滚这些能力。
- 文档差异：本文件原先把 route confidence 阈值误写成 scope-cardinality 授权，并要求“单对象高置信仍 top1”。该规则没有独立 grounding/catalog authorization producer，也与用户显式选择的 KB/document hard scope 边界冲突，现已勘误。

### 当前唯一契约

- `DOCUMENT`：用户显式选择的 document/task 是单一 hard execution scope；route shadow 结果不改变它。
- `AUTO_DOCUMENT`：用户选择的 KB 及其 selection snapshot 中全部 ready document/task 是 hard execution scope；route candidates 始终是 advisory。
- route top candidate 只有在身份稳定、位于 allowed scope 且达到通用推荐质量条件时，才可成为 recommendation hint；hint 只用于可降级增强，不得改变 provider document/task filters。
- route 返回低置信、FAILED、null、异常或空候选时，不发布 recommendation hint，但普通召回继续使用完整 hard scope；只有 selection snapshot 本身没有任何 ready document/task 时才进入澄清/fail closed。
- RetrievalPlan 必须验证 authorization mode、allowed scope、execution scope、task 对齐和 recommendation hint；request factory/provider 只能精确投影 Plan。

### 第三次重开的项目内数据流与决策边界

新 exchange 证明 route confidence 可以描述候选质量，却不能决定 hard scope cardinality。单对象高置信不等于用户授权只检索该对象；AUTO 模式下 route 只能排序或建议候选，明确用户选择才拥有排除性。由此取消“高置信 top1 接管 hard scope”的旧规则，并保持普通召回优先、增强可降级。

### Producer、transition、consumer

- Producer：`KnowledgeBoundaryResolver` 从用户模式、KB/document 选择和 selection snapshot 生产完整 allowed document/task pair；`KnowledgeRouteServiceImpl` 只生产 scope/topic/document candidate、score、confidence、status 与 feature。
- Transition：`KnowledgeRoutePlanner` 是 route advice 到 Plan route contract 的唯一转换点。`DOCUMENT` 只授权用户显式 document/task；`AUTO_DOCUMENT` 原样授权完整 ready scope，并单独判断 top candidate 是否有资格成为 recommendation。FAILED/null/exception/低置信/非法 identity 只能关闭 recommendation。
- Consumer：`RetrievalPlanAssembler` 复制该契约，`RetrievalPlan.validateForExecution()` 校验 mode、allowed/execution scope 全等、document/task 对齐和 recommendation identity；request/provider 只投影。`BusinessChatService` 只通过 `explicitlyAuthorizedDocumentId/TaskId/Name` 持久化会话 scope，AUTO recommendation 不能成为 selected document。
- Consumer 逃逸复核：`ChatPreparationOrchestrator` 只把 AUTO recommendation 交给可选结构导航定位；结构结果进入 Context Only，不改 provider scope。`ConversationContextLoader` 在 AUTO 模式只按 selection snapshot 的 allowed documents 过滤历史 anchors，不按 recommendation 过滤；`ContextExpansionPlanner` 的执行范围优先且实际只使用完整 `documentScope/taskScope`。因此该 hint 可以降低或误判结构增强，但不能间接收窄普通召回。

### 自动实现与质量口径

- `CONFIRMED`：旧 `CONFIDENT_TOP_DOCUMENT`、`MULTI_DOCUMENT`、`confident-document-threshold`、`multi-document-retrieval-threshold` 及 `routedDocumentIds/routedTaskIds` execution 语义已删除；仅保留 `recommendation-threshold=0.55` 作为软提示质量阈值。
- `CONFIRMED`：中立 fixture 覆盖高/中/低 confidence、FAILED/null/exception、候选五条上限小于 ready scope、越界/无 task/零分/不稳定 identity、显式 DOCUMENT、session scope 持久化和 Plan/request 投影；没有验收短语、文档名或业务词分支。
- S04 的 BLOCKING_FAIL 只针对确定性契约：hard scope 被扩大/收窄、document/task identity 不守恒、recommendation 越权成为 selected document、Plan/request 不一致、applicability/final evidence/citation/O9 守恒失效。普通召回命中和排名波动、生成措辞差异、复杂语义综合不完整，若未违反上述契约，只按验收规则记为 `OBSERVATION_ONLY` 或后续 owner 的质量问题，不要求在 S04 做样例补丁。
- advisor、ES、Graph、reranker 或 Python 工具失败时允许降低增强能力或保留普通顺序；不得删除 filter 重试、扩大 KB/document scope、恢复本地业务词规则或直接写 ES 制造通过。

### 第三次 REOPENED 首轮运行只读验收

- `DATA_CONFIRMED`：用户重启后的 Java PID=`2727`，启动于 `2026-07-21 08:55:15 +08:00`；关键 class 生成于 `08:55:11/12`，代码、Prompt、配置与 audit baseline 的 SHA-256 均与人工前冻结值一致。人工前水位之后恰好新增两条 exchange 和两条 route trace，没有 document/task/task-log 变化。
- `DATA_CONFIRMED`：AUTO 高置信 conversationId=`mrty0jzidiaz1zm1`、exchange/route trace=`2352299265272848385/2352299265272848388`。route confidence=`0.5636`，发布规范 recommendation 只作 advisory；route trace 和会话 `selected_document_id` 均为 `NULL`。ROUTE、RetrievalPlan 和两个 execution request 的 allowed/authorized/document/task scope 都是 selection snapshot 的完整六组，两个 reconciliation `differences=[]`，两个子问题均进入普通召回。
- `DATA_CONFIRMED`：显式 DOCUMENT conversationId=`mrty3i616layj4fz`、exchange/shadow trace=`2352299265272848460/2352299265272848463`。RetrievalPlan authorization=`EXPLICIT_DOCUMENT`，document/task scope 与唯一 execution request 均为故障预案 `2344516578374151941/2344516578374152257`，reconciliation `differences=[]`，检索结果没有越出该文档。
- `DATA_CONFIRMED`：两轮所有实际 stage 均为 `COMPLETED`。Prompt rendered Source identities 与 explicit citation eligibility 完全相等；explicit binding、CITATION_BINDING snapshot、FINALIZE 和 archive source snapshot 都是同一有序四项数组，逐序 mismatch=`0`，`conservationStatus=CONSERVED`，selected Context、selected unresolved Source、selected missing identity 均为 `0`。
- `CONFIRMED`：DOCUMENT rerank 发生 `TIMEOUT/TRANSPORT` 后保留 weighted hybrid Source 并继续回答；vector/keyword/table/graph-rag/raptor=`10/10/1/6/6`，答案和合法 citation 完成。该行为符合本阶段采用的 本项目已确认的可降级增强原则，不是 S04 `BLOCKING_FAIL`。日志另有 Spring MVC 默认 async executor 的既有负载警告，不影响本次契约结论。
- `CONFIRMED`：前两例通过但不能替代第三例。当前 ES 仍为全局=`65`、本 KB=`14`、scope/topic/document=`1/7/6`，`compare O7` lexical 仍只命中 topic/scope、document hit=`0`；动态 confidence 参考仍约 `0.4465`。唯一剩余人工操作是新建独立 AUTO_DOCUMENT 会话原样询问 `compare O7`，验证低置信时 recommendation 为空但完整六组 scope 与普通召回继续。

### 第三次 REOPENED 最终运行关闭证据

- `DATA_CONFIRMED`：最终 conversationId=`mrtzgzk4f73wgbip`、exchange/route trace=`2352382896876044339/2352382896876044342`，严格晚于冻结水位。用户同时说明有网络失败后删除的会话；数据库只残留四条非目标 route trace，目标 exchange/trace 均唯一，验收未混入残留。
- `DATA_CONFIRMED`：实际 route confidence=`0.4465`、status=`LOW_CONFIDENCE`，recommendation document/task/name 均为空，`clarificationRequired=false`、AUTO selected document=`NULL`。authorization=`KNOWLEDGE_BASE_ALLOWED_SCOPE`，ROUTE、RetrievalPlan 和 execution request 的 document/task scope 都是完整六组，reconciliation `differences=[]`。
- `DATA_CONFIRMED`：系统实际进入 `RETRIEVAL`，vector/keyword/table/graph-rag/raptor=`9/0/0/6/6`，rerank 成功，Source/Context=`6/10`；retrieval result 越界数、selected Context、selected unresolved Source 和 selected missing identity 均为 `0`。
- `DATA_CONFIRMED`：全部 10 个 stage row 为 `COMPLETED`。Prompt rendered Source identities 等于 eligibility；答案中的 Unicode `[1–6]` 不是合法 ASCII `[n]` token，binding 按唯一协议保持 parsed/bound/rejected=`0/0/0`，explicit/binding/FINALIZE/archive identity 数组全部为空，`conservationStatus=CONSERVED`。该模型展示只记 `OBSERVATION_ONLY`，没有非法 citation 越权。
- `CONFIRMED`：目标 Java PID=`14348` 启动于关键 class 之后，HEAD/hash 与前两例及人工前冻结完全一致；目标日志区间无 WARN/ERROR/Exception/timeout。关闭前 `git diff --check` 和正式 audit gate 均通过。三例与全部退出条件现已满足。

### 目标红灯、实施与退出清单

- [x] 高置信、多候选、显式多对象或普通未指定对象的 AUTO 查询都不能被静默收窄为 top1。
- [x] 中低置信、route FAILED/null/exception 均保持完整 KB ready scope，且 recommendation hint 按资格为空。
- [x] route candidate 最多五条时，六条以上的 ready scope 仍完整进入 RetrievalPlan/request。
- [x] 越界、无 task、零分或不稳定 route candidate 不能成为 recommendation hint，也不能扩大/收窄 execution scope。
- [x] 显式 DOCUMENT 继续是单 document/task hard scope；不一致的 mode/scope/hint 必须在 Plan validation fail closed。
- [x] O9 同时显示 scope authority、allowed/authorized IDs、recommendation hint 和 advisory candidate pool，request reconciliation 保持零差异。
- [x] section/entity/applicability、五 channel、final evidence、Prompt/citation 与既有 S04 回归保持绿色。
- [x] S04 聚焦矩阵 `109/0/0`、扩展受影响矩阵 `120/0/0`、14 模块编译、正式静态门禁和 `git diff --check` 通过；chat 全量唯一 error 是当前 HEAD 缺少后继 owner 的 migration 文件，不在 S04 内伪造修复。
- [x] 冻结项目 HEAD、PID、class 时间、代码/Prompt/config/audit hash、MySQL exchange/route/document/task 水位、六组 ready scope 和 ES `65/14` 动态基线。
- [x] 用户已重启修复版 Java，并按 `当前阶段与下一步.md` 完成两个全新独立会话；AUTO 高置信与显式 DOCUMENT 均已只读验收通过，不需要重跑。
- [x] 用户建立唯一剩余的独立 AUTO_DOCUMENT 会话，原样询问 `compare O7`；未重跑前两例或 48 case，未上传或重建文档。
- [x] AI 按第三个新 conversationId 和最新冻结水位只读验证：`compare O7` 保持六组 scope、AUTO route trace `selected_document_id=NULL`、无澄清且普通检索实际执行，并继续满足 Plan/request/O9/citation 守恒。
- [x] 三例与全部退出条件已通过，允许按 active reopen 协议在同一次 改造计划与进度 修改中执行 `S04 -> DONE`、`currentStage -> S18`、`activeReopen -> null`；随后停止，不在同一会话继续 S18。

