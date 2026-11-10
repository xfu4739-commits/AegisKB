# S19：SAG Agentic 检索分支

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S19 的工程实施合同；具体执行结果、偏差和证据见 [records/S19.md](../records/S19.md)。

## 阶段设计依据

S00-S18 已把固定 RAG 主链路（RetrievalPlan 一次解释、五 channel 并行、fusion/rerank/唯一 final policy、Source/Context 硬分池、显式 `[n]` citation binding、O9 守恒）收敛完成。本阶段引入 SAG（Search-Augmented Generation）思路作为新增检索分支：由 Agent 在循环中自主决定是否检索、检索什么、检索几次（可多跳改写），而不是每轮强制一次性预检索。

用户已确认的需求决策（2026-11-10）：

- 方向为 Agentic SAG 分支（经典 SAG 思路），不是 SAG2 式 embedding-free 多跳 BM25 检索层改造。
- 新增独立 `ExecutionMode.SAG` 与独立 Executor，不改变现有 RETRIEVAL / REACT_AGENT 分支行为。
- 何时走 SAG 由现有模式决策机制（模型驱动的 route/navigation decision）产出，不新增业务词、词表、文档名或 contains 规则。
- Tavily web 搜索结果仅进入 Context Only 池，不参与 `[n]` 显式引用；不为 web 证据设计新的 Source identity。
- 本期仅后端链路；前端不展示工具调用过程。

### 已确认事实

- `CODE_CONFIRMED`：`ExecutionMode` 现有 `RETRIEVAL / REACT_AGENT / CLARIFICATION` 三值；`ConversationExecutorRegistry` 按 mode 分发到 `RagChatExecutor / ReactAgentExecutor / ClarificationExecutor`。
- `CODE_CONFIRMED`：`ChatPreparationOrchestrator.prepare` 中，OPEN_CHAT 或未选知识库固定产出 `REACT_AGENT`；选定 KB 的文档问答由 `DocumentQuestionRouter.route` 产出 `DocumentNavigationDecision.executionMode`（当前实现只产出 `RETRIEVAL`）。
- `CODE_CONFIRMED`：`ReactAgentExecutor` 基于 spring-ai-alibaba graph `ReactAgent`（bean `businessChatReactAgent`），唯一工具为 `tavily_search`（`FunctionToolCallback` + `TavilySearchTool`），带 `ModelCallLimitHook` / `ToolCallLimitHook` / retry / error interceptor 和 MysqlSaver checkpoint。
- `CODE_CONFIRMED`：`RagRetrievalEngine.retrieve(ConversationExecutionPlan, traceRecorder)` 以冻结 `RetrievalPlan` 编译每子问题唯一 `RetrievalExecutionRequest`，channel 并行 → `HybridFusionService` → `SourceRankingWindow` + `RagRerankService` → `FinalEvidenceSelectionPolicy` → Source/Context 分池，全部在引擎内一次完成。
- `CODE_CONFIRMED`：`RagChatExecutor` 在检索后走 `RagPromptAssemblyService.assemble`（manifest 渲染、`PROMPT_RENDERED_SOURCE`/`PROMPT_REUSED_SOURCE` 资格、`explicitCitationEligibleIdentities` = rendered source identities）→ `ObservedChatModelService.streamText` → 下游 `ExplicitCitationBindingService` 绑定合法 `[n]` token。
- `STRONG_INFERENCE`：在 ReactAgent 循环内直接生成最终答案会使工具输出混入对话上下文，无法满足"最终 citation 只绑定同轮 Prompt manifest rendered source"的不变量；因此 SAG 分支必须两阶段执行（搜索循环与最终生成分离）。

## 唯一工程目标

新增 SAG 执行分支：Agent 在预算内迭代调用 `kb_search`（内部知识检索工具）与 `tavily_search`（web 搜索工具）收集证据，随后把收集到的证据送入现有唯一权威管线（final policy 一次选择 → Prompt manifest → 流式生成 → 显式 citation binding）产出答案。

## 目标链路

