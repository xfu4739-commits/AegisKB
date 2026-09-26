# S11：Markdown Structure 投影

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S11 的工程实施合同；具体执行结果、偏差和证据见 [records/S11.md](../records/S11.md)。

## 阶段设计依据

旧产物出现章节树全部 depth=1、原因列表误识别为标题和父子章节丢失。本阶段基于共享 Markdown 契约投影 section/list 业务结构，保持编号、层级、父子关系和原文 span 可追溯。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：从 S10 共享解析结果构建正确父子树，并让结构导航真实进入主链路。

前置阶段：S10。

必读：[S09 的 Q-ROUTE-03 冻结诊断](S09-Fusion-Rerank-Budget正式切流.md)中失败链路和测试约束；重建后 `9446` 对应语义必须投影为正文/列表，而不是 `TITLE` 或整段 `section_path`。

核心文件：

- `DocumentMarkdownSyntaxContract.java`
- `DocumentStructureProjector.java`
- `DocumentStructureNodeCandidate.java`
- `DocumentStructureNodeServiceImpl.java`
- `StructureNavigationResolver.java`
- `QueryUnderstandingService.java`
- `DocumentQuestionRouter.java`
- `FinalEvidenceSelectionPolicy.java`
- `DocumentStructureProjectorTest.java`
- `StructureNavigationResolverTest.java`
- `RetrievalPlanAssemblerTest.java`
- `FinalEvidenceSelectionPolicyTest.java`

约束：Python 提供语法事实，Java 做业务投影、校验和有限修复；Java 不重新猜整份 Markdown。正文不能因编号或短句批量变成 SECTION。

## 唯一语法权威与过渡退出

- `markdown-syntax.v1` 经 S10 Python schema 和 Java protocol 校验后的 typed syntax 是 Markdown 唯一语法权威。Java 构建期内存中的 `DocumentMarkdownSyntaxCandidate` 与持久化 `MARKDOWN_SYNTAX_JSON` 只是同一契约的内存形态和存储/审计形态，不得分别解释 heading、list、table、parent 或 span。
- 正常构建路径应直接消费已校验 typed DTO；需要从 artifact 重放或只读验收时，必须反序列化回同一 versioned DTO、执行同一校验并调用同一 projector。禁止为 JSON artifact 再写一套结构 parser，也禁止 typed DTO 失败后回退 `parsedText`/正则猜测。
- 当前已知旧链必须在修改生产代码前完成 characterization：Python `document_parser._build_structure_nodes()` 生产 `DocumentParseResponse.structureNodes`，Java `RagToolsDocumentParserService.toStructureNodes()` 映射到 `DocumentAnalysisResult.structureNodes`，`DocumentAsyncProcessServiceImpl` 再经 `replaceDocumentNodes()` 持久化并同步导航索引、图投影和 profile；`DocumentStrategyServiceImpl`、结构图服务、route/navigation 等消费持久化业务结构节点。阶段记录必须补齐所有 producer、transition、consumer 和最后一个迁移消费者，不能只依据类名推断。
- S11 的默认目标流是 `markdown-syntax.v1 -> 一个 Java structure projector -> DocumentStructureNodeCandidate -> task-scoped nexus_agent_document_structure_node`。持久化 structure node 可以保留为职责稳定的业务/导航投影，但必须可逐节点追溯到 typed syntax，且不得再拥有独立 Markdown 语法解释权。
- Markdown 路径的 Python `_build_structure_nodes`、response `StructureNode/structureNodes`、Java `toStructureNodes` 和任何从 `parsedText` 重新猜 Markdown 的 extractor 都属于待迁移旧语法链。S11 退出时应删除最后一个 Markdown 消费者对应的旧字段/分支；如果非 Markdown provider 经事实核验仍需同名 transport，必须限定为不解释 Markdown 的稳定 provider 投影，记录保留理由和边界，不能作为 Markdown fallback 或 compatibility facade。
- `CODE_CONFIRMED`：原 Java `DocumentStructureNodeExtractor -> DocumentStructureSignalExtractor -> DocumentStructureHierarchyResolver -> DocumentStructureTreeValidator` 链在当前生产代码中无调用点，只有测试保护其 raw-text/正则第二解释器。S11 不将这组死链改造成新 adapter，而是删除并由 `DocumentStructureProjector` 直接消费 typed syntax。
- `CODE_CONFIRMED`：非 Markdown provider 已生产 typed `DocumentBlock` 的 `TITLE`/layout 事实，而旧 Python `structureNodes` 只把这些 `TITLE` 投影成扁平章节。S11 将该行为作为非 Markdown 业务投影迁入同一 Java projector 的独立入口，并完全删除 Python/HTTP `structureNodes` transport；该入口不消费 `parsedText` 猜 heading，也不能成为 Markdown fallback。
- `CODE_CONFIRMED`：当前产品没有对既有 document 重新执行 parse-route 的 API；`POST /manage/document/upload` 才创建 `task_type=1` 的新解析任务，`POST /manage/document/index/build` 只创建 `task_type=2` 并读取 `document.last_parse_task_id` 对应的旧 blocks。S11 真实验收必须重新上传冻结源以生成新 document/parse task/artifact；只点击“构建索引”不能证明新 parser/projector 生效。新增既有 document reparse API 不属于本阶段为验收临时扩出的实现范围。
- 修改生产代码前先用目标 fixture 固化“同一 syntax node 只产生一个业务投影事实”和 DTO/artifact 重放等价性；再迁移 producer/consumer。旧测试若保护 `_build_structure_nodes` 或 raw-text regex 结果，必须判断其是在保护目标不变量还是固化第二语法权威。
- 退出精确扫描至少覆盖 `_build_structure_nodes`、Python response `structureNodes`、`RagToolsDocumentParseResponse.StructureNode/structureNodes`、`toStructureNodes`、Markdown 路径对 `DocumentStructureNodeExtractor.extract(parsedText)` 的调用，以及新增的正则/编号语法解释器。任何命中都必须归类为已确认的非 Markdown 稳定边界或删除，不能以“测试仍通过”为保留理由。

