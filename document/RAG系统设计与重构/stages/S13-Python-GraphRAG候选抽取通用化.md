# S13：Python GraphRAG 抽取去样例化

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S13 的工程实施合同；具体执行结果、偏差和证据见 [records/S13.md](../records/S13.md)。

## 阶段设计依据

旧 Python 抽取包含当前制度、部门、等级和关系样例，容易把验收数据特征固化为算法规则。本阶段只保留通用 entity/relation/evidence 候选抽取与 lineage contract，不决定业务入库、任务状态或最终关系权威。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：删除 `graph_extract.py` 中当前制度/验收样例规则优先逻辑，让 Python 只输出通用抽取结果和可验证 source quote。

前置阶段：S12。


核心文件：

- `nexus-agent-rag-tools/rag_tools/graph_extract.py`
- `nexus-agent-rag-tools/rag_tools/schemas/graph_extract.py`
- `nexus-agent-rag-tools/tests/test_graph_extract.py`

约束：

- 不因 L3/L4、部门名、角色后缀、记录/存放词自动造边。
- Python 的 `schema.title / schema.sectionPath / schema.explicitAlias` 与 `ner.pattern / ner.spacy / ner.model` 只生产 entity candidates；当前通用 relation LLM 只位于 Java `GraphRagExtractionAdvisor`，Python 不存在 relation LLM producer。
- 模型不可用时返回空、失败或低置信候选，不回退到业务规则事实。
- 每个 relation candidate 必须带可校验 source span/quote。

## Python 输出契约：`graph-extraction-status.v1`

`CODE_CONFIRMED`：唯一 producer 是 `rag_tools.graph_extract._extraction_status`。版本化状态位于 `GraphExtractResponse.metadata.extractionStatus`；`extractorLayers`、`extractorSourceCounts` 和 `candidateSourceCounts` 与它同属一次 Python 响应。Java 只能校验和消费，不能重新解释或修复 Python 状态。

必需字段：

| 字段 | v1 契约 |
| :--- | :--- |
| `schemaVersion` | 必须精确等于 `graph-extraction-status.v1` |
| `status` | 只允许 `completed / completed_with_degradation / empty` |
| `reasonCodes` | 字符串数组，按集合语义消费；v1 已知值为 `SPACY_MODEL_UNAVAILABLE / NER_MODEL_UNAVAILABLE / EMPTY_INPUT / NO_GROUNDED_ENTITY_CANDIDATE / NO_GROUNDED_RELATION_CANDIDATE` |
| `validChunkCount` | Python 实际接受的 chunk 数；有效 chunk 必须有非空 `chunkId`、可抽取文本和非空 evidence text |
| `entityCount` | 必须等于响应 `entities.size` |
| `relationCount` | 必须等于响应 `relations.size` |
| `evidenceCount` | 必须等于响应 `evidences.size` |
| `entityStatus` | `entityCount > 0` 时且仅时为 `available`，否则为 `empty` |
| `relationStatus` | `relationCount > 0` 时且仅时为 `available`，否则为 `empty` |
| `relationReason` | `relationStatus=available` 时必须为空；无有效 chunk 时为 `EMPTY_INPUT`；其余空关系为 `NO_GROUNDED_RELATION_CANDIDATE` |

`metadata.extractorLayers`、`metadata.extractorSourceCounts` 和 `metadata.candidateSourceCounts` 都是 v1 必需字段，不是可有可无的调试信息。`CODE_CONFIRMED`：当前 producer 按下表顺序输出四个 layer；v1 语义要求四个 name 恰好各出现一次，顺序本身不是业务语义。缺失、重复或额外未知 layer name 都不是 v1 输出，S14 必须先校验唯一 name 集合再按 name 取层，不能自行补默认值或依赖数组位置解释 layer。

| layer | `enabled` | 合法 `status` | v1 字段与 reason 约束 |
| :--- | :---: | :--- | :--- |
| `schema` | `true` | 仅 `ready` | `sources` 按集合语义必须精确为 `schema.title / schema.sectionPath / schema.explicitAlias` 且无重复；不得有 `reason` |
| `ner.lightweight` | `true` | 仅 `ready` | `sources` 按集合语义必须精确为 `ner.pattern`；不得有 `reason` |
| `ner.spacy` | `true` | `loaded / not_used / unavailable` | `loaded` 必须带 `modelName` 且不得有 `reason`；`not_used`、`unavailable` 必须带非空 `reason`；不允许 `disabled` |
| `ner.model` | `false / true` | `enabled=false` 时仅 `disabled`；`enabled=true` 时仅 `loaded / unavailable` | 必须带 `provider / modelName / device / autoDownload / loaded`；`disabled` 要求 `loaded=false` 且无 `reason`，`loaded` 要求 `loaded=true` 且无 `reason`，`unavailable` 要求 `loaded=false` 且有非空 `reason`；不允许 `not_used` |

只有 `enabled=true AND status=unavailable` 才构成 unavailable layer。layer 与 top-level `reasonCodes` 必须满足双向守恒：

