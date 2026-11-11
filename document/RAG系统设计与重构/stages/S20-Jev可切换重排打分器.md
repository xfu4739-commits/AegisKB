# S20：Jev 可切换重排打分器

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S20 的工程实施合同；具体执行结果、偏差和证据见 [records/S20.md](../records/S20.md)（开工时创建）。

## 阶段设计依据

S00-S19 已把固定 RAG 主链路收敛完成：RetrievalPlan 一次解释、五 channel 并行、fusion 只合并算特征、SourceRankingWindow 截窗、RagRerankService 重排打分、FinalEvidenceSelectionPolicy 唯一选择 Source、Source/Context 硬分池、显式 `[n]` citation binding、O9 守恒。S19 新增的 SAG 分支也复用同一权威管线。

本阶段评估并落地一个**可切换、默认关闭、可逆**的 Jev 重排打分后端：在现有 rerank 打分位置，于"交叉编码器（`bge-reranker-base`）"之外新增一个可选的"Jev 决策模型打分器"，两者产出同形状的相关性分数，喂给现有窗口/排序/最终选择权威；任一打分器失败时沿用现有 weighted-hybrid 兜底。

用户已确认的需求决策（2026-11-11）：

- 方向为"只在重排打分后端做一个可切换的 Jev scorer，和现有 weighted-hybrid 兜底并列"，**不是** SAG2 式 embedding-free 多跳 BM25 检索层改造，**不是** Context Decision Layer 的 keep/drop 门控，**不是**路由/模式决策改造。
- Jev 只产出相关性分数特征，不获得任何业务决策权威（scope/route/window/final selection/citation 全部不变）。
- 默认后端保持现有 cross-encoder；Jev 为 opt-in，需显式配置 + 用户授权外部数据出境 + 用户提供 API key。
- 本期仅后端打分链路；不改前端，不改现有 RETRIEVAL / REACT_AGENT / SAG / CLARIFICATION 行为。

### 已确认事实

- `CODE_CONFIRMED`：全仓无 Jev / TypeSafe / Noul / system_one 真实集成（grep 仅命中 `vue/` 前端压缩 JS 与 package-lock 的巧合子串）。
- `CODE_CONFIRMED`：Java 重排唯一 producer 是 `RagRerankService.rerank(query, candidates)`（`RagRerankService.java:36`），经 `RagToolsClient` POST `/rerank`（`RagToolsClient.java:74-81`，readTimeoutMs 默认 10000，`RagToolsProperties.java:16`）；请求 `{query, candidates[{id,text,metadata}], topK}`，响应 `{results[{id, score:Double, rank:Integer}]}`；校验全覆盖 / 唯一 rank>0 / 非空 score（`:133-204`），写回 `RERANK_SCORE`/`RERANK_RANK`/`RERANK_MODEL="rag-tools"`（`:188-192`）。
- `CODE_CONFIRMED`：`RagRetrievalEngine.executeRerank`（`:833-920`）在 rerank 失败时保留 fusion 候选（weighted-hybrid），`markPolicyRerankStatus` FAILED 移除 `RERANK_SCORE`/`RERANK_RANK`（`:922-936`），`log.warn` "保留 weighted hybrid Source 候选继续回答"（`:897`），exchange 继续；成功则 `markUsedChannel(RERANK)`。
- `CODE_CONFIRMED`：`SourceRankingWindow.build(sourceCandidates, plan.getRerankWindow())`（`RagRetrievalEngine.java:340-343`）截窗；`FinalEvidenceSelectionPolicy.select(rerankResult.candidates(), plan)`（`:389`）消费分数 → policyScore → 预算选择 → finalRank；`is_selected=(finalRank!=null)`（`:1594`）。
- `CODE_CONFIRMED`：Python `/rerank`（`main.py:38`）当前只有单一 cross-encoder 后端 `BAAI/bge-reranker-base`（`semantic_model.py:7,55-74`），`score_rerank_pairs` → `_normalize_score` 归一化（`:28-29,100-107`），`RAG_TOOLS_RERANK_MODEL` 环境变量选模型名（`:111`）；**无"多打分后端可切换"抽象**。
- `CODE_CONFIRMED`：rerank 开关/窗口配置 `ChatRagProperties.rerankEnabled`/`rerankCandidateTopK`（受 DB 快照覆盖）、`SystemConfigRegistry`、`RetrievalPlan.rerankWindow`/`rerankRequested` 由 `RetrievalPlanAssembler` 冻结。
- `CONFIRMED`（外部一手）：Jev 是 TypeSafe AI 的 System One 决策模型，不生成自由文本，返回类型化 `Choice`/`Score`/`Noul`(布尔概率)；SAG2 论文（arXiv:2610.22588v1，Yuchao Wu，2026-10-15）标题即《SAG2: Embedding-Free Multi-Hop Retrieval with BM25 and JEV》，正文确认 JEV=TypeSafe Jev、SQLite/BM25 embedding-free、查询时 JEV 判证据相关性、MuSiQue R@10 86.62%。
- `STRONG_INFERENCE`：Jev 精确 API 契约（endpoint/auth、请求 `state`+`questions`、响应字段、SDK 版本 `jev-latest` vs `1.13`/`1.14`、`Noul` vs `Boolean` 命名）来自二手资料且产品早期仍在变化；开工前必须以当时官方文档为准并用契约测试锁定，不得凭博客示例实现。
- `HYPOTHESIS`：Jev 能提升本 KB 重排质量 / 降本降延迟——未经本项目验证，只能驱动可逆 A/B 实验，不得驱动长期或不可逆生产设计；默认后端不变。