## Source span 单位

- 旧诊断 `[7786,7851)` 是 Python `str` 的 Unicode 字符下标半开区间，只用于复现旧错误，不是契约定位值。
- `markdown-syntax.v1` 的 `startByte/endByte` 是原始 `sourceText` UTF-8 编码后的 0-based 半开区间；line/column 是 1-based，column 按 Unicode code point 计数，end position 是半开区间终点。
- 对当前冻结源且 `sourceSha256=4113c18c82dd7b971db4664ea1ade603b9e76725ed04cc4fcca7982546675d57`，ordered-list node `md-000764` 的 byte span 是 `[16872,17039)`、位置是 `486:1 -> 491:1`，包含列表后的两个换行。node ID 只在同一 parser/version/source 下用于诊断，不得成为生产规则。
- S11 隔离重建以及后续 S17 构建产物验收必须先核对新 artifact 的 `sourceSha256`，再使用该 artifact 自身的 UTF-8 byte span 定位；source hash 不同时从 typed AST 重新定位，禁止把旧字符下标直接当 byte offset。

## O8-RD-PARSE-01：原因列表业务投影

必读：[S03 新代码真实 cohort 与阶段退出](../records/S03.md#2026-07-12-新代码真实-cohort-与阶段退出)及 S10 执行记录中已确认的 producer、provider 输入和共享事件。Java 只能消费 S10 的 typed event、origin 和 source span，不得按“原因”、章节号、文档名或三问词汇重新猜语法。

S11 必须服从 S10 已确认的 typed event、origin 和 UTF-8 byte span：若语法事实是正文/列表，则投影为对应 `LIST/TEXT` raw Source；若是合法 heading 或 mixed 结构，则保留其语法事实，同时为四项 source-authored 内容形成一个或多个结构正确、可引用的 Source 产物，不能只留下不可引用的 `TITLE` Context。所有分支都要保留正确 parent、section path、ordinal 和 sibling，且 `section_path` 只能是结构路径，不能等于整段四项内容。新增 fixture 使用通用 heading + ordered list + sibling body，不得包含旧 chunk ID 或验收业务词。

本阶段不调整 fusion、rerank 分数、finalTopK 数量或 citation policy。第五轮真实 cohort `DATA_CONFIRMED`：deterministic target 对应的 original TITLE 已成为合法 Source 并进入 ranking window，但短标题的 rerank 分接近 0，仍被唯一 `FinalEvidenceSelectionPolicy` 按预算淘汰，因而没有进入 Prompt。原“不调整 final policy”表述与“结构导航真实进入主链路”的退出条件冲突，现勘误为：不得在 ContextExpansion、Prompt 或 citation 层旁路 final policy；由 source-heading producer 写入 typed required provenance，唯一 final policy 在既有预算内按 target 顺序保留该 Source，并继续为每个候选产生唯一 decision。普通 structure body、普通 TITLE 和派生 TITLE 不得获得该 provenance，也不能覆盖更高 rerank Source。完成自动验证并进入本阶段既有的隔离人工重建后，AI 先按新 task 的 `MARKDOWN_SYNTAX_JSON.sourceSha256` 和该 artifact 自身的 UTF-8 byte span 定位 `14.1.2`，再按 S10 已确认契约核对类型、结构和 provenance，并确认四项内容具有可引用 Source identity、不是只以 `TITLE` Context 存在。S11 必须完成 O8-09/10/11 的阶段性回答、Prompt、explicit citation binding 和 final source snapshot 运行闭环；S18 再在最终 48 用例 cohort 中重新执行终验，不能把 S11 自身的三问退出条件推迟给 S18。

实施清单：

- [x] 开工 characterization 列出旧 `structureNodes` 全部 producer、transition、consumer、最后消费者和保护旧错误的测试。
- [x] 建立 typed DTO 直接投影与同一 DTO artifact 重放等价测试，禁止第二套 JSON/raw-text parser。
- [x] Markdown structure 只由一个 Java projector 从 `markdown-syntax.v1` 产生，并逐节点保留 syntax node/source span provenance。
- [x] 迁移旧 Markdown `structureNodes` producer/consumer；保留的持久化 structure node 只能是职责不重叠的业务投影。
- [x] 执行旧字段、旧消费者、raw-text extractor 和第二语法解释器精确退出扫描，逐条删除或记录非 Markdown 稳定边界。
- [x] 测试中文一级章与 decimal 子节跨 family 关联。
- [x] 测试原始 node code、depth、parent、previous/next sibling。
- [x] 修复 QueryUnderstanding/history 污染导致的结构意图丢失。
- [x] route/plan/structure resolver 真实消费结构结果并写 O9。
- [x] [用户操作-构建] 在隔离验收 KB 重新上传星联智服手册，等待新 parse task 成功、确认策略并构建新索引。
- [x] [AI 只读验收-构建] 根据操作前水位定位新 document/parse task/artifact/index task，核对 syntax hash/span、结构树、provenance 和冻结 block 155。
- [x] [用户操作-运行时] 在选中新 document 的同一新会话中，按 O8-09 -> O8-10 -> O8-11 的原始多轮顺序执行真实对话。
- [x] [AI 只读验收-运行时] 根据对话前水位记录新 exchange ID，核对 Prompt、回答和 ROUTE/RetrievalPlan O9 的 structure intent/result；首轮新代码运行时结果为 `RUNTIME_BLOCKING_FAIL`。
- [x] 先用目标红灯固化“检索使用 index task，结构导航必须使用同一 document 的 last parse task”，再修复 `KnowledgeRoutePlanner -> ChatPreparationOrchestrator -> StructureNavigationResolver` 的 task lineage；禁止通过去掉 parse-task 过滤制造通过。
- [x] [用户操作-task lineage 修复后复测] 重启后在选中新 document 的同一新会话中重新按 O8-09 -> O8-10 -> O8-11 产生全新 exchange。
- [x] [AI 只读验收-task lineage] 确认 index/parse task 分离和 deterministic adjacency 已生效，同时定位结构 Context 预算排序及 advisor 降级目录意图两个后续断点。
- [x] 建立“advisor 不得降级当前轮确定性目录语法、导航目标正文优先扩展、确定性结构 Context 先于普通 wrapper”目标红灯并完成通用修复；不调整 fusion/rerank/final/citation。
- [x] [用户操作-结构闭环修复后复测] 重启后在选中新 document 的同一新会话中再次按 O8-09 -> O8-10 -> O8-11 产生全新 exchange。
- [x] [AI 只读验收-结构闭环] 确认 O8-10/O8-11 的 deterministic structure Context 已进入 Prompt，同时定位 O8-10 缺少下一节原始标题 Source、模型因 citation 边界回退“资料不足”的最后断点。
- [x] 建立“只有 deterministic navigation target 对应的 original TITLE chunk 可受控成为 Source、普通或派生 TITLE 仍为 Context”目标红灯并完成通用修复；不调整 fusion/rerank/final/citation。
- [x] [用户操作-标题 Source 闭环修复后复测] 重启后在选中新 document 的同一新会话中再次按 O8-09 -> O8-10 -> O8-11 产生全新 exchange。
- [x] [AI 只读验收-标题 Source 候选窗] 确认 O8-10/O8-11 的原始标题已获得 citation identity 并进入 ranking window；第五轮结果仍为 `RUNTIME_BLOCKING_FAIL`，唯一 final policy 因短标题 rerank 分低而按既有预算过滤。
- [x] 建立“deterministic target 的 original/source-authored TITLE 在既有 final budget 内按 target 顺序保留，普通正文、普通/派生 TITLE 不得获得 required provenance”目标红灯并完成通用修复；不得在 ContextExpansion、Prompt 或 citation 层旁路唯一 final policy。
- [x] 增加主链路 fixture，确认 required 标题以低 rerank 分穿过 structure expansion、ranking window、rerank 和 final policy 后进入 `sourceDocuments`，并写入 `INCLUDED_BY_STRUCTURE_NAVIGATION_REQUIREMENT`。
- [x] [用户操作-final policy 修复后复测] 完全停止旧 Java 进程并以最新 class/JAR 启动，在选中同一 document 的同一新会话中再次按 O8-09 -> O8-10 -> O8-11 产生全新 exchange；不需要删除旧数据、重新上传、解析或构建。
- [x] [AI 只读验收-final policy Source 闭环] 第七轮 dialogue `mrlxkzbp1l2grw4p` 已确认 O8-10 chunk `2334598914772059259` 及 O8-11 chunks `2334598914772059215/9219/9223` 的 final decision reason 均为 `INCLUDED_BY_STRUCTURE_NAVIGATION_REQUIREMENT`，manifest 为 `PROMPT_RENDERED_SOURCE/PROMPT_REUSED_SOURCE`，并进入 rendered Source、`explicitCitationEligibleIdentities` 和最终 source snapshot；三轮答案与 citation 同时通过。

最终退出：隔离上传、解析、索引构建、structure artifact、父子树、旧链退出扫描和自动回归均已完成。S02 修复后的第七轮 dialogue `mrlxkzbp1l2grw4p` 继续证明 parse/index task-lineage、目录意图、结构 target/Context、原始标题 Source、required final-policy 和 Prompt manifest 全部生效；O8-09 正确正文、O8-10 `13.2`、O8-11 `10.1/10.2/10.3` 都由合法显式 token 绑定到同轮 rendered Source 并实际归档，Context/未渲染 identity 为 0。S11 全部退出条件满足；后续不得在 S12 重做 S11，只有新的反证才能按 owner 规则重开。

数据库验收：

- 所有结构节点必须满足 `structure_node.parse_task_id = document.last_parse_task_id`，且对应 `nexus_agent_document_task.task_status=3`。
- ROOT 恰为 1。
- 存在 `depth > 1`。
- 通过 structure node 自连接验证 10.1/10.2/10.3 的 parent 是“十、机器人策略设计”。
- 通过 structure node 自连接验证 13.1/13.2/13.3 的 parent 是“十三、上线观察与值班规则”。
- 普通正文不批量为 SECTION。

运行时验收：由用户按 O8-09 -> O8-10 -> O8-11 的原始多轮顺序产生新 exchange；AI 只读确认 Prompt 和答案包含正确一级章、下一小节和 10.1/10.2/10.3，O9 structure intent/result 非空；deterministic target 对应的 source-authored TITLE 必须具有真实 chunk citation identity，以 `INCLUDED_BY_STRUCTURE_NAVIGATION_REQUIREMENT` 进入 rendered Source 和 `explicitCitationEligibleIdentities`，并由答案合法显式 token 绑定进入最终 source snapshot，不能只由 Context 告知模型结构关系。

退出条件：Markdown 只有 `markdown-syntax.v1` 一个语法权威；typed DTO 与 artifact 重放进入同一个 Java projector；旧 Markdown `structureNodes` transport/raw-text 解释链和最后消费者已删除，或仅保留有 `CODE_CONFIRMED` 非 Markdown 责任边界的投影；持久化 structure node 可逐节点追溯 syntax provenance；结构定向测试、精确退出扫描、隔离重建和只读运行验收全部通过。