- `SPACY_MODEL_UNAVAILABLE` 当且仅当 `ner.spacy.status=unavailable`。
- `NER_MODEL_UNAVAILABLE` 当且仅当 `ner.model.enabled=true AND status=unavailable`。
- `EMPTY_INPUT` 当且仅当 `validChunkCount=0`；它与 `NO_GROUNDED_ENTITY_CANDIDATE`、`NO_GROUNDED_RELATION_CANDIDATE` 互斥，但可以与模型 unavailable reason 共存。
- `NO_GROUNDED_ENTITY_CANDIDATE` 当且仅当 `validChunkCount>0 AND entityCount=0`。
- `NO_GROUNDED_RELATION_CANDIDATE` 当且仅当 `validChunkCount>0 AND relationCount=0`；当前 v1 固定 `relationCount=0`，所以它在每个非空输入响应中出现，并可与 `NO_GROUNDED_ENTITY_CANDIDATE` 共存。
- `reasonCodes` 只能包含 v1 已知值且不得重复，消费时按集合语义比较；model unavailable reason 可以与 input/entity/relation reason 组合出现。
- `relationStatus=empty` 时 `relationReason` 必须等于 `EMPTY_INPUT` 或 `NO_GROUNDED_RELATION_CANDIDATE`，并且同名 reason 必须存在于 `reasonCodes`；`relationStatus=available` 时 `relationReason` 必须为空且 `reasonCodes` 不得包含这两个关系空值 reason。

两个 source-count map 必须按响应 `entities + relations + evidences` 中每个对象 metadata 的 source 数组逐项精确重算：同一对象的一项 source 计一次，map 必须与重算结果 key/value 完全相等。空 map 合法；出现的 value 必须为正整数，`0`、负数、小数、字符串或 boolean 都非法。由于同一对象可以有多个 source，禁止把 map value 求和后冒充 entity/relation/evidence 总数。

| map | v1 合法 key | layer 对应关系 |
| :--- | :--- | :--- |
| `extractorSourceCounts` | 仅 `schema`、`ner` | 分别对应 schema candidates 和所有 NER candidates |
| `candidateSourceCounts` | `schema.title / schema.sectionPath / schema.explicitAlias / ner.pattern`，以及 `ner.spacy.<label>`、`ner.model.transformers`、`ner.model.transformers.<label>` | 分别对应 `schema / ner.lightweight / ner.spacy / ner.model`；`<label>` 只能由当前 producer 归一化为非空小写字母、数字或下划线 |

未知 source key、source key 与固定 layer 不对应、map 与对象 metadata 重算不一致，或 source 指向缺失/不可用的 producer layer，均是整个 v1 response 的 contract failure；Java 不能删 key、改 count 或降级兼容。

状态真值表：

| 条件 | `status` | 必需状态与 reason |
| :--- | :--- | :--- |
| `validChunkCount=0` | `empty` | `entityStatus=empty`、`relationStatus=empty`、`EMPTY_INPUT`、`relationReason=EMPTY_INPUT` |
| `validChunkCount>0 AND entityCount=0` | `empty` | `entityStatus=empty`、`NO_GROUNDED_ENTITY_CANDIDATE`；当前无关系时还必须有 `NO_GROUNDED_RELATION_CANDIDATE` |
| `validChunkCount>0 AND entityCount>0 AND 存在 unavailable layer` | `completed_with_degradation` | `entityStatus=available`，并按 layer 包含对应 `*_MODEL_UNAVAILABLE` reason |
| `validChunkCount>0 AND entityCount>0 AND 不存在 unavailable layer` | `completed` | `entityStatus=available`；`completed` 只表示 Python 实体候选抽取完成，不表示 GraphRAG 构建成功 |

当前 S13 producer 的关系边界：

- `CODE_CONFIRMED`：Python 当前不推断关系、不跨 chunk 合并关系，因而 `relations=[]`、`communities=[]`、`relationCount=0`、`relationStatus=empty`。
- `NO_GROUNDED_RELATION_CANDIDATE` 是 Python 的正常空关系事实，不是 Java 构建成功、失败或降级的最终判定；该判定只有 S14 Java owner 可以做。
- 跨 chunk 标题/列表不得制造关系或拼接跨 chunk quote。未来若新增通用 Python relation producer，必须先修改本 owner 契约和目标测试并升级 schema version；每条 relation 必须绑定同一 chunk 中可定位的 quote/evidence。
- 所有返回 entity 必须至少绑定一条 evidence；evidence 的 `chunkId` 必须属于本次有效 chunk，规范化后的 `quoteText` 必须能在该 chunk 的 evidence text 中定位。relation 若未来出现，端点、evidence id、chunk 和 quote 必须满足同等 fail-closed 约束。
- S14 对缺失/未知 version、必需字段缺失、未知 v1 状态/reason、计数不守恒或字段矛盾必须拒绝整个 Python 响应，不能用 advisor 或旧规则静默修复跨语言契约错误。

实施清单：

- [x] 用陌生领域 fixture 替换固化制度规则的旧测试。
- [x] 保留通用 schema parsing、entity normalization 和算法能力。
- [x] 删除制度部门、等级、系统和流程词驱动的主路径。
- [x] 测试 Python 不推断或跨 chunk 合并关系，跨 chunk 标题/列表不得制造关系或 quote。
- [x] 门禁扫描 Python 主链路无样例词驱动规则。

退出条件：Python 全量测试通过；域外 fixture 可抽取 source-grounded entity candidates；当前样例不再靠规则制造事实；无 grounded relation producer 时 `relations=[] / communities=[]`；失败语义满足 `graph-extraction-status.v1` 真值表。