```text
ChatPreparationOrchestrator（现有决策机制扩展产出 SAG）
-> SagChatExecutor
   阶段1 搜索循环（ReactAgent graph，预算受控）
     ├─ kb_search：Agent 提供每次调用的查询文本
     │    scope/channel/预算/排序特征仍由冻结 RetrievalPlan 一次解释
     │    每次调用投影为唯一 per-call execution request
     │    执行 channel→fusion→rerank window（不做 final selection）
     └─ tavily_search：现有工具，结果标记 Context Only disposition
   循环终止：模型判定证据充分 / ToolCallLimitHook / ModelCallLimitHook 兜底
-> 阶段2 统一生成（复用现有权威，不新增决策点）
     合并各次调用的 rerank window（按稳定 identity 去重、保序）
     -> FinalEvidenceSelectionPolicy（对合并窗口执行一次，预算来自冻结 Plan）
     -> RagPromptAssemblyService（Source 渲染 [n]；web/Context 证据只渲染为 Context Only）
     -> ObservedChatModelService.streamText
     -> ExplicitCitationBindingService（现有链路）
-> FINALIZE / Archive / O9（现有链路，identity 守恒）
```

## 关键设计决策与不变量

1. **Plan 权威不变**：`RetrievalPlan` 仍只解释一次 hard scope、filter、channel、预算和排序特征。SAG 循环新增的自由度只有"每次调用的查询文本与调用时机"；每次调用必须通过既有 `RetrievalExecutionRequest` 编译契约投影，不得绕过 scope 校验。越界或未知 identity 的候选 fail closed。
2. **final selection 只发生一次**：拆分 `RagRetrievalEngine`，把 per-sub-question 的 channel→fusion→rerank window 阶段暴露为可复用入口；`FinalEvidenceSelectionPolicy` 只在阶段2对合并后的窗口执行一次。禁止 per-call 提前做 final selection 造成双决策。
3. **两阶段生成**：最终答案只能由阶段2的 manifest 渲染 + `ObservedChatModelService` 产出。搜索循环内模型对工具输出的中间推理不得作为最终答案流出（循环阶段的模型文本只作为 thinking/trace 事件，不作为 answer token）。
4. **citation 守恒**：`explicitCitationEligibleIdentities` 必须与阶段2 rendered/reused Source identities 完全相等；web 结果与未渲染候选一律无 `[n]` 资格；答案无合法 token 时 binding/FINALIZE/archive 数组为空且同序。
5. **Web 证据 Context Only**：Tavily 结果以 `CONTEXT_ONLY` disposition 进入 Prompt（复用现有 Context 渲染机制），不进 Source Evidence 池、不进 final policy、不进 archive source snapshot。
6. **预算与降级**：`kb_search` 注册独立 `ToolCallLimitHook`（配置化 run/thread limit）；每次调用带超时与降级 note（复用现有 per-channel 降级语义）；循环耗尽预算时阶段2必须仍能基于已收集证据或空证据兜底回复（`noEvidenceReply`）完成。
7. **模式决策不新增词表**：SAG 只能由现有模型驱动决策（`DocumentQuestionRouter`/QueryUnderstanding 的 decision 输出空间扩展）产出，且仅在存在 KB scope 时可选；OPEN_CHAT/无 KB 保持 `REACT_AGENT`。不读取问题词、文档名、部门名等业务词。

## 实施边界

新增（预估）：

- `rag/model/ExecutionMode.java` 增加 `SAG` 枚举值（修改）。
- `rag/executor/SagChatExecutor.java`：两阶段编排。
- `tool/KbRetrievalTool.java` + `KbRetrievalRequest.java`：内部检索工具。
- `config/` 中 SAG agent bean（或扩展 `ChatAgentConfiguration`）：注册 `kb_search` + `tavily_search`、独立 hooks、SAG system prompt 模板。
- `ChatAgentProperties` 增加 SAG 循环预算配置；`prompt/` 新增 SAG system 模板与 Context Only web 证据渲染段。
- trace：`ConversationTraceStageCode` 增加 SAG 循环/合并阶段码。

修改（预估）：

- `RagRetrievalEngine`：暴露 per-call 检索阶段入口（channel→fusion→rerank window），保持 RETRIEVAL 模式现有调用路径行为不变。
- `DocumentQuestionRouter` / `ChatPreparationOrchestrator`：decision 输出空间加入 SAG（含 trace 快照字段）。
- `RagPromptAssemblyService`：如现有 Context 渲染不能直接承载 web Context Only 证据，做最小扩展；不得改变 Source 资格与 manifest 语义。

明确不做：

- 不改前端、不改 Python 工具箱、不改现有 RETRIEVAL / REACT_AGENT / CLARIFICATION 行为。
- 不实现 SAG2 式 embedding-free 多跳 BM25 检索层。
- 不为 web 结果设计 Source identity 或可引用机制。
- 不引入 compatibility facade 或双执行链；阶段退出前删除全部过渡代码。

## 实施步骤拆分与提交计划

