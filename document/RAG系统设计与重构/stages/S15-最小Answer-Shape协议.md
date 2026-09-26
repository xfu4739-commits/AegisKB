# S15：最小 Answer Shape 协议

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S15 的工程实施合同；具体执行结果、偏差和证据见 [records/S15.md](../records/S15.md)。

## 阶段设计依据

旧 expectedEvidenceRoles 没有完整 producer/consumer，既不能约束回答，也会形成失效协议。本阶段只保留能够被 Prompt、显式引用和归档共同验证的最小 Answer Shape，不引入面向验收问题的语义模板。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：消除 Prompt 要求但 Java 丢弃的 `expectedEvidenceRoles`，只在有真实消费价值时保留最小 answer shape。

前置阶段：S14。

核心文件：

- `prompt/document-query-understanding.st`
- `QueryUnderstandingResult.java`
- `QueryUnderstandingService.java`
- `RagPromptAssemblyService.java`
- 对应 tests。

默认策略：先删除失效 `expectedEvidenceRoles`。只有失败测试证明比较、步骤、列表、否定边界或互斥分类/table-row coverage 需要显式 answer shape，才引入最小 `AnswerShapePlan`，并保证 Prompt 与 Java model 一一对应。

S04 转入门禁债务：逐项核验 `answerExpectation`、`NORMAL_QA` 的 producer、consumer，以及 Prompt 输出与 Java model/消费逻辑是否一一对账。失效字段必须从 Prompt、模型和门禁 baseline 删除；只有目标不变量测试证明存在真实消费价值时，才纳入最小 Answer Shape。不得跳过事实核验直接标记为 allowed。

## `ANSWER-SHAPE-TABLE-01` 冻结反例入口

