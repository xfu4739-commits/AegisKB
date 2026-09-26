# AI Harness 项目改造指南（课程版）

## 1. 固定恢复入口

进行 RAG 后端、Python rag-tools、Prompt、检索观测、文档解析、构建或验收工作前，必须按以下顺序恢复上下文：

1. 阅读根级 `AGENTS.md`。
2. 阅读 `document/RAG系统设计与重构/改造计划与进度.md`，确认唯一的当前阶段和状态。
3. 阅读 `document/RAG系统设计与重构/当前阶段与下一步.md`。
4. 只读取《改造计划与进度》指向的当前阶段文件。
5. 只读取该阶段明确列出的源码、测试、记录和验证规则条目。
6. 核对 `git status --short`、当前 HEAD、预存脏文件和阶段状态。

聊天摘要只能帮助定位，不能覆盖《改造计划与进度》中的状态、《当前阶段与下一步》中的接力信息和磁盘源码事实。`records/` 是历史证据，默认不全量进入恢复上下文。

## 2. 总体架构边界

- Java 拥有 RAG 业务主链路：文档与任务状态、知识范围、会话、RetrievalPlan、channel 调度、fusion/rerank 编排、最终证据、Prompt、显式引用、归档和 O9。
- Python 是算法工具箱：Markdown/OCR/layout/table、模型推理、rerank、NER、GraphRAG/RAPTOR 等 Python 生态更成熟的能力。Python 不决定 KB/document scope、route、最终 Source Evidence、回答或业务库写入。
- 一个业务决定只能有一个权威。投影层可以转换协议和存储形状，但不能重新解释 scope、query、filter、route、channel、window、rank feature、final evidence 或 citation。
- 不长期保留 compatibility facade、双 request、双写模型、旧字段或旧消费者。阶段内允许最小编译过渡，阶段退出前必须删除最后一个旧消费者对应的兼容代码。
- 禁止使用验收问题、文档名、部门名、系统名、等级、金额、角色后缀、特定关系名、`contains`、词表、黑名单或 O9 reason 驱动 route、channel、fusion、final evidence 和 citation。Markdown 标题、编号、列表、表格等通用语法、明确 hard scope 和通用质量阈值除外。

## 3. 核心不变量

- `RetrievalPlan` 只解释一次知识范围、查询、过滤、通道、预算和排序特征；Provider 只消费并投影计划。
- fusion 只合并和计算特征；rerank 只排序统一窗口；`FinalEvidenceSelectionPolicy` 只选择 Source Evidence；Context Expansion 只产生 Context Only。
- Source Evidence 与 Context Only 必须物理分池。Context、未渲染 Source、未知或越界 token、没有稳定 identity 的候选必须 fail closed。
- 显式引用绑定是最终 citation 的唯一权威，只解析答案中的合法 1-based ASCII `[n]` token，并且只能绑定同轮 Prompt manifest 的 `PROMPT_RENDERED_SOURCE` 或 `PROMPT_REUSED_SOURCE`。
- `explicitCitationEligibleIdentities` 必须与同轮 rendered/reused Source identities 完全相等；最终 explicit identities 只能是其按合法 token 首次出现顺序形成的有序子集。
- `CITATION_BINDING.sourceSnapshotIdentities`、`FINALIZE.sourceSnapshotIdentities` 和 archive source snapshot identities 必须与最终 explicit identities 完全同序。答案没有合法 token 时，这些最终数组必须为空。
- 不使用相似度、NLI、词法规则或生成模型猜测、修复、补充最终 citation。

## 4. 事实等级与开工审计

关键结论必须标注：

- `CONFIRMED`：由多类证据交叉确认。
- `CODE_CONFIRMED`：由当前源码或测试直接确认。
- `DATA_CONFIRMED`：由数据库、日志或运行产物直接确认。
- `STRONG_INFERENCE`：证据高度支持，但仍缺少一个直接闭环。
- `HYPOTHESIS`：待验证假设。

`HYPOTHESIS` 不得驱动长期或不可逆生产设计。修改生产代码前必须：

1. 找到字段、score、identity 和状态的 producer。
2. 找到聚合、映射、去重、窗口、替换等 transition。
3. 找到真正做业务决策的 consumer。
4. 区分数据现象与代码原因。
5. 检查现有测试是在保护目标行为还是固化旧错误。
6. 先建立 characterization fixture 和目标不变量测试，再修改生产实现。
7. 如果阶段文件与当前源码不一致，先勘误阶段文件并在 record 中记录影响，禁止为了符合旧文档而修改正确源码。

开工输出必须列出：HEAD、预存工作区、已确认事实、文档差异、证据不足项、固化旧错误的测试、拟新增 fixture 和实际修改边界。

## 5. 文档权威

