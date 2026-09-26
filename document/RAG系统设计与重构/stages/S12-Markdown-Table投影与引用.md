# S12：Markdown Table 投影与引用

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S12 的工程实施合同；具体执行结果、偏差和证据见 [records/S12.md](../records/S12.md)。

## 阶段设计依据

旧解析无法稳定形成 table/row/cell，后续只能把表格当普通文本，无法支持行列查询和精确引用。本阶段从共享语法事实投影表格结构，为 row/cell 建立稳定 identity、source span 和显式引用资格。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：从 S10 共享解析结果构建 table/column/row/cell，并让结构化 table retrieval 路径和 row/cell citation 真实工作。

前置阶段：S11。

核心核验对象（当前类名只用于定位事实，不构成保留要求）：

- Python Markdown parser/table schema。
- `DocumentTableStructureServiceImpl.java`
- `DocumentTableQueryPlanner.java`
- `TableRetrievalChannel.java`（provider 保留时；删除/收拢后核验新的结构化检索 owner）
- `DocumentTableStructureServiceImplTest.java`
- `DocumentTableQueryPlannerTest.java`
- `TableRetrievalChannelTest.java`（provider 保留时；删除/收拢后由等价 contract test 替代）

## Table provider 稳定接口复核

S05 只因当前 table 路径具有受控 query plan、schema 校验和 row/cell citation 而暂时保留独立 provider；本阶段必须基于完成后的结构化产物和真实引用链复核它是否构成稳定项目边界。只有结构化 table plan、受控 schema 以及可回源的 row/cell citation 共同形成普通 chunk retrieval 无法等价替代的深接口时，才允许长期保留。

即使保留，table provider 也继续只消费 S05 的单一 `RetrievalExecutionRequest`，只执行其中的 query/scope/filter 和 controlled table intent；不得拥有第二个 route/scope/query 权威，不得引入双 request 或多层 adapter，也不得恢复按业务词、样例词、文档名或部门名触发的 fallback。若上述稳定边界不成立，应删除/收拢独立 provider，而不是用兼容层维持现状。

核心核验对象和测试矩阵中的当前类名不构成保留条件。若 table provider 被删除或收拢，必须删除或改写 `TableRetrievalChannelTest`，由等价的结构化 table retrieval contract test 保护 plan/schema、单一 `RetrievalExecutionRequest`、row/cell identity 和 citation 边界，并继续完成真实运行验收；不得为满足旧测试矩阵而保留 `TableRetrievalChannel`。

S12 可以在本阶段明确的 table/row/cell 与 explicit-binding 集成边界内修改前置阶段建立或暂时保留的共享代码和测试，包括 table provider、Prompt manifest/binding 消费边界及其 contract test；这不等于重做 S02/S05 或自动重开前置阶段。任何共享修改都必须保持 S02 explicit binding、S03 Source/Context、S05 单一 `RetrievalExecutionRequest` 和 S09 唯一 final policy 等已冻结不变量，并在 `records/S12.md` 记录受影响面和回归证据。

## Row/Cell 显式引用契约

S12 沿用 S02 已完成的唯一 final citation 协议，不新增 table citation adapter、第二个 matcher 或 repair/NLI 分支：

- citation-capable row/cell Source 必须以稳定 evidence identity 进入同轮 Prompt manifest，取得同轮唯一的 canonical 1-based rendered `referenceId`，且 disposition 只能是 `PROMPT_RENDERED_SOURCE` 或 `PROMPT_REUSED_SOURCE`；对应 identity 必须出现在 `EVIDENCE_BUDGET.explicitCitationEligibleIdentities`。
- 答案只有使用合法 ASCII `[n]` token 才能引用 row/cell Source；`CITATION_BINDING / S02_EXPLICIT_CITATION_BINDING_V1` 必须将 token 绑定到该 manifest reference 对应的正确 row/cell evidence identity。
- `CITATION_BINDING.explicitCitationIdentities`、`sourceSnapshotIdentities`、`FINALIZE.sourceSnapshotIdentities` 和 archive source snapshot 必须一致，并按答案中合法 token 第一次出现的顺序去重；不得按 table row 顺序、检索顺序或分数重新排序。
- Context row/cell、未渲染 row/cell、未知或越界 token、缺少稳定 identity 的条目必须 fail closed，不能进入最终 citation；答案没有合法 token 时不得按相似度、NLI 或 table 邻近关系自动补引。