改造计划与进度 已冻结 `ANSWER-SHAPE-TABLE-01 / Q-TABLE-04`。历史 exchange `2337747709915590463` 只用于刻画失败：结构化 table result、Prompt manifest 和合法 `[7]` binding 已完整包含三个相关互斥分类及其值，binding/FINALIZE/archive 也完全守恒，但最终自然语言静默遗漏中间分类。完整数据库证据见 [Q-TABLE-04 最小复测](../records/S12.md#s12-q-table-04-minimal-retest)，责任边界见 [S12 正式退出](../records/S12.md#s12-responsibility-closeout)；该旧 exchange 不得作为 S15 或 S18 的通过证据，也不得把其中的文档名、城市名、金额、cell ID 或答案文本复制进生产 Prompt、运行配置或 fixture。

S15 是“完整可见证据如何组织成完整答案”的唯一 owner。其目标不变量是：当问题语义要求覆盖多个相关互斥分类，且这些分类已经以可引用 Source 进入同轮 Prompt 时，Answer Shape 必须保留所需分类/table-row coverage，不能静默遗漏某一分类；它不重新判定 scope、召回、final evidence、citation eligibility 或 token binding。不得把“覆盖全部 table 行”写成无条件规则，只约束问题语义要求且已进入 Prompt 的相关分类，避免把无关行强塞进答案。

允许能力：组织答案结构、表示 compare/list/steps/negative-boundary/category-table-row-coverage、引用已经入选的 Source Evidence。

禁止能力：重新召回、过滤或重排 evidence，改变 retrieval/final evidence identity，按业务词指定角色，因某角色缺失否定已有原文；禁止文档名、城市名、金额、验收答案、contains/词表、Prompt 样例特化、答案后处理 adapter、第二 matcher、citation repair 或 NLI 最终决策。

实施和运行时验收：

- [x] 如果只删除 `expectedEvidenceRoles`，至少新跑 O8-02、O8-16 和一个明确负边界，验证现象/原因不混、关系组织不反向否认证据。（本阶段新增最小 Answer Shape，条件分支不适用；负边界已纳入五类 schema 和 Prompt/上游不变测试。）
- [x] 将 `ANSWER-SHAPE-TABLE-01` 建成 characterization fixture：使用通用合成分类和数值构造完整、已渲染且可引用的 table Source，冻结“答案静默遗漏中间相关分类”的当前失败；fixture 不得包含验收文档、城市、金额、角色或历史 ID。
- [x] 先建立目标红灯：在问题要求覆盖三个相关互斥分类时，完整 table Source 已进入 Prompt，但缺少任一分类的答案不能满足 Answer Shape contract；同时增加只要求单一分类、存在无关 table 行和没有 table Source 的负例，证明不会无条件枚举整表。
- [x] 如果新增 Answer Shape，增加 compare/list/steps/negative-boundary/category-table-row-coverage 五类受控 schema 测试；Prompt 字段、Java model、producer 和 consumer 必须一一对应，未知/非法 shape fail closed。
- [x] 在相同受控输入和冻结配置下，对比 Answer Shape 前后的 `RetrievalExecutionRequest`、retrieval candidate、final selected Source Evidence、Prompt rendered/reused Source identities、`explicitCitationEligibleIdentities` 和 canonical `referenceId -> identity` 映射；这些上游集合及顺序必须完全相等。Answer Shape 只能约束答案组织，不能重新选择证据或改变 citation eligibility。
- [x] 对每一次答案分别解析合法 `[n]` token：该答案的 `explicitCitationIdentities` 只能是同轮 eligibility 的有序子集，并且必须与 `CITATION_BINDING.sourceSnapshotIdentities`、`FINALIZE.sourceSnapshotIdentities` 和 archive source snapshot identities 按该答案 token 首次出现顺序完全相等。两个不同答案之间的最终 citation identity 集合和顺序允许随其合法 token 改变，不得建立跨答案相等断言。
- [x] 证明 Answer Shape 的 producer/consumer 不直接解析、补写、替换或重排答案中的 `[n]` token，也不改变 binding 算法；模型受 Answer Shape 约束后自然生成不同合法 token 是允许结果。合法 token 仍只由 `CITATION_BINDING / S02_EXPLICIT_CITATION_BINDING_V1` 绑定，Context/未渲染/越界 token 继续 fail closed。
- [x] 对生产代码、Prompt 和配置执行禁止项扫描，确保没有验收问题、文档名、城市名、金额、角色后缀、contains/词表、样例化 Prompt、答案后处理 adapter、citation repair 或 NLI 最终分支。
- [x] 自动退出证据至少包括 `AnswerShapePlanTest`（或等价 policy contract test）、`QueryUnderstandingServiceTest`、`RagPromptAssemblyServiceTest`、`RagChatExecutorTest`、受影响 Java 全量和 package；在 `records/S15.md` 保存目标红灯、转绿数量、上游 evidence/eligibility/referenceId 不变对账、逐答案 citation 守恒和静态扫描结果。
- [x] 自动测试和 package 全绿后，在 `records/S15.md` 动态冻结项目 HEAD、实际运行配置、document/task/exchange watermark，以及本次操作唯一使用的 KB ID/name、document ID、当前成功 index task ID、`indexTask.sourceParseTaskId` 和支持 Q-TABLE-04 的 table ID 或等价 table artifact cohort；不得硬编码或默认沿用 S12 的旧 document/task ID。（HEAD、构建配置、既有水位和精确 cohort 已冻结；用户明确承担真实提问前重启当前项目的责任，并要求省略 AI 运行版本及最终水位复核。）
- [x] 冻结从验收方案读取的 Q-TABLE-04 精确问题文本、一个全新隔离 conversation 中只执行该题的分组方式，以及预期 `exchange_state=2 / status=1`。同时记录应用已经使用目标 HEAD、构建产物和 Prompt 重启的证据，或可等价证明实际运行进程版本的 build fingerprint/version、进程启动时间与启动日志；无法证明运行进程承载当前 S15 代码和 Prompt 时不得进入人工交接。（用户于 `2026-07-18 13:29 +08:00` 明确确认将自行重启当前项目，并要求不再检查 PID、启动时间、fingerprint、健康状态和最终水位；本次交接以该用户确认作为运行版本责任声明。）
- [x] 上述代码、配置、运行版本、KB/document/task/table cohort、水位、问题文本、对话分组、预期状态和后续只读 SQL/O9/log 检查清单齐备后，将 S15 标为 `HUMAN_ACTION_REQUIRED` 并停止；由用户在冻结 KB 中只选择冻结 document，原样执行一次新的 Q-TABLE-04，不需要重建文档。（已进入人工操作等待。）
- [x] 用户返回 conversation/exchange ID 后，将 S15 恢复为 `IN_PROGRESS`。AI 使用 conversation/exchange ID、`exchange_id > watermark`、selected KB/document、冻结 index/source-parse lineage 和 table cohort 共同隔离新 exchange；如果实际 retrieval task、source parse、table artifact 或运行版本与冻结值不一致，该 cohort 无效，不得用同名文档、后来重解析产物、历史 S12 exchange 或自动 fixture 补证。（watermark 后只有 exchange `2343970670850932737`；request、时间、KB/document、index/source-parse/table lineage 均与冻结 cohort 一致。）
- [x] AI 只读核对新 exchange 的 `exchange_state/status`、答案覆盖问题要求的三个相关分类及值、结构化 table Source 与 Prompt manifest 完整、每个合法 token 只绑定同轮 eligible Source、binding/FINALIZE/archive 对该答案内部同序守恒，并检查 Answer Shape/Prompt、O9 和 document/task 水位变化；无关 document/task 水位推进必须记录但不能替换冻结 cohort。（回答明确包含 `500/450/350`；运行时 Answer Shape 为 `COMPARE`；10 个合法 token 全部绑定且 0 拒绝，四个最终 identity 在 binding/FINALIZE/archive 同序；document/task/task-log/table 水位未变。）
- [x] 向 S18 交接 `ANSWER-SHAPE-TABLE-01` 的最终关闭条件。S15 的最小新 exchange 只证明当前代码在现有文档产物上真实生效，不能替代 S18；S18 仍必须在 S17 全量重建后的当前 task、最终固定 HEAD/config 下重新产生全新 Q-TABLE-04 exchange，并作为 48 个 case 的最终关闭证据。（已在 `records/S15.md` 和 `当前阶段与下一步.md` 保留该条件。）

人工交接：S15 只有在自动测试、package、禁止项扫描、运行进程版本证明、具体 KB/document/index/source-parse/table cohort 和 document/task/exchange 水位全部冻结后才能进入 `HUMAN_ACTION_REQUIRED`。真实对话必须由用户通过产品入口执行；AI 不调用聊天接口、浏览器自动化、脚本或数据库写入制造 exchange。用户完成后 AI 只读验收，再依据自动证据和同一冻结 cohort 的真实运行证据决定是否满足阶段退出条件。

退出条件：Prompt 不再输出任何 Java 忽略字段；若存在 Answer Shape，其字段有唯一 producer 和 consumer，compare/list/steps/negative-boundary/category-table-row-coverage 的目标与负例均绿色；完整 Prompt Source 下的问题所需互斥分类不会静默遗漏；Answer Shape 前后的 execution request、retrieval candidate、final selected Source Evidence、Prompt rendered/reused Source、eligibility 和 canonical referenceId 映射不变；每个答案的最终 citation identities 只由该答案合法 token 决定并在 binding/FINALIZE/archive 内部同序守恒，不要求不同答案之间相等；禁止项扫描、自动测试和 package 全绿；运行进程版本与 KB/document/index/source-parse/table cohort 已冻结且可审计，S15 新 Q-TABLE-04 严格位于 exchange watermark 后并通过同 cohort 真实只读验收；已把 S17 重建后再次产生全新 exchange 的最终关闭条件交给 S18。

