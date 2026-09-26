# S06：薄 Orchestrator 与旧契约删除

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S06 的工程实施合同；具体执行结果、偏差和证据见 [records/S06.md](../records/S06.md)。

## 阶段设计依据

Provider 迁移完成后，旧字段、兼容构造器、pass-through service 和双 request 会继续制造认知负担与潜在双权威。本阶段让 Orchestrator 只负责编排，并删除最后一个旧消费者及其兼容代码。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：让 `ChatPreparationOrchestrator` 只负责编排，并在最后一个消费者迁走后删除旧检索字段和兼容构造器。

前置阶段：S05。

核心文件：

- `rag/service/ChatPreparationOrchestrator.java`
- `rag/model/ConversationExecutionPlan.java`
- `rag/service/DocumentQuestionRouter.java`
- `rag/service/NoEvidenceReplyPolicy.java`
- `rag/service/StructureNavigationResolver.java`
- `ChatPreparationOrchestratorTest.java`
- `DocumentQuestionRouterTest.java`
- `RetrievalContractBoundaryTest.java`

目标流程：

~~~text
load context
-> rewrite
-> understand
-> resolve boundary
-> route candidates
-> assemble RetrievalPlan
-> return
~~~

约束：Orchestrator 不再决定具体 channel、final evidence、structure/table/graph/raptor 特判或业务 no-evidence 文案，不手工 `new` 大量协作者。

删除旧字段、旧接口和兼容构造器时，S05 已建立的执行契约不得退化：

- 每个 `RetrievalExecutionQuery` 仍只编译出一个 `RetrievalExecutionRequest`，不得因删除兼容字段而引入双 request、旁路 request 或第二次 query/scope 解释。
- provider 仍只消费该 `RetrievalExecutionRequest`；语言、gateway 或存储投影不得重新拥有 query、scope、filter、route、channel、window 或 rank feature 决策权。
- RAG_RETRIEVE O9 中 Plan/request 数量保持一一对应，`requestReconciliations[*].differences=[]`。
- enabled provider 每 execution query 恰执行一次，disabled provider 不执行；缺失或重复 enabled provider 继续 fail-fast，不得被 compatibility fallback、默认 provider 或静默去重掩盖。

实施清单：

- [x] characterization tests 固化正常、澄清、无 KB、工具失败路径。
- [x] 下沉领域决策到明确 owner，优先复用已有 owner；确实缺少 owner 时允许建立稳定深接口，但禁止新建平移式 facade。
- [x] 删除已经无读取者的 scope/runtime/retrieval 旧字段。
- [x] 删除兼容构造器和测试中的手工大对象拼装。
- [x] 门禁加入旧字段、旧构造器和旧接口的禁止新增规则。
- [x] 运行保护 S05 契约的固定受影响回归：`RetrievalExecutionRequestTest`、`RetrievalChannelContractTest`、`RagRetrievalEngineTest`、`RagChatExecutorTest`。
- [x] 条件验证 execution request -> gateway 边界：若 `DocumentRetrieveRequestFactory` 经事实核验后保留，运行 `DocumentRetrieveRequestFactoryTest`；若该 factory 被合理删除或收拢，删除或改写原测试，并由等价 boundary/contract test 继续保护 query/scope/filter 的单次无语义投影和 S05 不变量。不得为了保留测试类名而保留 production factory 或 adapter。

退出条件：此时才允许声明 RetrievalPlan 是唯一检索执行契约；旧字段全局无读取者；Orchestrator 测试只验证顺序、委托和失败传播；固定回归与按实际边界选择的 factory/等价 contract test 全绿，并继续证明单一 request、provider 单一消费、Plan/request `differences=[]` 以及 enabled/disabled/缺失/重复 provider 契约均未退化。退出判断保护行为不变量，不要求 `DocumentRetrieveRequestFactory` 或其同名测试继续存在。