实施清单：

- [x] header/separator/data row、空 cell、转义 pipe、对齐符和相邻标题测试。
- [x] table/column/row/cell 数量和顺序精确映射。
- [x] row/cell 拥有稳定 source identity，可回到当前 task 的源文档位置。
- [x] citation-capable row/cell Source 进入 Prompt manifest 并取得稳定 `referenceId`，且 `explicitCitationEligibleIdentities` 与 rendered/reused row/cell Source identity 对账。
- [x] 通过 `ExplicitCitationBindingServiceTest` 和 table contract fixture 验证合法 `[n]` 绑定正确 row/cell identity、重复 token 按首次出现去重，以及 Context/未渲染/未知/越界 token fail closed。
- [x] table plan 只消费受控 schema，不允许模型生成 SQL。
- [x] 删除按当前样例词触发的 table rule fallback；模型计划非法、越界或不可解析时安全拒绝，不用业务词规则补计划。
- [x] 复核 table provider 的稳定接口，并在 `records/S12.md` 记录结构化 plan/schema/row-cell citation 证据、保留或收拢结论及长期边界/退出条件。
- [x] 按复核结论运行条件测试：provider 保留时运行 `TableRetrievalChannelTest`；删除/收拢时删除或改写原测试，并运行等价的结构化 table retrieval contract test。
- [x] [用户操作] 在隔离 KB 重建星联、差旅、客户数据制度、入职培训 4 份代表文档。
- [x] [用户操作] 执行 Q-TABLE-01 至 Q-TABLE-07 的真实对话。
- [x] [AI 只读验收，首轮失败] 根据操作前水位记录新 document/task/exchange ID，核对 table recalled/accepted/selected、Prompt manifest/referenceId、`explicitCitationEligibleIdentities`、`CITATION_BINDING` token/binding/rejection、FINALIZE 和 archive source snapshot。2026-07-16 cohort 的 table channel 全部为 `0/0/0`，未满足退出条件；根因和证据见 `records/S12.md` 最新小节。
- [x] 为 index task 持久化不可变 `sourceParseTaskId`，index build 与 table retrieval 只按该 lineage 读取 parse-owned block/table/row/cell；历史无 lineage 的 index task fail closed，不从当前 `document.lastParseTaskId` 猜测或回填。
- [x] O9 channel execution 保存 `table-retrieval-lineage.v1` 配置快照与 lineage 拒绝原因；生产者、异步消费者、table consumer 和快照持久化 contract test 全绿。
- [x] [用户操作，修复后复验] 执行 `V20260716__document_task_source_parse_lineage.sql`，使用新代码重启应用，并为四份代表文档重新构建 index task；用户实际选择重新上传并完整解析/构建四份文档，形成了全新的 document/parse/index cohort，未回填旧 index task。
- [x] [用户操作，修复后复验] 使用新 index task 执行 Q-TABLE-01 至 Q-TABLE-07 的新对话。
- [x] [AI 只读验收，修复后链路通过、内容复测未完成] 新 index/parse 血缘、14 个子问题 table recall/accepted、6 个 final-selected row/cell Source、`table-retrieval-lineage.v1`、Prompt manifest、合法 `[n]` binding 和 final/archive identity 守恒均通过；Q-TABLE-04 最终回答遗漏新一线城市 `450 元/晚`，尚未满足本阶段冻结的三档内容预期，完整证据见 `records/S12.md` 最新小节。
- [x] [用户操作，最小内容复测] 在新差旅文档 `2337747606836378550` 上原样重问 Q-TABLE-04，conversation 为 `mrn00czeicw42z1r`，未删除旧数据、未再次迁移/上传/解析/构建。
- [x] [AI 只读验收，最小内容复测后仍未满足内容预期] 新 exchange `2337747709915590463` 严格大于复测水位；table result、manifest、合法 `[7]` row/cell binding 和 final/archive 守恒全部通过，但最终文字再次只写 `500/350` 并遗漏新一线 `450`。重复复现已将剩余问题归到答案完整性/分类解释，而非 table lineage、projection、retrieval 或 citation；不再要求无代码变化的重复人工对话，完整证据见 `records/S12.md` 最新小节。
- [x] [责任归属勘误与阶段退出] 基于同轮 producer/transition/consumer 数据，把 Q-TABLE-04 的 S12 责任明确为结构化 result、Prompt row/cell Source 和 citation 链必须完整覆盖三档；最终自然语言在完整可见且已绑定证据下仍漏写分类的反例冻结为 `ANSWER-SHAPE-TABLE-01`，由 S15/S18 负责，不在 S12 增加答案 adapter 或重解释 citation。