## 唯一工程目标

在现有 rerank 打分位置新增一个**可切换、默认关闭、可逆**的 Jev 打分后端：与现有 cross-encoder 产出同形状归一化相关性分数，经现有 `/rerank` 契约返回，喂给不变的 `SourceRankingWindow` → `FinalEvidenceSelectionPolicy` 权威；Jev 失败/超时/低置信/不可用时 fail-closed 降级到现有 weighted-hybrid 兜底，exchange 不中断、O9 守恒不破。用中性 fixture + 真实 exchange 的 A/B 证据判定其价值，不改变任何业务权威。

## 目标链路

```text
（不变）RetrievalPlan 冻结 rerankRequested/rerankWindow
-> SourceRankingWindow.build(cap=rerankWindow)
-> RagRetrievalEngine.executeRerank
   -> RagRerankService.rerank(query, candidates)
      -> RagToolsClient POST /rerank        （Java 权威与契约不变）
         Python /rerank 内部新增可切换打分后端：
           ├─ backend=cross-encoder（默认，现状 bge-reranker-base，行为零变化）
           └─ backend=jev（opt-in，需配置 + key + 出境授权）
                state=query+chunk → Jev 类型化相关性判定(Score/Noul)
                → 映射归一化 [0,1] 分数 → 按分数定 rank
                → 返回同形状 {results[{id,score,rank}]}
                失败/超时/低置信/无 key → SemanticModelUnavailable
      <- 响应校验（全覆盖/唯一 rank/有限 score）不变
   -> 成功：写 RERANK_SCORE/RANK（可加 backend 标识用于 O9 归因）
   -> 失败：现有 weighted-hybrid 兜底（保留 fusion 候选，移除 rerank score/rank）
-> FinalEvidenceSelectionPolicy.select（唯一选择，不变）
-> Source/Context 分池 → Prompt manifest → 生成 → 显式 [n] binding
-> FINALIZE / Archive / O9（全不变）
```

## 关键设计决策与不变量