每个部分独立核验成功后形成一个提交，提交信息沿用仓库既有 `type(scope): 中文描述` 格式。每部分内部仍按"红灯 → 最小实现 → 定向绿灯 → 受影响回归"闭环执行，只有绿灯后才提交；提交只 stage 该部分明确列出的文件，严禁 `git add -A`（工作区预存的用户注释修改不属于本阶段）。

| 部分 | 内容 | 核验标准 | 提交信息 |
| :--- | :--- | :--- | :--- |
| P1 | characterization fixture 冻结现状：RETRIEVAL 分支引擎管线行为、executor 注册、final selection 单次语义 | 新增 fixture 与受影响既有测试绿灯，编译通过 | `test(rag): S19-P1 冻结检索链路 characterization 基线` |
| P2 | `RagRetrievalEngine` 阶段拆分：暴露 per-call channel→fusion→rerank window 入口，RETRIEVAL 行为零变化 | P1 fixture、`RagRetrievalEngineTest` 及引擎相关回归绿灯 | `refactor(rag): S19-P2 拆分检索引擎阶段入口` |
| P3 | `KbRetrievalTool` + `KbRetrievalRequest`：per-call execution request 投影、scope 校验 fail closed | 投影一致性、越界 fail closed 单测绿灯 | `feat(rag): S19-P3 新增 kb_search 知识库检索工具` |
| P4 | `ExecutionMode.SAG` + `SagChatExecutor` 两阶段 + SAG agent bean + 预算 hooks + trace 阶段码 + SAG prompt 模板 | SAG 不变量测试绿灯：final selection 单次、循环中间文本不入 answer 流、预算耗尽兜底 | `feat(rag): S19-P4 新增 SAG 两阶段执行分支` |
| P5 | 模式决策接入（`DocumentQuestionRouter`/`ChatPreparationOrchestrator` 输出 SAG）+ web 证据 Context Only 渲染 | 路由决策测试 + citation 守恒测试绿灯（web 不可引用、空 token 空数组、同序守恒） | `feat(rag): S19-P5 接入 SAG 模式决策与 web Context Only` |
| P6 | 全量 Java 测试、Maven package、静态门禁、`git diff --check`，records/S19.md 收尾 | 全部绿灯；随后转 `HUMAN_ACTION_REQUIRED` 等待用户真实验收 | `docs(rag): S19-P6 全量回归与记录收尾`（如有代码修改则拆分为对应 fix/test 提交） |

真实验收（用户执行 SAG 分支聊天、AI 只读核对 trace/O9）在 P6 之后进行，验收通过才满足阶段退出条件。

## 测试与验证

按闭环执行：冻结旧问题 → characterization fixture → 目标红灯 → 最小实现 → 定向绿灯 → 受影响回归 → 编译与静态门禁 → 真实产品验收 → record。

目标不变量测试（至少）：

- `kb_search` 每次调用产生唯一 execution request，scope 与冻结 Plan 完全一致；越界 scope 请求 fail closed。
- final selection 在一次 exchange 中只执行一次，输入为合并窗口；per-call 不产生 `FINAL_EVIDENCE_POLICY` decision。
- 循环内模型中间文本不进入 answer 流；最终答案只来自阶段2生成。
- web 证据渲染为 `PROMPT_RENDERED_CONTEXT`，不出现在 `explicitCitationEligibleIdentities`。
- 答案无合法 `[n]` token 时，binding/FINALIZE/archive source snapshot 数组为空；有 token 时与 rendered source identities 同序守恒。
- 工具预算耗尽/超时/失败时，阶段2仍产出合法答案或空证据兜底，无未捕获异常。

受影响回归：`RagRetrievalEngineTest`、`FinalEvidenceSelectionPolicyTest`、`RagPromptAssemblyService` 相关测试、executor 注册相关测试、Java 全量测试、Maven package、正式 RAG 静态门禁。

## 人工操作边界

自动测试全部通过后，真实验收（选定 KB 的真实聊天产生 SAG 分支 exchange、核对 trace 与 O9 守恒）由用户执行；AI 按流程转 `HUMAN_ACTION_REQUIRED`，记录水位与操作清单，用户完成后只读验收。

## 退出条件

- SAG 分支端到端可用：新 exchange 走 `ExecutionMode.SAG`，完成搜索循环 + 两阶段生成。
- 上述全部不变量测试绿灯，受影响回归与静态门禁通过。
- 至少一轮真实 SAG exchange 验收：scope/Plan/request、final selection 单次、citation 绑定与 O9 守恒合法；模型质量波动只记 `OBSERVATION_ONLY`。
- `records/S19.md` 完整记录命令、退出码、测试数量、实际修改、证据 ID、偏差与未完成项。