人工交接：AI 完成自动化测试、编译和操作前 document/task/exchange 水位记录后，将 S12 标为 `HUMAN_ACTION_REQUIRED` 并输出迁移、四份文档 index 重建与七个对话的操作清单。用户完成后，AI 恢复 `IN_PROGRESS`，只读查询 task lineage、table/row/cell、检索结果、Prompt、citation、O9 stage snapshot 和日志。

退出条件：

- parse task `P` 是 block/syntax/table/column/row/cell 的产物 owner；这些产物必须全部属于同一成功、有效且同文档的 `P`，table/block 和 row/column/cell lineage 不得跨 task 或跨 document。
- 每个用于聊天检索的成功 index task `I` 必须在创建时冻结 `I.source_parse_task_id = P.id`；request 冻结的是 `I`，index build 和 table retrieval 只能校验 `I -> P` 后读取 `P` 的产物。历史 `source_parse_task_id=NULL`、错误类型、未成功、失效、跨文档或不存在的 lineage 必须 fail closed。
- 运行时不得读取可变的 `document.last_parse_task_id` 猜测、修复或回填 `I -> P`。本次四份全新上传 cohort 中 `table.task_id = document.last_parse_task_id` 只是一项验收时点的数据一致性观测，不能作为长期血缘契约、历史数据回填依据或运行时 fallback。
- 使用 `COUNT(DISTINCT table/column/row/cell.id)` 验证四份文档产物均非零且数量符合原文，避免 join 乘积制造虚高。
- 结构化 table retrieval 路径真实 recall 非零，不再以正文偶然答对代替 table 能力；该能力可以由保留的 provider 或收拢后的稳定 owner 提供，不以 `TableRetrievalChannel` 类继续存在为通过条件。
- citation-capable row/cell Source 具有稳定 identity、可回源，并以 `PROMPT_RENDERED_SOURCE` / `PROMPT_REUSED_SOURCE` 进入 manifest 和 `explicitCitationEligibleIdentities`。
- 答案合法 `[n]` 经 `CITATION_BINDING` 绑定到正确 row/cell identity；final source snapshot 与合法 token identity 按首次出现顺序完全一致。
- Context row/cell、未渲染 row/cell、未知/越界 token 和无稳定 identity 条目均未进入 binding 或 final citation；无合法 token 时没有自动补引。
- table provider 已有明确的保留或收拢结论；对应的原测试或等价 contract test 全绿。若保留，只消费单一 `RetrievalExecutionRequest`，不存在第二 route/scope/query 权威、多层 adapter 或业务词 fallback。
- Q-TABLE-04 的结构化 table result、同轮 Prompt row/cell Source 必须完整覆盖北京属于一线城市 `500 元/晚`、新一线 `450 元`、其他城市 `350 元`，并至少有一个合法 row/cell `[n]` 绑定完成 final/archive 守恒；如果这三档在 result/Prompt/citation 任一层缺失，S12 必须失败。三档已经完整可见且被合法绑定后，最终自然语言仍漏写某一分类属于唯一 Answer Shape owner，按 改造计划与进度 的 `ANSWER-SHAPE-TABLE-01` 路由到 S15，并由 S18 用新 exchange 终验，不构成重开或阻塞 S12 的 table/citation 能力。

本阶段只重建 4 份代表文档，最终 15 份统一重建留给 S17。