1. **权威零改动**：Jev 只是 `/rerank` 内部的一个打分后端，产出分数特征。scope/route/channel/RetrievalPlan/SourceRankingWindow/FinalEvidenceSelectionPolicy/Source-Context 分池/Prompt 资格/显式 citation binding/O9 全部不变。Jev 不做 keep/drop 决策、不做 final selection、不碰 citation。
2. **同形状契约**：Jev 后端必须返回与 cross-encoder 完全相同的 `{results[{id,score,rank}]}`：score 归一化 `[0,1]` 有限值、rank 唯一且 >0、全候选覆盖。Java `RagRerankService` 校验逻辑不改；不满足契约即按现有 `INVALID_RESPONSE`/`PARTIAL_RESULT` 失败语义降级。
3. **默认关闭 + 出境门控**：默认 `backend=cross-encoder`，行为与现状逐字段一致。`backend=jev` 仅在显式配置且具备 API key 时启用；cross-encoder 路径绝不向外部发送数据。Jev 路径会把 query+chunk 文本发送到 TypeSafe 外部 API——这是**新增数据出境**，必须由用户显式授权并提供 key，合同与 record 明确记录数据边界。
4. **fail-closed 降级**：Jev 不可用/超时/错误/低置信/无 key/契约不符 → 抛 `SemanticModelUnavailable`（或等价失败），触发 Java 现有 weighted-hybrid 兜底；不得伪造分数、不得静默吞错、不得中断 exchange。降级沿用现有 `RerankExecutionReason`/`failureStage` 语义。
5. **Jev 输出只当噪声特征**：Jev 有官方承认的 jaggedness（计数/算术/日期弱）、"结构化≠正确"。本阶段只取其相关性分数作为排序特征，不用它做版本/时效/精确匹配等业务门控，不用它猜测/修复/补充 citation。
6. **可逆、无 facade**：不新建并行 rerank 链、不新增 Python 路由（仍在 `/rerank` 内切换）、不长期保留双写或兼容门面。若 A/B 证据不支持，阶段退出前删除 Jev 后端、恢复 cross-encoder-only；若支持，保留为 opt-in（默认仍 cross-encoder），是否转默认是后续独立决策。
7. **API 契约先锁定**：实现前以当时 TypeSafe 官方文档为准，用契约测试（mock，无真实出境）锁定 Jev 请求/响应/错误/超时行为与版本；命名（`Noul`/`Boolean`、`jev-latest`/`1.13`/`1.14`）以官方为准，适配层隔离供应商字段，上层只认归一化分数。
8. **A/B 证据驱动**：Jev 价值是 `HYPOTHESIS`。用中性 fixture（不含验收文档名/部门/关系名/样例词）+ 真实 exchange 对比 cross-encoder vs jev 的排序质量/延迟/成本；模型质量波动记 `OBSERVATION_ONLY`，只有本项目权威/不变量违反才记 `BLOCKING_FAIL`。

## 实施边界

新增（预估）：

- Python：`/rerank` 内部可插拔打分后端抽象（backend 选择）；jev 后端适配器（`state`+`questions` 组装、Jev API 调用、`Score`/`Noul` → `[0,1]` 归一化、rank 生成、失败 → `SemanticModelUnavailable`）；backend 配置（如 `RAG_TOOLS_RERANK_BACKEND`，默认 cross-encoder）；Jev API key 配置（环境变量，不入库、不打印）。
- 测试：Jev 后端契约测试（mock API，锁定请求/响应/错误/超时/低置信降级）；backend 切换测试（默认 cross-encoder 行为零变化）；归一化/覆盖/rank 唯一性测试。
- 文档：`records/S20.md`；数据出境边界与授权说明。

修改（预估，最小）：

- Python `semantic_model.py` / `main.py`：把单一 cross-encoder 调用重构为可切换后端，cross-encoder 路径行为不变；`/rerank` 请求响应 schema 不变。
- Java（可选，仅观测）：`RagRerankService` 写回的 `RERANK_MODEL` 或 `ScoreSource` 增加 backend 标识（jev vs cross-encoder）用于 O9 归因；不得改变校验/选择/降级语义。若不改 Java 也能满足归因，则 Java 零改动。

明确不做：

- 不改 scope/route/channel/RetrievalPlan/SourceRankingWindow/FinalEvidenceSelectionPolicy/Source-Context 分池/Prompt 资格/citation binding/O9 守恒。
- 不改现有 RETRIEVAL / REACT_AGENT / SAG / CLARIFICATION 行为；不改前端。
- 不实现 SAG2 式 embedding-free BM25 检索层；不做 Context Decision Layer keep/drop 门控；不做 Jev 路由/模式决策。
- 不新增 Python 路由（仍在 `/rerank` 内）；不引入并行 rerank 链或长期兼容门面。
- 不用 Jev 做业务词/版本/时效/精确匹配门控；不用 Jev 猜测/修复 citation。
- 不在未获用户授权与 key 前发生任何真实外部出境。

## 实施步骤拆分与提交计划

每个部分独立核验成功后形成一个提交，提交信息沿用仓库既有 `type(scope): 中文描述` 格式。每部分内部仍按"红灯 → 最小实现 → 定向绿灯 → 受影响回归"闭环执行，只有绿灯后才提交；提交只 stage 该部分明确列出的文件，严禁 `git add -A`（工作区预存的用户注释/文档修改不属于本阶段）。