| 文档 | 唯一职责 |
| :--- | :--- |
| `document/RAG系统设计与重构/改造计划与进度.md` | 当前阶段、唯一状态表、依赖、问题归属、完成边界和导航 |
| `document/RAG系统设计与重构/当前阶段与下一步.md` | 最近会话继续点和下一会话提示词；不拥有阶段状态 |
| `document/RAG系统设计与重构/stages/Sxx-*.md` | 当前阶段的问题证据、实施边界、测试、验收和退出条件 |
| `document/RAG系统设计与重构/验证规则.md` | 通用命令、O9 模型、自动测试和人工操作矩阵 |
| `document/RAG系统设计与重构/records/Sxx.md` | 阶段实际执行证据；不能替代状态权威 |
| `document/RAG系统验收与运行/` | 当前有效的项目启动、Python 工具安装和真实验收手册；不维护阶段状态 |

## 6. 状态机和会话边界

阶段状态只允许：`NOT_STARTED`、`IN_PROGRESS`、`HUMAN_ACTION_REQUIRED`、`BLOCKED`、`DONE`、`REOPENED`。

普通阶段生命周期：

```text
NOT_STARTED -> IN_PROGRESS -> DONE
IN_PROGRESS -> HUMAN_ACTION_REQUIRED -> IN_PROGRESS
IN_PROGRESS <-> BLOCKED
```

已经完成的前置 owner 被新证据推翻时：

```text
DONE -> REOPENED -> IN_PROGRESS -> DONE
```

一个会话只执行一个阶段，一个阶段可以跨多个会话。当前阶段未 `DONE` 时不得进入下一阶段；阶段完成后必须停止，下一阶段在新会话开始。发现前置阶段错误时，必须把最早责任 owner 标为 `REOPENED` 并停止当前阶段。

《改造计划与进度》中的 `activeReopen` 覆盖 owner 的完整返工周期；后继阶段必须保持重开瞬间的 baseline 状态。只允许一个 active reopen；若 active owner 又发现更早 owner，按其中的协议移入唯一 `suspendedReopen`。不得从 records、Git 时间或聊天内容反推状态。

## 7. 测试和验证闭环

每个阶段必须遵循：

```text
冻结旧问题
-> characterization fixture
-> 目标红灯
-> 最小实现
-> 定向绿灯
-> 受影响回归
-> 编译和静态门禁
-> 必要的真实产品验收
-> record 证据
-> 阶段退出
```

旧 exchange 只能证明旧问题存在；新代码验收必须使用新代码和新的 task/document/exchange。模型理解、reranker 或答案生成波动，在确定性 scope、Plan/request、证据资格、citation 和 O9 均合法时，只记 `OBSERVATION_ONLY`；只有本项目自己的 producer/transition/consumer 违反明确 authority 或 invariant 时才记 `BLOCKING_FAIL`。

## 8. 真实操作的人机边界

AI 可以运行单元测试、集成测试、characterization fixture、编译、静态门禁和测试隔离数据的自动验证，也可以准备人工操作清单、记录水位，并在用户操作后执行只读验收。

下列真实产品操作必须由用户执行，除非用户在当前任务中另行明确授权：

- 创建或选择验收知识库，上传或重新上传文档。
- 触发真实解析、索引、重建、重试、GraphRAG 或 RAPTOR 构建。
- 执行会产生真实 exchange 的聊天。
- 执行写入真实验收库的初始化脚本。

需要人工操作时，AI 必须先完成自动测试和前置核验，记录代码/config/document/task/exchange 水位，给出精确操作清单，把阶段状态改为 `HUMAN_ACTION_REQUIRED`，更新《当前阶段与下一步》和 record，然后停止。用户完成后恢复 `IN_PROGRESS`，AI 只读验收，不得修改数据制造通过。

## 9. 工作区纪律

- 不自动 commit、push、部署或切换分支。
- 不回滚、覆盖、清理、重新 stage 用户已有修改。
- 不使用旧数据、手工数据库修补或恢复兼容分支制造绿色结果。
- 只修改当前阶段明确允许的范围。
- 收工前运行阶段定向测试、受影响回归、编译/静态门禁、`git diff --check` 和 `git status --short`。
- 每阶段把命令、退出码、测试数量、实际修改、证据 ID、偏差、风险和未完成项写入 `records/Sxx.md`。

## 10. 前端固定入口

进行 `vue/` 下的页面、组件、样式、交互、路由、响应式或可访问性修改前，必须完整阅读根级 `DESIGN.md`。单纯前端任务不读取 RAG 后端阶段目录，不建立 Sxx 状态机，也不把后端改造计划套用到前端。生产前端修改至少在 `vue/` 执行：

```bash
npm run test:unit
npm run check:f03
npm run build
```

修改路由、认证、弹窗、关键工作流、响应式或可访问性时运行受影响的 Playwright；修改全局 Token 或公共组件时运行完整 `npm run test:e2e`。不要修改 `vue-claude/`。
