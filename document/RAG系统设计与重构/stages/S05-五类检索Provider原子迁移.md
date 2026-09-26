# S05：五个 Channel 原子迁移

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S05 的工程实施合同；具体执行结果、偏差和证据见 [records/S05.md](../records/S05.md)。

## 阶段设计依据

即使 RetrievalPlan 已建立，只要各 Provider 仍读取会话计划、运行配置或旧 request，检索语义就仍有多个解释点。本阶段为每个子问题建立唯一 execution request，并将 vector、keyword、table、GraphRAG、RAPTOR 逐个迁移为只执行、不重新决策的 Provider。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：把当前 vector、keyword、table、GraphRAG、RAPTOR 执行集合一次性迁移到由 RetrievalPlan 一次编译的受控 execution request；五个名称是本阶段待迁移的现有执行集合，不预设它们是最终对等的运行时拓扑。

前置阶段：S04。

核心文件：

- `rag/retrieve/channel/RetrievalChannel.java`
- `VectorRetrievalChannel.java`
- `KeywordRetrievalChannel.java`
- `TableRetrievalChannel.java`
- `GraphRagRetrievalChannel.java`
- `RaptorRetrievalChannel.java`
- `rag/service/DocumentRetrieveRequestFactory.java`（待核验删除或收拢，不预设保留）
- `rag/service/RagRetrievalEngine.java`
- `DocumentScopeRetrievalChannelTest.java`
- `RetrievalExecutionRequestTest.java`
- `RetrievalChannelContractTest.java`
- 各 channel test

## 项目内数据流与必要边界

开工审计确认：五个 Channel 类都能执行检索，但旧接口仍可直接接触会话计划或各自构造 request。类的数量不是问题，真正风险是同一 query/scope/filter 在多个边界被重复解释。阶段设计因此不要求把五类能力合并成一个实现类，而要求每个子问题只编译一次 execution request，所有 Provider 只消费该请求。

本阶段目标数据流：

~~~text
RetrievalPlan
-> 每个 execution query 一次编译为 RetrievalExecutionRequest
-> 按 request 中的受控能力执行当前 provider
-> 统一候选流（后续 fusion/rerank/final policy 仍由各责任阶段收口）
-> O9 记录同一 request 及 Plan/request 对账
~~~

不得仅从现有五个 channel 类反推“五份 request + 五次语义解释”的目标设计。Plan 到 execution request 只保留一个业务语义编译点，避免形成 `Plan -> channel request factory -> document request factory -> channel` 的长期 pass-through 链。现有 `DocumentRetrieveRequestFactory` 是待核验对象，不因已存在或列为核心文件就默认保留；若 Java 类型边界或底层 gateway 协议确需 request 投影，可以保留一个无语义、职责稳定的投影边界，但不得重新解释业务语义或继续叠加适配层。

当前项目允许保留的必要偏离及退出条件：

| 偏离 | 已确认原因 | 本阶段边界 | 退出/复核阶段 |
| :--- | :--- | :--- | :--- |
| vector、keyword 暂为两个执行 provider | `CODE_CONFIRMED`：当前 `DocumentKnowledgeService` 暴露独立 vector/keyword gateway，S09 才拥有 fusion/rerank/budget 正式切流 | 两者消费同一个 execution request，不得各自读取配置或重新解释 query/scope | S09 复核是否收口为 统一 hybrid retrieval；S05 不提前夺取 S09 排序权威 |
| table 保持结构化执行 provider | `CODE_CONFIRMED`：当前 table 路径包含受控 query plan、schema 校验和 row/cell citation，不能由普通 chunk retrieval 等价替代 | 只消费 request 的 query/scope/filter/受控 table intent，不新增自然语言规则 | S12 完成 table 投影与引用闭环后复核稳定接口 |
| GraphRAG 保持独立 provider | `CODE_CONFIRMED`：当前 Graph 结果携带 source quote/identity 与 Context Only 分池所需字段，直接照搬单个 KG chunk 会破坏 S03 守恒 | 只消费同一 request；不得在 channel 内增加关系语义或 route 决策 | S07、S14 分别收口运行时语义和构建权威 |
| RAPTOR 保持独立 provider | `CODE_CONFIRMED`：当前 RAPTOR 使用独立树存储并执行 raw chunk/parent/summary source status 下钻，无法由普通 chunk Provider 等价替代 | 只消费同一 request；不得伪造 Source identity | S17 用新构建产物验收；若未来统一索引，删除独立适配而非双写 |

上表是当前已识别的必要适配，不表示后续事实核验不能发现新的语言、存储或业务边界差异。新增会改变主链路或长期保留的 adapter 前，必须先在本表补充已确认事实、责任边界，并说明长期保留理由或过渡退出 owner；普通无语义协议投影只需证明没有形成第二业务权威。

约束：

- channel 包不得 import、接收或读取 `ConversationExecutionPlan`。
- Plan 到 execution request 只编译一次；下游不得解析 history、navigation、query understanding、年份、章节或其他旧字段。
- current-document 和 auto-knowledge 走同一 execution request 接口，只是 scope 值不同。
- 不为每个 channel 创建一套重复业务 request，不保留多层 pass-through factory/wrapper 链；必要的单层无语义协议投影允许存在。
- 不保留新旧双接口适配层作为阶段结果。

实施清单：

- [x] 先完成项目 HEAD、producer/transition/consumer 和数据流复核，再写统一 execution request 与五个现有 Provider 的 contract test。
- [x] request 的 query/scope/filter/topK/weight/timeout 全部来自 Plan。
- [x] 每个子问题只生成一个 execution request；provider 只读取 request，不重新解释 Plan 或旧执行字段。
- [x] disabled channel 无执行记录，enabled channel 每个子问题恰有一次执行。
- [x] O9 保存 Plan 与 request 的逐字段对账结果。
- [x] 删除 channel 内对旧字段的读取和兜底解释。

运行时验收：分别新跑当前文档、自动知识、多文档和 follow-up；实际 channel 集合等于 Plan enabled 集合。scope 必须分两层验收，不能用第一层代替第二层：

1. RAG_RETRIEVE complete snapshot 的 `$.subQuestions[*].references[*].documentId` 验证最终 references 全部落在对应 `$.executionRequests[*].documentScope`。
2. `nexus_agent_chat_retrieval_result` 按 exchange/sub-question/channel 保存的全部 raw retrieval rows 验证每个非空 `document_id` 均落在同一 execution request 的 `documentScope`；零召回 channel 以 request enabled 集合和唯一 `channelTraces` 验证已执行，不能因没有 raw row 判为缺失 provider。

退出条件：`rag/retrieve/channel` 不再依赖 `ConversationExecutionPlan`；每个子问题只有一个受控 execution request；不存在长期兼容接口或多层 pass-through 适配链；Plan/request 对账为零差异；主要实现边界都有已确认事实、责任边界以及长期保留理由或过渡退出 owner。