| 部分 | 内容 | 核验标准 | 提交信息 |
| :--- | :--- | :--- | :--- |
| P1 | characterization 冻结现状：`/rerank` cross-encoder 行为、`RagRerankService` 校验、`executeRerank` weighted-hybrid 兜底、score→window→final policy→O9 守恒 | 新增中性 fixture 与受影响既有测试绿灯，编译通过 | `test(rag): S20-P1 冻结重排打分链路 characterization 基线` |
| P2 | Python `/rerank` 可插拔后端 + Jev 适配器（opt-in，默认 cross-encoder 零变化）+ 归一化/rank/覆盖 + fail-closed + Jev 契约测试(mock) | 契约/切换/降级测试绿灯；默认路径行为不变；Python 全量测试绿 | `feat(rag): S20-P2 新增可切换 Jev 重排打分后端` |
| P3 | 出境门控 + 配置 + 降级链路集成验证（jev 失败→weighted-hybrid，exchange 继续，O9 守恒）；可选 Java backend 观测标识 | 门控/降级/守恒集成测试绿灯；无 key/未授权不出口；Java 受影响回归绿 | `feat(rag): S20-P3 Jev 出境门控与降级链路验证` |
| P4 | A/B 对比（中性 fixture cross-encoder vs jev）+ Java/Python 全量测试 + package + 静态门禁 + `git diff --check` + records 收尾 | 全部绿灯；随后转 `HUMAN_ACTION_REQUIRED` 等待用户真实验收 | `docs(rag): S20-P4 全量回归与 A/B 证据收尾` |

真实验收（用户提供 Jev API key + 显式授权出境 + 以 `backend=jev` 跑真实 KB 聊天，AI 只读核对）在 P4 之后进行，验收通过才满足阶段退出条件。

## 测试与验证

按闭环执行：冻结旧问题 → characterization fixture → 目标红灯 → 最小实现 → 定向绿灯 → 受影响回归 → 编译与静态门禁 → 真实产品验收 → record。

目标不变量测试（至少）：

- 默认 `backend=cross-encoder` 时，`/rerank` 请求/响应/分数/降级与现状逐字段一致（characterization 保护）。
- `backend=jev` 时返回同形状契约：score∈`[0,1]` 有限、rank 唯一>0、全候选覆盖；不符 → 现有失败语义降级。
- Jev 不可用/超时/错误/低置信/无 key → `SemanticModelUnavailable` → Java weighted-hybrid 兜底，exchange 不中断，`RERANK_STATUS=FAILED` 且移除 score/rank，O9 守恒。
- 未授权/无 key/`backend≠jev` 时绝不发生外部出境（契约测试断言无外呼）。
- Jev 分数只影响排序特征，不改变 final selection 单次语义、Source/Context 分池、citation 资格与守恒、scope/Plan/request 对账。
- A/B：中性 fixture 上 cross-encoder vs jev 排序质量/延迟/成本对比可复现，结论写入 record。

受影响回归：`RagRerankService` 相关测试、`RagRetrievalEngineTest`、`FinalEvidenceSelectionPolicyTest`、`SourceRankingWindow` 测试、O9 守恒测试、Python rag-tools 全量测试、Java 全量测试、Maven package、RAG 静态门禁。

## 人工操作边界

- AI 可自动：单元/集成/契约测试（mock，无真实出境）、编译、静态门禁、只读 DB/日志/O9 对账、中性 fixture A/B。
- 必须用户执行（默认不可 AI 自动）：提供 Jev API key；显式授权私有 KB chunk 文本出境到 TypeSafe 外部 API；以 `backend=jev` 产生真实 exchange 的聊天。
- 进入真实验收前状态转 `HUMAN_ACTION_REQUIRED`，《当前阶段与下一步》与 record 记录：目标 KB/文档/对话分组/精确问题、运行版本、backend 配置、key 来源（不打印值）、出境授权、水位、预期状态与后续只读检查。用户完成后 AI 只读验收，不改数据制造通过。

## 退出条件

- 可切换 Jev 打分后端落地为 opt-in，默认 cross-encoder 行为零变化；上述全部不变量测试绿灯，受影响回归与静态门禁通过。
- 至少一轮真实 `backend=jev` exchange 验收：scope/Plan/request、rerank 分数归因、final selection 单次、citation 绑定与 O9 守恒合法；Jev/模型质量波动只记 `OBSERVATION_ONLY`。
- A/B 证据（中性 fixture + 真实 exchange）写入 `records/S20.md`，明确 Jev 价值结论与 keep-as-opt-in / drop 决策；若 drop 则删除 Jev 后端恢复 cross-encoder-only，无残留 facade。
- `records/S20.md` 完整记录命令、退出码、测试数量、实际修改、证据 ID、数据出境边界与授权、偏差、风险与未完成项。
- 是否把 Jev 转为默认后端是后续独立决策，不在本阶段擅自变更默认。