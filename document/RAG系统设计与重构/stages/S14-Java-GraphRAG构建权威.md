# S14：Java GraphRAG 构建权威

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S14 的工程实施合同；具体执行结果、偏差和证据见 [records/S14.md](../records/S14.md)。

## 阶段设计依据

真实运行证明 typed relation label 会把“确认”放大成 APPROVES，而多个构建层又分别解释成功、失败和重试。本阶段规定 quote 与 grounded direction 是关系事实权威，typed label 仅作 metadata；Java 统一拥有逐候选校验、持久化、outcome、lease、checkpoint、任务状态和 O9。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：Java 校验并决定 GraphRAG 候选能否入库，收口 advisor、source quote、entity resolution 和失败语义。

前置阶段：S13。

核心文件：

- `GraphRagBuildServiceImpl.java`
- `GraphRagBuildResult.java` 与唯一 outcome/decision policy/model。
- `DocumentAsyncProcessServiceImpl.java`
- `DocumentGraphRagBuildCheckpointServiceImpl.java`
- `GraphRagBuildProperties.java`、`KnowledgeBaseIndexingConfigResolver.java`
- `LlmGraphRagExtractionAdvisor.java`
- `LlmGraphRagEntityResolutionAdvisor.java`
- `GraphRagSearchServiceImpl.java`
- `GraphRagCrossDocumentIndexSupport.java`
- `GraphRagCrossDocumentIndexServiceImpl.java`
- `GraphRagQualityServiceImpl.java`
- 对应 build/search/evaluation/quality/channel tests。

约束：

- Python 是候选工具，不拥有业务库事实权威。
- Java 必须校验 document/task/chunk、quote、entity/relation schema、confidence 和幂等 identity。
- advisor 失败不能把旧制度规则结果当高置信事实。
- relation schema 的解释集中到单一 owner，S07 通用运行时特征不回退为关系名判断。

## S13 跨语言输入契约与 Java 权威

S14 必须以 S13 阶段文件定义的 `GraphExtractResponse.metadata.extractionStatus` / `graph-extraction-status.v1` 为唯一 Python 状态输入，并在调用 advisor 或持久化前把核心入库契约与观测描述分层校验：

- `metadata.extractionStatus` 或 `schemaVersion` 缺失、schema 未知或不等于 v1，`validChunkCount`/实际列表计数非法或不一致，Python 越权返回 relation/community，或 entity/evidence 的 ID、owner、chunk、quote grounding 不成立时，整体 `graphPersistenceOutcome=FAILED`、`reason=FAILED_CONTRACT`，不得调用 advisor 修复、不得持久化、不得标成功。
- `validChunkCount` 必须与 Java 对本轮受控 request 中有效 chunk 的重算结果一致；所有 entity/evidence source chunk 必须属于同一 request。
- `status/entityStatus/relationStatus/relationReason/reasonCodes`、四层 `extractorLayers` 的 name/enabled/status/sources 细节、candidate source metadata 以及 `extractorSourceCounts / candidateSourceCounts` 是 Python producer 的观测描述，不授权候选入库。它们缺失、矛盾或重算不一致时，Java 保留已经通过核心 grounding 的候选，写入 `contractObservationWarnings`，并由唯一 outcome owner 显式标记 `DEGRADED / PYTHON_OBSERVATION_METADATA_MISMATCH`；不得静默忽略，也不得因观测描述噪声丢弃合法候选。
- Python 当前 v1 producer 必须返回 `relations=[] / communities=[]`。出现非空 Python relation/community 说明 producer 行为已越过冻结契约，S14 必须拒绝并要求 owner 升级 schema/version，不能猜测兼容。
- 每个 Python entity 必须有可解析 evidence，evidence id/owner/chunk/quote 必须守恒；任一 orphan、未知 chunk 或不可定位 quote 使对应候选拒绝。计数矛盾属于整包 contract failure，不允许删掉坏项后继续冒充合法 v1 响应。
- `completed` 只证明 Python 有 grounded entity 且没有 enabled-but-unavailable layer；`completed_with_degradation` 只证明 Python 有 grounded entity 且至少一个 enabled layer unavailable；`empty` 只陈述 Python 空结果。三者都不是 Java 构建成功的充分条件。

Java 的 KG transaction 只允许四类 `graphPersistenceOutcome`；它只陈述主 KG 是否提交以及提交了什么，不能同时代表 derived index、观测投影或外围 index task 的状态：

| `graphPersistenceOutcome` | Java 语义 |
| :--- | :--- |
| `SUCCESS` | 至少一条 relation 通过 Java schema、端点、identity、confidence 和同 task quote/evidence 校验；按冻结 task 原子持久化 |
| `DEGRADED` | 只有 grounded entity、Python 观测 metadata 不守恒、Python layer 已降级，或 advisor 失败但仍有已验证 Python entity；保存合法子集并保留 degradation，不能冒充普通成功 |
| `EMPTY` | 核心 contract 合法但最终没有 grounded graph candidate；可以来自正常空结果、advisor candidates 全部被过滤或 advisor 失败，observation 必须用 invocation outcome/reason 区分这些来源 |
| `FAILED` | Python transport/核心 contract、无法隔离到单个候选的 request-level lineage 非法，或 lease lost/KG transaction rollback；不得提交本轮 KG，只有此 outcome 才对应 `NO_COMMIT`。可选 advisor 分支失败不撤销已经验证的 Python entity，也不制造 relation |

组合真值表是 S14 目标测试的最低集合：

| Python v1 结果 | Java advisor 结果 | 目标 `graphPersistenceOutcome` |
| :--- | :--- | :--- |
| `completed` + grounded entities + `relations=[]` | grounded entities/relations/evidence | `SUCCESS` |
| `completed` + grounded entities + `relations=[]` | 明确 empty / not-graphable / disabled | `DEGRADED` entity-only；无 relation/community，不能普通成功 |
| `completed` + grounded entities + `relations=[]` | timeout / exception | `DEGRADED` entity-only；`advisorInvocationOutcome=FAILED`，不得伪造 relation 或普通成功，也不得重跑整条 Python/KG 链路 |
| `completed` + grounded entities + `relations=[]` | 个别 relation evidence 的 quote/chunk-lineage 非法，其他 advisor 候选合法 | 拒绝非法 evidence；该 relation 仍有其他完整证据时可保留，否则连同 relation 拒绝；合法余项继续，不得持久化非法 quote 或 orphan |
| `completed` + grounded entities + `relations=[]` | request-level D/I/chunk lineage 无法验证 | `FAILED`；不得用 Python entity-only、旧 KG 或旧 checkpoint 冒充本轮成功 |
| `completed` + grounded entities + `relations=[]` | advisor candidates 全部被过滤 | `DEGRADED` entity-only；提交 Python grounded entity，advisor observation 记录 `filtered/EMPTY` 和逐候选拒绝原因 |
| `empty` 且 `validChunkCount>0` | advisor candidates 全部被过滤 | `EMPTY`；提交本轮空图并清晰区分正常空结果与 advisor 整体调用失败 |
| 任意结构合法 Python 状态 | 个别 advisor entity/relation/evidence 的 ID/schema/endpoint/self-loop/type/confidence/owner/chunk/quote/predicate/duplicate/limit 非法 | 只拒绝非法候选及其依赖项，继续校验合法余项；最终 outcome 由实际 grounded 剩余图和 Python 状态决定，并记录 rejected candidate，不得把拒绝项入库或隐藏为普通全量成功 |
| `completed_with_degradation` | grounded advisor graph | `DEGRADED` accepted graph；保留 Python layer reason |
| `completed_with_degradation` | empty / not-graphable | `DEGRADED` entity-only 或 `EMPTY`，只由是否仍有 grounded entity 决定 |
| `empty` 且 `validChunkCount>0` | grounded advisor graph | `DEGRADED` accepted graph；Python empty 事实必须保留 |
| `empty` 且 `validChunkCount>0` | empty / not-graphable | `EMPTY` |
| `empty` 且 `validChunkCount=0`、reason 为 `EMPTY_INPUT` | 不得调用 | `EMPTY`；不能用 advisor 为无有效 source 的请求补造图 |
| 任意核心 contract 合法的 Python 状态 | advisor timeout / exception | 有 grounded entity 时 `DEGRADED` entity-only，否则 `EMPTY`；均记录 advisor `FAILED`，不制造 relation |
| 缺失/未知 `schemaVersion`、实际列表 count mismatch、request-level lineage 非法、unexpected Python relation/community | 不得调用 | `FAILED`，reason=`FAILED_CONTRACT`；advisor 不得成为 repair adapter |
| 核心 contract 合法，但 layer/status/reason/source-count 等观测 metadata 矛盾 | 正常按受控条件调用 | 保留 grounded candidate，最终显式 `DEGRADED` 并保存 `contractObservationWarnings` |

## 唯一 Outcome / Orchestration Policy

开工时确认的实现基线事实（实施前快照）：

- `CODE_CONFIRMED`：`GraphRagBuildResult` 当前只有 entity/relation/evidence/community 四个计数，没有 final outcome、reason、persistence disposition 或外围 task disposition。
- `CODE_CONFIRMED`：`GraphRagBuildServiceImpl` 当前在 advisor exception 时保留 Python 结果，空输入和 KB 禁用路径也可调用 `replaceGraph` 后 `markSuccess`；没有统一消费 `graph-extraction-status.v1`。
- `CODE_CONFIRMED`：`DocumentAsyncProcessServiceImpl` 当前只收到计数，随后无条件记录 GraphRAG `COMPLETE` 并继续 typed index/RAPTOR；它无法区分 `SUCCESS / DEGRADED / EMPTY`，只能靠异常识别 `FAILED`。
- `CODE_CONFIRMED`：`replaceGraph` 已将同 task delete + entity/relation/evidence/community insert 放在一个 transaction 内，这是可保留的原子边界；但最终 decision 目前发生在该边界之外且没有统一模型。
- `CODE_CONFIRMED`：当前 build lease 以 `taskId` 组成 Redis key、以随机 UUID 作为 owner token；没有持久化 fencing。`lease-enabled=false` 会记录 `LOCK_SKIPPED` 后继续，lease 丢失也只在 KG 持久化前做一次 renew 检查。该事实要求 S14 用受控并发测试确认 stale writer 是否能发布结果，但不预先证明必须新增 attempt head 或数据库 lease epoch。
- `CODE_CONFIRMED`：当前 `max-attempts` 驱动 `for (attempt=1..maxAttempts)` 完整重跑 Python/advisor/KG；`maxAttempts<1`、`leaseTtlSeconds<1` 和负 backoff 会分别被静默 clamp 到 `1/1/0`。这证明 retry/config 语义必须进入 S14 事实核验和目标红灯，但不能在核验前预设为 projection-repair 专用预算或强制改写现有配置含义。
- `CODE_CONFIRMED`：typed projection 在 `DocumentAsyncProcessServiceImpl` 中每次 GraphRAG 返回后直接调用，没有独立 enable 配置；cross-document service 是无条件 `@Service`，`KnowledgeBaseIndexingConfigResolver` 也只有整体 `graphRagBuildEnabled`，没有 typed/cross-document 开关。当前 cross-document bean 缺失会静默跳过、运行异常只记 warning，typed 为空也直接跳过；S14 目标必须把两者作为 enabled GraphRAG 的正式 component 产生 `SUCCESS/FAILED/按输入证明的 NOT_APPLICABLE`，不能把缺 bean、异常或空列表解释为“配置禁用”。
- `CODE_CONFIRMED`：当前 cross-document `rebuildAll()` 每次读取全量 KG，在同一 transaction 内 `deleteAllDerivedIndexes()` 后重建全局/各 scope 派生数据，并可由不同 D/I build 触发；没有显式 generation/revision。S14 必须先复现或排除 stale rebuild 覆盖，再选择现有事务串行化、锁、generation CAS 或版本化 projection 中的最小方案。
- `CODE_CONFIRMED`：当前 `DocumentAsyncProcessServiceImpl` 对任意 RUNNING task 重入会先调用 `cleanupIndexBuildTaskArtifacts`，其中包含 `graphRagBuildService.deleteByTask` 和 typed/vector/keyword 删除。这可能在新结果可见前删除同 I 已提交 KG 与派生产物，必须以 characterization fixture 验证并消除预删风险；不预设由 attempt head 还是现有 task/transaction owner 承担。

S14 必须建立唯一 application-layer outcome owner，集中解释 Python/advisor、KG transaction、derived projection、observation 和外围 task disposition；目标可以命名为 `GraphRagBuildOutcomePolicy`，也可以由职责等价的现有 service/use-case 落地，但不得增加 pass-through facade。当前 typed index 位于 `GraphRagBuildService` 返回之后，因此开工时必须确认最终编排边界；可以移动边界、收窄当前 result，或复用现有 task/checkpoint 形成等价的单一决策点，不能在 typed/cross-document 结果未知时把四个计数冒充最终成功。

### 规范层级与候选实现

本阶段的硬性要求是可由源码、测试和验收观察的业务不变量，不是预先指定持久化对象或分布式协议。下列名称和形状只保留为问题分析时的 **候选方案**：`GraphRagBuildFinalizationJournal`、`GraphRagBuildDispositionApplyReceipt`、`GraphRagBuildAttemptHead`、`graph-rag-build-observation.v2`、`GraphRagCrossDocumentProjectionHead`、`executeOrResume`、lease epoch/fencing token、immutable active pointer、required/published revision。S14 不得仅因为它们出现在本文就新增表、schema、service、outbox、配置项或兼容层。

新会话必须先审计当前源码、现有 task/checkpoint/task-log/O9/transaction/lease 和索引存储，至少比较“复用并加固现有机制”和“新增专用机制”两种方案。只有满足以下条件之一，候选方案才可升级为实现要求：

- `CODE_CONFIRMED` 证明现有机制无法表达或保护下述硬不变量。
- 目标红灯在更小方案下可稳定复现，且新增机制能以更小、更稳定的接口消除失败。
- 当前项目证据提供可迁移原则，并且适配理由、责任边界和长期保留价值已记录。

类名、字段名、内部 schema、表结构、CAS 位置、事务共置、attempt 编号、repair ordinal 和配置 key 复用方式均由 S14 实施事实决定。职责等价且更简单的实现优先；若阶段文件候选形状与源码事实冲突，必须先勘误本文件并记录影响，不得为了符合候选设计而重写正确源码。

### 正交结果语义

S14 必须区分以下四类事实，不能用一个 count、exception 或 `finalOutcome` 混为“成功”。这些名称是阶段通用语言；生产模型允许使用职责等价的类型和存储形状，但必须能无歧义投影到本表：

| 语义 | 阶段通用值 | 硬性含义 |
| :--- | :--- | :--- |
| KG persistence | `SUCCESS / DEGRADED / EMPTY / FAILED` | 只描述本轮 KG 是否提交以及提交了什么；`FAILED` 对应本轮无 commit 或 rollback |
| derived index | `NOT_APPLICABLE / SUCCESS / FAILED` | 只描述 typed/cross-document add、replace 或 cleanup，不改写 KG 事实 |
| observation | `SUCCESS / PARTIAL / FAILED` | 只描述 checkpoint/task-log/O9 等实际观测结果，不反向决定 KG 是否提交 |
| outer task | `CONTINUE / FAIL_INDEX_TASK / REPAIR_REQUIRED` | 只决定 BUILD_INDEX 继续、终态失败或保持非终态等待修复 |

两个 derived component 的聚合仍遵守最小守恒：任一实际执行 component 失败，aggregate 不能是成功；没有失败且至少一个成功时 aggregate 为成功；都确有理由不适用时才是 N/A。运行时 exception、缺 bean 或静默跳过不能冒充 `NOT_APPLICABLE`。S14 人工目标 config 中 typed/cross-document 都应可用，因此六文档最终验收要求 derived 成功。

KB `graphRagBuildEnabled=false` 时不得调用 Python/advisor，也不得把历史 KG/derived 状态静默当作本轮成功。究竟复用当前 empty replace、执行 cleanup，还是采用现有业务状态表达 disabled，必须由当前数据可见性、旧状态读取路径和目标红灯确认；本文不再预设唯一 `EMPTY/REPLACE_WITH_EMPTY` transaction 形状。S14 人工 KB 必须启用 GraphRAG，disabled 结果不能用于阶段通过。

### 核心失败、恢复与并发不变量

以下行为是硬门禁，具体实现由事实核验决定：

1. Python transport/核心 contract、request-level lineage 非法或 KG rollback/lease lost 时，本轮不得提交 KG，也不得用旧 checkpoint 或旧产物冒充成功。advisor 整体调用失败只结束该可选分支：Python 仍有 grounded entity 则提交 `DEGRADED` entity-only，Python 也为空则提交可观测 `EMPTY`；两者都必须记录 advisor `FAILED`，不得制造 relation、触发完整链路重跑或冒充普通成功。已成功解析且可归属到单个 advisor entity/relation/evidence 的结构、schema、quote、chunk-lineage 或数量上限问题属于未受信候选拒绝，非法候选及其依赖项不得入库，但不自动升级为 terminal package failure。
2. KG 已提交后的 typed/cross-document/observation failure 是 post-commit failure；不得改写为 `FAILED/NO_COMMIT`，不得删除已提交 KG，也不得把外围 index task标成完整成功。
3. `REPAIR_REQUIRED` 时 BUILD_INDEX 保持非终态，不完成、不推进 `lastIndexTaskId`；修复成功或终态失败只能由同一个 outcome owner决定。
4. terminal `CONTINUE/FAIL_INDEX_TASK` 必须能幂等落到 task/stage；重复消息或进程恢复不能重复创建后续 stage、重复删除 KG或永久漏应用 disposition。可以复用现有 task version、checkpoint、事务、outbox，也可以在证据证明必要时增加专用 receipt。
5. 同一 D/I 在受控并发下不能有两个 writer 同时发布 active KG/derived 结果。先用 characterization test 证明当前 Redis lease、task 状态、数据库 transaction/unique key 的实际边界；只有现有边界无法阻止 stale writer 时才引入 attempt head、fencing 或其他更强协调。
6. cross-document stale rebuild 不能覆盖包含更新 KG 的较新 active projection。先确认当前是否只有 global scope、事务隔离和并发触发方式，再在串行化、数据库锁/generation CAS、版本化 projection 等方案中选择最小实现；本文不预设 scope revision 数据模型。
7. retry/reentry 必须幂等使用冻结 I；在新结果原子可见前不得预删同 I 已提交 KG。当前 `cleanupIndexBuildTaskArtifacts` 和完整重试循环必须进入 characterization，但是否保留 `max-attempts` 的 full-retry 语义、增加 targeted-repair 预算或取消自动 retry，由当前源码、运行证据和目标红灯决定，不得在核验前改写配置含义。
8. 未调用 Python/advisor 时不得伪造成功 status、layer、count 或 result；transport failure、invalid response 和正常 empty 必须可区分。可在现有 checkpoint/O9 中增加 invocation facts，也可在证据证明必要时版本化 observation schema，不强制新增 v2 对象。
9. checkpoint、task log、O9 和最终业务状态必须能够按 D/I 关联并解释同一轮事实；任一 projection 不得成为第二个 outcome 决策者。不存在的字段保持 absent/null，不用 0 或旧值冒充已执行事实。

纯理论的 coordinator bootstrap、极端 retry 请求返回细节、尚未确认存在的多 scope 组合和运维级灾难恢复，不作为 S14 开工前置或退出硬门禁。若实现或测试中真实复现，再按责任边界记录风险或补充最小设计。

### 开工必须证实的设计问题

写生产代码前，S14 必须用 `CONFIRMED/CODE_CONFIRMED/STRONG_INFERENCE/HYPOTHESIS` 明确回答：

- 当前哪一处真正决定 GraphRAG build 成功、失败、继续和 pointer；哪些 count/exception 分支正在形成第二解释器。
- 当前 KG transaction、task/checkpoint/task-log/O9 能否承载 durable outcome 和幂等 terminal application；缺口是普通错误路径还是已复现崩溃/并发错误。
- 当前 Redis lease、task 重入和数据库约束能否保证同 D/I 单 active writer；lease disabled 的真实生产可达性和期望行为是什么。
- 当前 `max-attempts/retry-backoff-millis` 的配置兼容性、调用者和运维含义；是否确有必要区分 full retry 与 targeted repair 预算。
- 当前 cross-document `rebuildAll()` 的 scope、事务隔离、触发并发和 active 可见性；最小的 stale-write 防护是什么。
- 当前 disabled、empty、advisor failure 和 post-commit derived failure 在数据库、task、日志和 O9 中分别留下什么事实。
- 当前项目证据支持哪些稳定原则；哪些 journal、checkpoint、锁或恢复能力属于本项目无需复制的商业化/运维复杂度。

开工目标红灯只保护公开行为和上述硬不变量，不以“必须存在某个新类/表/schema”为断言。若更小的现有机制加固能够通过全部红灯和人工验收，应优先采用。

### 候选抽取与 advisor 拒绝边界

`CODE_CONFIRMED`：当前 Java validator 已经能够将 advisor 返回解析为独立 entity/relation/evidence candidate，并对 endpoint、identity、quote 和 lineage 分项判断；历史代码也会局部跳过无法归属的候选。`DATA_CONFIRMED`：真实 cohort 中既出现过单个 self-loop/坏 quote，也存在同包其他合法候选，证明“单候选噪声”和“request-level contract 失败”必须使用不同失败语义。

由此采用逐候选 fail closed：能归属到单个候选的 schema、endpoint、self-loop、type、confidence、owner、chunk、quote、predicate、duplicate 或 limit 错误，只拒绝该候选及依赖项；Python transport、核心 v1 contract、request-level D/I/chunk lineage、KG rollback 或 lease lost 才整包失败。本项目 relation schema 继续拒绝 self-loop，这是已有数据质量边界，不代表整份文档失败。

`2026-07-17` 的 B 真实反例证明，阶段原文把“quote/schema 非法”合并成整包 `FAILED` 过于宽泛：AGENTS.md 和冻结硬门禁要求 v1/quote/lineage fail closed，但没有把单个 relation 的 self-loop 定义为 terminal package failure；S14 前源码也会局部跳过该候选。该处现已按源码事实勘误，不能继续为了符合旧文字让一个模型噪声 relation 拖垮整份 document。

本项目采用逐候选校验设计：已成功解析的 advisor entity/relation/evidence 均由唯一 Java validator 逐候选校验；ID、schema、endpoint、self-loop、type、confidence、owner、chunk、quote、predicate、duplicate 或 limit 非法时，只拒绝该候选及依赖项。同一 advisor 返回中的其余候选继续校验，被拒 relation 的 evidence 必须一起拒绝，不能成为 orphan；rejected count/reason 必须进入现有 advisor observation。quote 是否属于冻结 D/I chunk、source/target/predicate 是否被同一连续 quote 支撑，仍是每个 relation/evidence 的 fail-closed 入库边界：非法 evidence 必须丢弃，无任何合法 evidence 的 relation 必须丢弃，但不得因此拒绝同包其他独立合法候选。全部 advisor candidates 被过滤或 advisor 调用失败时，按 Python grounded entity 是否存在提交 `DEGRADED` 或 `EMPTY`；只有 Python transport/核心 v1 contract、request-level D/I/chunk lineage 无法建立或 KG rollback/lease lost 才整包 fail closed。不得用 LLM correction、业务词 repair、旧 KG 或 count 重判掩盖拒绝。

`2026-07-18 DATA_CONFIRMED`：旧 cohort 中一个 advisor relation 把“主体定义审批等要求”和下一句“规范适用于某对象”拼成了主体对对象执行 `APPROVES`，并在 Q-O6-06 的 GraphRAG path 中诱导了错误答案。原 validator 只证明 source、target、predicate 分别存在于整个连续 quote，不能证明三者位于同一直接动作句段。该反例不是 relation 数量、`DEGRADED`、WARN 或覆盖度不足，而是错误 relation 已进入答案上下文。

最终采用的项目适配仍位于唯一 Java candidate validator：`EXPLICIT_ACTION` evidence 除原有 chunk/quote/source/target/predicate 校验外，必须存在一个由通用句末/分号边界划分的句段同时包含 source、target、predicate；列表换行保持为同一连续清单结构。失败只拒绝该 relation 及其 evidence，并记录 `RELATION_QUOTE_DIRECT_ACTION_NOT_GROUNDED`，同包合法候选继续。Prompt 同步禁止把跨句范围共现升级为强关系。该实现不增加依存句法模型、额外 LLM 调用、业务词规则、Python fallback 或适配层。

本项目持久化的 grounded predicate 自带方向；GraphRAG 检索可以从两个 endpoint 展开，但路径文本必须保持持久化 `source -> target` 的方向语义：正向显示 `--grounded predicate-->`，从 target 反向遍历显示 `<--grounded predicate--`。内部 relation label 不得覆盖原文动作。这是现有 Java 检索 owner 内的必要投影，不新增第二个 relation 决策点。

### 自然语言关系与 typed label 权限收口

`2026-07-18 DATA_CONFIRMED`：新 cohort 的 relation `2343311857227432017` 以同 D/I 合法 quote“数据治理负责人负责确认权限申请是否符合客户数据分级要求”和 `predicateQuoteText=负责确认` 入库，但 advisor 把它映射为 `APPROVES`，并用“确认在制度语境下等价于审批决策”解释；relation `2343311857227432015` 同样把“负责复核”映射为 `RECORDS`。两条 relation 的 endpoint、confidence、同句 predicate 和 quote lineage 都合法，因此既有结构校验无法发现 typed label 的语义放大。Q-O6-05 随后把“负责确认”回答为“履行审批职责”。

`2026-07-18 CODE_CONFIRMED`：当前 Java validator 只校验 relation type 非空/格式、endpoint、confidence、predicate 是否在同一直接动作句段，不证明 `predicateQuoteText` 与 advisor 选择的 `relationType` 语义等价，却把 mapping 记录为 `accepted by generic schema and quote validation`。旧测试 `controlledGraphExtractionAdvisorPreservesGroundedUnfamiliarRelationType` 也把“保留任意 advisor type”固化成目标行为，现按真实反例认定为固化旧错误。回答模型实际收到的 Q-O6-05 Source 只有“负责确认”quote，没有 `APPROVES` 路径，说明图谱 typed label 越权和回答模型语义加强是两个独立边界：前者必须由 S14 收口，后者是 确定性工程链路同样不能消除的生成质量观察，不反推 GraphRAG 构建失败。

`2026-07-18 CODE_CONFIRMED`：本项目回答和 GraphRAG path 真正需要的是 source、target、原文动作、方向、quote 与 lineage；有限 typed predicate 并不是事实成立的必要条件。强制把开放自然语言动作压缩成有限标签会额外制造“确认 -> APPROVES”一类语义放大，因此 typed label 必须降为非权威 metadata。

候选方案比较：

- 继续增加 `CONFIRMS/REVIEWS/VALIDATES/...` 只能扩大有限词表，后续仍会遇到新动作，且会让 Prompt/query planner 继续承担开放词义映射，拒绝。
- 在 Java 增加动作词、同义词或 type-to-predicate 黑白名单，会重新引入本地语义规则并对语言/领域脆弱，拒绝。
- 把全部关系退化为无向、无动作的宽松 description 会丢失同句 quote 已经证明的方向与 D/I lineage，不采用。
- 采用 description-first 的最小项目适配：`predicateQuoteText + evidence quote` 是关系事实唯一权威；advisor `relationType` 变为可缺省、非权威的内部检索标签，只保留在 source metadata；持久化 effective type 只使用 `GROUNDED_ACTION / ASSOCIATED_WITH` 这类内部结构类别。typed chunk、GraphRAG path 和回答 Prompt 只呈现 grounded predicate/quote，保持动作、职责、范围和情态强度；没有 grounded authority marker 的旧关系只能中性显示为 `related`。该方案被选用。

该收口不新增模型调用、NLI correction、Python fallback、业务词规则、配置开关、pass-through facade 或兼容双写。旧 KG 不迁移制造通过；生产代码变化后必须由用户使用新 D/I 重建并重新执行七个独立诊断 conversation。

### Java StringTemplate 运行边界补正

`2026-07-18 DATA_CONFIRMED`：首次 description-first 人工 cohort 的抽取 Prompt 第 68 行包含字面示例 `X <原文动作> Y`。本项目 `PromptTemplateService` 使用 `<` / `>` 作为 StringTemplate 表达式分隔符并启用 `ValidationMode.THROW`，因此模型调用前即发生 `invalid character '原/文/动/作'`。文档 `2343569761423630336 / 2343569761423637720` 的所有 advisor 批次和拆分重试均失败，分别以 index task `2343569761423630372 / 2343569761423637751` 提交 `advisorInvocationOutcome=FAILED`、`relationCount=0` 的 `DEGRADED` entity-only 结果。外围索引按既有 best-effort 语义继续成功，但该 cohort 没有执行关系抽取，必须作废，不能用于 S14 通过。

`2026-07-18 CODE_CONFIRMED`：此前 `LlmGraphRagExtractionAdvisorTest` mock 了 `PromptTemplateService`，Prompt 断言只读取资源文本，因而 `428/0/0` 没有覆盖生产 `StTemplateRenderer.createST -> PromptTemplateService.render -> advisor` 路径。该缺口属于本项目 Java 模板框架边界；源码文本审阅和 mock 测试都不能替代生产模板编译路径。

目标红灯在修改 Prompt 前使用生产 `PromptTemplateService`、真实 classpath 模板和完整运行变量稳定得到 `1 test / 1 error`，异常与线上相同。最终把字面示例改为 `X 【原文动作】 Y`，并增加三层永久保护：所有 `.st` 只允许当前受支持的变量/`if`/`else`/`endif` delimiter token；抽取模板使用生产 renderer 完整渲染；advisor 使用真实 template service 和参数 producer 渲染后才 mock 最终模型网络调用。没有增加运行时 adapter、fallback 或第二套模板语法。

### 名称聚合与 alias 适配边界

`2026-07-17 CODE_CONFIRMED`：当前项目同时保存 primary name、normalized name、source-grounded aliases 和 `entityResolutionCanonicalName`。旧固定字符长度 guard 会漏掉合法短 alias-to-primary 桥接，也会错误合并只有 alias-to-alias 重合的长名称，因此字符长度不能成为 entity resolution 权威。

本项目保留 source-grounded `aliases` 和 `entityResolutionCanonicalName`，因此需要最小防歧义适配，但固定字符数不是稳定规则。候选比较结论如下：

- 固定拒绝 `<3` 的拉丁 alias 会漏掉 `SA` 这类确实命中另一实体 primary name 的合法桥接，拒绝。
- 把所有 alias 与 primary name 视为同等 canonical key，会让两个不同长名称仅因共享 `OA` 或 `ABC` alias 错误合并，拒绝。
- 采用 role-aware anchor：primary name、normalized name 和已持久化的 explicit canonical name 形成 anchor；alias 只有精确命中现存 anchor 时才桥接。显式 alias-to-anchor 可以跨 entity type 消化抽取类型噪声，但没有 alias 证据的短 primary name 不因此自动跨 type 合并；多个不同长名称只有 alias-to-alias 重合而没有 anchor 时保持分离。该方案被选用。

首轮目标红灯在生产修改前精确得到 `8 tests / 2 failures`：两字符 alias 命中另一实体 primary name 未合并，三字符共享 alias 在无 anchor 时错误合并。随后一致性检查又以 `9 tests / 1 failure` 暴露两字符 alias 命中不同 entity type primary name 时仍受旧 cross-type 三字符 guard 阻断。最终实现以归一化 alias name 精确查询 anchor name，删除字符长度对 alias-to-anchor 的决策作用；三组目标测试与原有 `OA` 不误合并、`NQV` alias-to-primary 合并 characterization 共 `9/0/0`。该适配不包含缩写词表、文档名、业务词、模型 correction 或额外模型调用。

## 冻结 Index Task `I` 与不可变 Lineage

GraphRAG 构建权威是调用 `rebuildDocumentGraph(documentId, taskId, chunks)` 时显式冻结的 index task `I = taskId`，不是 `document.lastIndexTaskId`：

- 构建入口必须加载并校验 `I.id`、`I.documentId=D`、`I.taskType=BUILD_INDEX`、业务记录有效。GraphRAG 子阶段执行期间 `I.taskStatus` 应为 `RUNNING`；只有完整 index task 收工验收时才要求 `I.taskStatus=SUCCESS`，不能在子阶段开始前错误要求成功。
- `I.sourceParseTaskId` 必须指向同 document、类型正确、成功且有效的冻结 parse task；这条 lineage 在构建期间不得被 document 可变 pointer 覆盖。
- 传入和重新查询的每个 raw chunk 必须满足 `chunk.documentId=D AND chunk.taskId=I`。Java 不得因 `document.lastIndexTaskId` 已变为 `J` 而读取或替换成 J 的 chunk。
- entity/relation/evidence/community 的 `document_id` 必须为 D、`task_id` 必须为 I；evidence `chunk_id` 必须指向 D/I 的 raw chunk，quote 只能在该 chunk/source span 中验证。
- 构建、retry、lease、checkpoint、删除旧的本 task 派生产物和原子持久化全过程都使用 I。任何时点重新读取 `document.lastIndexTaskId` 只能作为观测，不得替换 I、路由 chunk 或修复 lineage。
- S14 新 cohort 可以记录“验收时 `document.lastIndexTaskId=I`”作为当时一致性观测；后续 pointer 前进不使 I 的历史产物失效，也不能成为长期业务不变量。
- 必须增加负例：I 构建已冻结后把 `document.lastIndexTaskId` 推进到 J，结果仍只能写 D/I、只能引用 D/I chunk；不得错挂 J，也不得从 J 取 quote。

## S07 移交的已确认债务

- `CODE_CONFIRMED`：`GraphRagSearchServiceImpl` 对持久化 entity type `SECTION` 仍有硬编码加分；同一分支被 audit 规则 `RAG-J006 / RAG-J007` 命中并归为 `REFACTOR_REQUIRED_MAIN_CHAIN / S14`。实体类型可以作为 schema 事实，但不得仅凭类型名称改变运行时 GraphRAG 质量分或录取优先级。
- `CODE_CONFIRMED`：`GraphRagCrossDocumentIndexSupport.isQuestionLikeEntityName` 仍被 relation phrase 过滤和 generated entity 判定消费，按“哪些/什么/如何/怎么/是否/有没有”等问法词识别生成实体；相关 finding 已归为 `REFACTOR_REQUIRED_MAIN_CHAIN / S14`。不得以本地问法词表、业务词或 contains 黑名单代替 source/schema/quote 真实性校验。
- S14 必须先用 characterization fixture 冻结上述 producer、transition 和 consumer：同一通用质量下更换为陌生 entity type 不得产生类型名硬加分；生成实体的接受或拒绝必须由来源、schema、identity、quote 与通用质量证据解释，不能由问法词命中单独决定。
- 这两项是 S07 主动保留给 Java GraphRAG 构建与质量权威的已知债务，不得通过恢复 relation-name strong/weak、扩大 Prompt 规则或新增样例 adapter 解决。

实施清单：

- [x] `graph-extraction-status.v1` 缺失/未知、top-level count mismatch、request-level lineage 和 unexpected Python relation/community 的核心 fail-closed 测试；四层 layer、reason、source-count 等观测 mismatch 的合法候选保留与显式降级测试。
- [x] LLM 成功、空、超时的可观测 best-effort 降级和 request-level 非法 lineage 的整包失败测试，以及 relation schema/quote/chunk-lineage 候选级拒绝、合法余项继续且拒绝事实可观测的测试。
- [x] Python `completed / completed_with_degradation / empty` 与 advisor outcome 的组合真值表测试。
- [x] 开工输出 producer/transition/consumer 事实、项目内数据流审计、候选设计比较和目标红灯；未证实的 journal/head/receipt/revision/schema/config 改义保持 `HYPOTHESIS`，不得先写生产实现。
- [x] 唯一 outcome owner 能把 KG persistence、derived、observation 和 outer task 四类事实无歧义区分；外围 service/checkpoint/persistence/O9 不再按 count、exception 或旧状态第二次解释成功。
- [x] derived component 成功/失败/N/A 和 disabled/empty 旧状态处理有行为测试；运行异常、缺 bean 或静默跳过不能冒充 N/A，具体 cleanup/persistence 形状由源码事实决定。
- [x] Python transport/核心 contract、prepare/request lineage 和 KG rollback 的 no-commit 红灯通过；advisor failure 保留合法 Python entity 或提交空图并显式降级，不执行额外模型调用或完整链路 retry。
- [x] KG 已提交后的 derived/observation failure 保留 KG、不得标完整成功、不得推进 pointer；若进入 repair，task 保持非终态，retryable/终态判定只有一个 owner。
- [x] terminal CONTINUE/FAIL 在重复投递和进程恢复下幂等落到 task/stage；测试验证公开状态，不要求预先存在独立 journal、apply receipt 或 outbox。
- [x] checkpoint/task-log/O9 能区分 Python/advisor 未调用、正常 empty、transport/invalid response 和成功；不伪造 status/layer/count/result，具体 schema 由选定实现决定。
- [x] 同 D/I 受控并发测试证明最多一个 active writer；先验证当前 lease/task/transaction/unique key，只有红灯证明不足时才增加 fencing/attempt coordination。
- [x] cross-document 并发测试证明 stale rebuild 不能覆盖较新 active projection；根据确认的 global/多 scope 和存储事实选择最小串行化、锁、generation CAS 或版本化方案。
- [x] retry/reentry 使用冻结 I、重复执行不预删已提交 KG、不继承旧成功冒充本轮结果；`max-attempts/retry-backoff-millis` 是否保留、拆分或改义有配置消费者和目标测试证据。
- [x] orphan entity/relation/evidence 不得入库。
- [x] quote 必须属于当前 task 的真实 chunk/source span。
- [x] `EXPLICIT_ACTION` 的 source、target、predicate 必须由同一直接动作句段或连续清单结构支撑；跨句范围共现只过滤该 relation/evidence，合法余项继续。
- [x] 关系事实采用 description-first 权威：`predicateQuoteText + evidence quoteText` 决定事实，advisor `relationType` 可缺省且只作非权威 metadata；Java 只持久化 `GROUNDED_ACTION / ASSOCIATED_WITH` 结构类型，path、typed chunk 和回答 Prompt 均保留 grounded predicate，旧无 authority marker 关系只显示 `related`。
- [x] 活动 `.st` 模板通过统一 delimiter-token 门禁；GraphRAG 抽取 Prompt 由生产 `PromptTemplateService` 完整渲染，并通过 advisor 参数 producer 到 renderer 的非 mock 边界测试，模板语法错误不得等到真实构建才发现。
- [x] 有向 typed relation 从 target 反向遍历时路径箭头保持持久化 source/target 语义，不把反向遍历伪装成反向事实。
- [x] 冻结 index task I 后 `document.lastIndexTaskId` 前进到 J 的 lineage 负例通过。
- [x] `SECTION` 等 entity type 不再以类型名称硬编码改变 GraphRAG 运行时评分；陌生类型 characterization test 通过。
- [x] generated/question-like entity 不再由本地问法词表单独接受、拒绝或降权；`GraphRagCrossDocumentIndexSupportTest` 覆盖通用来源、schema、identity 和 quote 边界。
- [x] cross-document canonical merge 区分 name/canonical anchor 与 alias 候选；删除固定三字符阈值，允许同 type 或跨 type 的 alias-to-primary 精确桥接，并拒绝无 anchor 的 alias-to-alias 假合并。
- [x] [用户操作] 使用 `2026-07-18` 最终 artifact 在隔离 GraphRAG 验收 KB 重新上传并构建验收方案中的 6 份 GraphRAG 文档；旧 cohort 只作为目标红灯证据。
- [x] [用户操作] 基于新 D/I 按精确原文执行 Q-O6-01 至 Q-O6-07；每题使用独立 conversation，陌生领域关系能力继续由业务无关自动 fixture 验证。
- [x] [AI 只读验收] 按最终 artifact、KB/document、操作时间、`2026-07-18 10:36:01 +08:00` 水位和用户返回 ID 隔离新 cohort，核对 parse/index/GraphRAG stage、KG、quote、检索结果、checkpoint/task log/O9 守恒和七个 O6 exchange。
- [x] [AI 只读验收] 六个 D/I 各自满足 contract、D/I/chunk/quote lineage、候选过滤、持久化和四类 outcome 守恒；relation/relation-evidence 数量只作覆盖度观测，不得用外围 BUILD_INDEX success 或 `lastIndexTaskId` 代替逐文档事实核对。
- [x] [AI 只读验收] 按下述 O6 分级矩阵区分已确认的构建/检索错误、citation authority 违规和非阻断模型输出观察；只有前两类计入 `BLOCKING_FAIL`。

## S14 人工 Cohort 冻结协议

`CODE_CONFIRMED`：当前没有独立的 KG task type；也没有 typed/cross-document 独立 enable 配置。`GraphRagTypedChunkService` 在 GraphRAG 返回后由 `DocumentAsyncProcessServiceImpl` 直接调用，`GraphRagCrossDocumentIndexServiceImpl` 是无条件 `@Service` 且由 build service 调用。因此 S14 enabled GraphRAG 的 cohort 必须把两者冻结为 `componentEnabled=true`；`NOT_APPLICABLE` 只能由 EMPTY/无现存状态等输入事实产生，不能用“缺 bean”或未记录的隐藏开关解释。每份文档的任务链必须按下表冻结，禁止在交接中虚构第三种 task：

| 产物阶段 | 当前类型/阶段 | 验收要求 |
| :--- | :--- | :--- |
| parse task `P` | `DocumentTaskTypeEnum.PARSE_ROUTE(code=1)` | P 属于 document D，最终 `taskStatus=SUCCESS` |
| index task `I` | `DocumentTaskTypeEnum.BUILD_INDEX(code=2)` | I 属于 D，`sourceParseTaskId=P`；GraphRAG 执行时为 `RUNNING`，完整索引收工后才为 `SUCCESS` |
| KG build | I 内 `DocumentTaskStageEnum.GRAPH_RAG(code=10)` | 使用 I 的 raw chunks，写 D/I 的 KG 与 `graphRagBuild` checkpoint/task log/O9；不是独立 task |
| typed graph index | I 内 `GRAPH_TYPED_INDEX(code=11)` | 按最终确认的 success/degraded/empty/disabled policy 执行 projection 或清理；不得遗留被当作本轮 active 的旧 typed 结果。component 结果进入 `derivedIndexOutcome` |
| cross-document index | I 内 GraphRAG finalization component；当前没有独立 task/stage code | 最终 active canonical/relation/community 索引必须包含本轮应见 KG，stale rebuild 不得覆盖较新结果；异常不得只记 warning。component 结果进入 `derivedIndexOutcome` |

AI 进入 `HUMAN_ACTION_REQUIRED` 前必须完成全部自动测试、编译和静态门禁，并在 `records/S14.md` 与 当前阶段与下一步 同时冻结以下内容后停止：

1. 项目 HEAD、完整 `git status --short`、所有预存用户改动，以及 S14 实际变更文件。
2. 最终实际采用的源码/构建产物与 GraphRAG Prompt artifact hash。用户明确自行重启并确认加载该版本时，PID、classpath、启动命令、health 和运行时 bean/effective-config 核对只作可选辅助诊断，不是不可替代的阶段硬门槛，也不得因此反复要求重启。
3. 只冻结会改变本轮 GraphRAG 行为且确有必要区分 cohort 的配置语义，不记录 secret。源码默认值、用户确认的运行版本和新 D/I 足以定位时，不要求枚举全部 advisor/NER/timeout 配置；只有日志或结果显示配置分支异常时才读取相应 effective value。
4. 验收 KB 精确 `id/name`，固定为本轮选择的 `GraphRAG图谱评测知识库`；若名称重复，必须以 ID 为权威。
5. 操作前水位和时间：document、document task、task log、KG entity/relation/evidence/community、typed chunk、cross-document active index 和 exchange 的可用 `MAX(id/version)` 或等价定位信息，以及记录时的带时区时间。S14 最终若复用或新增专用 outcome/attempt/receipt/generation 状态，再按实际 schema 冻结其 key/version/head；不得为满足交接文档而预建对象，也不得遗漏已实际参与本轮决策或 active 发布的权威状态。
6. 每份文档的预期操作：由用户在上述 KB 上传或重新上传、触发 parse/index，并等待 P/I 与 I 内 GraphRAG/typed-index stage 收工。AI 不得通过浏览器、HTTP、脚本或数据库写入代跑。

六份文档精确清单：

| 文件 | 主要构建证据 |
| :--- | :--- |
| `需要的例子演示/O6跨文档图谱-审计证据规范A.md` | AuditTrail 证据规范、grounded relation quote |
| `需要的例子演示/O6跨文档图谱-审计系统别名说明B.md` | AuditTrail alias identity 与负边界 |
| `需要的例子演示/O6多社区排序-生产发布回滚规范A.md` | ReleaseControl community 与关系 evidence |
| `需要的例子演示/O6多社区排序-生产发布回滚别名B.md` | CAB/SRE alias identity 与关系 evidence |
| `需要的例子演示/O6多社区排序-客户数据访问控制规范A.md` | DataAccessGuard community 与关系 evidence |
| `需要的例子演示/O6多社区排序-客户数据访问控制别名B.md` | 数据治理负责人/信息安全部 alias identity 与关系 evidence |

构建完成后，用户必须只选择同一验收 KB，通过实际产品入口建立七个互相隔离的新 conversation，每题各产生一个新 exchange，不能让前题答案成为后题证据。这是 **S14 专用诊断性偏离**：权威验收方案规定 O6“新建一轮对话”，即一个新 conversation 内按 Q-O6-01 至 Q-O6-07 顺序产生七个 exchange；S14 为排除历史答案/追问上下文对 relation retrieval 的干扰，故意采用七个独立 conversation 定位构建问题。该诊断 cohort 不能替代 S18 正式 O6 分组；S18 仍须在完整重建后用一个全新 conversation 顺序重跑七题并产生七个全新 exchange。陌生领域关系能力使用自动 contract fixture，不在真实验收中临场增加没有对应 source 文档的问题：

| Case | 精确问题 | 预期 |
| :--- | :--- | :--- |
| `Q-O6-01` | 审计系统有哪些权限相关要求？ | AuditTrail 权限记录、审批、回收、临时权限延长等 |
| `Q-O6-02` | 审计系统本身是否审批权限或直接回收权限？ | 负边界：不审批、不直接回收 |
| `Q-O6-03` | 如果出现异常权限扩散，AuditTrail 要保留哪些信息，谁负责复核？ | 五类信息和信息安全部复核 |
| `Q-O6-04` | 生产发布控制台 ReleaseControl 和变更评审委员会、值班 SRE 分别是什么关系？ | CAB 批准高风险发布；SRE 执行回滚演练 |
| `Q-O6-05` | DataAccessGuard 和数据治理负责人、信息安全部分别是什么关系？ | DataAccessGuard 校验申请；数据治理负责人确认 L4 范围；信息安全部批准/复核 |
| `Q-O6-06` | ReleaseControl 是否负责 L4 高敏感客户数据访问范围确认？ | 负边界：不是 |
| `Q-O6-07` | DataAccessGuard 是否负责回滚演练和发布窗口管控？ | 负边界：不是 |

每个 S14 诊断 exchange 的预期持久化状态为 `exchange_state=2 AND status=1`。这只表示调用完成，不替代答案、Source、quote、citation 或 O9 判定；任一 case 必须保存独立 conversationId/exchangeId 对应关系，且这些 ID 不得登记为 S18 正式 O6 完成证据。

用户完成后必须返回或允许 AI 只读定位：实际操作开始/结束时间、KB ID/name、六个 document ID、每个 document 的 parse task P/index task I、最终实现实际产生的 build/retry correlation identity 和权威 outcome 状态，以及七个 conversation/exchange ID和用户看到的失败提示。优先使用用户提供 ID；缺 ID 时，AI 只能用“全部操作前水位 + 操作时间 + KB ID + 精确文件名”定位，并要求结果恰好形成无歧义 cohort，否则保持 `HUMAN_ACTION_REQUIRED`，不得按数量或最新时间猜测。若实现确有多个 attempt/version，必须按实际权威指针或 task identity 逐个列出；不得用 `MAX(attempt/version)` 自行发明当前权威。

AI 恢复 `IN_PROGRESS` 后只读验收：

- 校验每个 D/P/I 的类型、状态、`I.sourceParseTaskId=P` 和 KB scope；用户确认最终代码已重启后，以新 D/I、水位、文件名和操作时间隔离本轮，冻结 I 后不再用 `lastIndexTaskId` 替换。
- 按 I 核对 raw chunk、entity/relation/evidence/community、typed graph chunk 的 D/I lineage、orphan、quote 可定位性和 persisted counts；对照 checkpoint/task log/O9 的同一 observation snapshot。
- 按最终实现的业务 task/correlation identity 定位本轮唯一权威 outcome，确认重复投递或 retry 没有两个 active writer，也没有从旧 checkpoint、旧 count 或最大版本猜成功。
- 核对 Python/advisor 的实际调用事实：Python 核心 v1/count/lineage/result 必须守恒，layer/reason/source-count 观测 mismatch 必须形成 warning/degradation；advisor 未调用、失败、正常 empty、filtered 和 accepted 必须可区分。再核对 KG 是否提交、两个 derived component、observation 和 outer task 四类事实；no-commit 无本轮 KG，post-commit failure 保留 KG，repair pending 不推进 pointer。
- 只读确认 cross-document active projection 包含本轮应见 KG，并结合最终实现的锁/generation/version/transaction 证据排除 stale rebuild 覆盖；不要求某个预设 revision 字段存在。
- 只读检查 cross-document/typed index 产物、应用/rag-tools 日志和七个诊断 exchange 的 GraphRAG recall、Source quote、答案与 O9；`DEGRADED/EMPTY` 或外围 index task success 都不能自动判定 O6 通过，必须应用下述逐 D/I 与问题分级矩阵。
- 若用户确认代码/Prompt/关键行为配置、KB scope 或冻结 task lineage 在 cohort 中途变化，本 cohort 失效，不能混用变化前后 ID；不以缺少 PID/health 采样单独判 cohort 失效。

## S14 阶段 PASS / BLOCKING_FAIL 矩阵

`CODE_CONFIRMED`：`graph-extraction-status.v1`、Java outcome 真值表和现有 entity-only 路径都允许“有 grounded entity、没有合法 relation”的结果。因此“每个文档 relation 和 relation evidence 必须非零”不是构建正确性标准；S14 只把 relation 数量作为覆盖度质量观测：

| 文档 | 本 D/I 硬不变量 | 覆盖度观测与 A/B pair 预期 |
| :--- | :--- | :--- |
| 审计证据规范 A | grounded entity/evidence 属于本 A 文档冻结 D/I raw chunk；任何已接受 relation 必须有同 D/I 合法 quote | 观测权限职责 relation 覆盖；与 B 的“审计系统/AuditTrail”canonical identity 对齐，不得拼接 A/B quote |
| 审计别名说明 B | grounded entity/evidence 和任何已接受 relation 均属于本 B 文档冻结 D/I | 观测接收/写入等职责覆盖和负边界；与 A 共享 AuditTrail canonical seed |
| 发布回滚规范 A | grounded entity/evidence 属于本 A 文档冻结 D/I；非法 relation 及其 evidence 不得入库 | 观测校验/批准/执行 relation 覆盖；与 B 的 ReleaseControl/CAB/SRE alias identity 对齐 |
| 发布回滚别名 B | grounded entity/evidence 和任何已接受 relation 均引用本 B 文档 quote | 观测批准/执行/记录职责覆盖，不制造跨文档 relation evidence |
| 数据访问规范 A | grounded entity/evidence 属于本 A 文档冻结 D/I；非法 relation 及其 evidence 不得入库 | 观测校验/确认/批准/复核 relation 覆盖；与 B 的 DataAccessGuard/Owner/信息安全部 alias identity 对齐 |
| 数据访问别名 B | grounded entity/evidence 和任何已接受 relation 均引用本 B 文档 quote | 观测确认/复核/执行职责覆盖，不能用 A 的 evidence 冒充 |

这里的名称和关系只定义人工验收预期，不得进入生产 route、relation type、Prompt 规则或 adapter。entity-only 是允许且必须可观测的 GraphRAG 结果；每条实际接受的 relation/evidence 仍始终属于其 source document 的冻结 I，不要求也不允许合成跨文档 quote。

构建 outcome 到阶段判定：

| 只读事实 | S14 判定 |
| :--- | :--- |
| 任一 D/I `graphPersistenceOutcome=FAILED` | `BLOCKING_FAIL` |
| 核心 contract 合法但得到 `EMPTY`，且 invocation、未提交 relation/community 和 observation 可解释 | 不因计数、advisor failure 或 layer/reason/source-count warning 自动阻断；记录强质量观测，由 O6 真实检索、答案和引用判定功能影响；不得用旧 KG 冒充本轮图 |
| `DEGRADED` 只有 grounded entity/evidence，`relationCount=0` 或 `relationEvidenceCount=0` | 允许 entity-only；记录 relation 覆盖度观测，必须保留 advisor filtered/empty 和逐候选拒绝原因，不得改写为普通 `SUCCESS` |
| `DEGRADED` 的原因是可选 NER unavailable、Python empty、advisor empty/filtered/failed 或 Python observation metadata mismatch | 只要核心 contract/lineage/grounding 合法、warning 与 invocation 可观测且下述 O6 阻断项通过，就不因 degradation 或 relation 数量自动阻断 |
| `DEGRADED/EMPTY` 中出现 schema/count/request-level lineage/grounding 等核心契约缺口 | `BLOCKING_FAIL`；这类事实本应由唯一 outcome owner 判为 `FAILED`，不得用降级掩盖。仅 layer/reason/source-count 观测 mismatch 不属于本行 |
| `SUCCESS` | 只是必要条件；仍须逐 D/I 核对 relation/evidence/quote/identity、derived/observation、O6 Source/citation/O9，不能只看 outcome 通过 |
| 任一实际入库 relation/evidence 存在 orphan、非法 endpoint/self-loop、跨 D/I chunk 或不可定位 quote | `BLOCKING_FAIL`；零 relation 可以接受，错误 relation 不可以接受 |
| unresolved `derivedIndexOutcome=FAILED`、`observationProjectionOutcome=PARTIAL/FAILED` 或 `outerTaskDisposition=REPAIR_REQUIRED` | `BLOCKING_FAIL`；修复成功并保留失败历史后才可重新判定 |
| 外围 `BUILD_INDEX=SUCCESS` 或 `lastIndexTaskId=I`，但任一上述 GraphRAG 不变量失败 | `BLOCKING_FAIL`；外围成功不能替代子阶段验收 |

最终阶段 PASS 要求六个 D/I 均满足本表的 contract、lineage、持久化完整性和状态守恒；entity/relation/evidence/community 数量按实际结果记录，不设置逐文档非零门槛。最终权威状态还必须证明 `derivedIndexOutcome=SUCCESS`、`observationProjectionOutcome=SUCCESS`、`outerTaskDisposition=CONTINUE` 已实际落到外围 task/stage，且完整 index task 最终成功。若发生 retry/repair，必须保留足够事实证明没有混用旧结果、预删已提交 KG或由 stale writer 覆盖；不要求采用预设 attempt head、journal 或 apply receipt。

### Citation authority 与非阻断模型输出观察

`2026-07-18 DATA_CONFIRMED`：最终 cohort 的 Q-O6-02 在正确回答“不审批、不直接回收”的同时输出越界 `[10]`；Q-O6-04 输出越界 `[8]` / `[9]`。explicit citation binding 均以 `OUT_OF_RANGE_REFERENCE_ID` 拒绝这些 token，未把它们写入 source snapshot；七个 exchange 的 Prompt rendered Source、explicit eligibility、binding、FINALIZE 和 archive source identities 全部守恒，`context_only=1 AND is_selected=1` 为 `0`。因此“回答正文残留已拒绝 token”和“非法 token 被绑定到最终 citation”不是同一事实，不能继续合并为一个阻断项。

S14 只对 citation authority 的确定性边界负责：Context 或未渲染 Source 进入 binding/final snapshot、未知或越界 token 被实际绑定、或 binding/FINALIZE/archive identity 不守恒，均为 `BLOCKING_FAIL`。本阶段不伪造一个确定性语义裁判器去证明 citation 与生成句子完全等价；答案明确答反原文的肯定/否定边界仍由人工事实验收阻断。下列模型输出表现只记 `OBSERVATION_ONLY`：

- 回答正文残留已被唯一 binding authority 拒绝的未知或越界 `[n]`，且该 token 没有进入任何最终 source identity。
- 模型提到 Prompt 中 Context Only 所含但与原文一致的信息，Context 仍无 reference ID、未被升级为 Source、未进入 binding/final snapshot，且回答核心结论没有因此变成错误事实。
- citation identity 合法绑定同轮 rendered/reused Source，但引用覆盖不完整、复杂关系回答保守或自然语言表达不够完整。
- KG、GraphRAG path 和 rendered Source 都正确时，回答模型对原文动作做语义加强，或综合出原文未明说的上下游关系；这是 生成链路仍然依赖模型、没有确定性语义裁判的质量风险。

`OBSERVATION_ONLY` 是验收分类，不是 改造计划与进度 阶段状态，也不创建实现任务：不因此修改生产代码、Prompt 或 citation repair，不要求重建文档/重跑当前 cohort，不移交 S15。S18 对这四类表现只按 case 统计数量、原因和用户可见影响并作说明，不把它们计入 `BLOCKING_FAIL`，也不据此重开 S14/S15。错误 relation/path 进入检索或答案、明确答反原文的肯定/否定边界，仍为 `BLOCKING_FAIL`；但在 KG/path/Source 正确时，Q-O6-05 中模型自行的“确认 -> 审批”语义加强和上下游关系综合按用户确认口径只作观察。

O6 诊断问答分级：

| Case | 阻断要求 | 可记为 `OBSERVATION_ONLY` 的范围 |
| :--- | :--- | :--- |
| `Q-O6-01` | AuditTrail canonical 或权限相关核心事实错误，或错误 Source 被实际绑定为支持依据时为 `BLOCKING_FAIL` | 核心答案正确时，Context 中正确细节被提及、已拒绝 token 残留、引用覆盖不完整或自然语言质量不足 |
| `Q-O6-02` | 必须保持“不审批、不直接回收”负边界；反向事实或错误 citation 被实际绑定为核心依据时为 `BLOCKING_FAIL` | 核心否定有合法 Source 支持时，正文残留已拒绝 token、额外正确背景或引用不完整 |
| `Q-O6-03` | 五类保留信息或信息安全部复核答错，或已绑定 citation 不支持这些核心结论时为 `BLOCKING_FAIL` | 不改变核心结论的额外背景、已拒绝 token 或措辞质量 |
| `Q-O6-04 / Q-O6-05` | 错误 relation/path 进入检索或答案、明确答反原文的肯定/否定边界、错误 citation identity 被实际绑定，或 O9 identity 不守恒时为 `BLOCKING_FAIL` | KG/path/Source 正确时的模型语义加强或上下游综合，以及复杂关系覆盖不足、保守拒答、Context 正确信息被提及、已拒绝 token 残留或自然语言质量不足 |
| `Q-O6-06 / Q-O6-07` | 给出与原文相反的职责关系，或错误 citation 被实际绑定为核心依据时为 `BLOCKING_FAIL` | 核心否定正确且有合法 Source 支持时，保守表达、复杂覆盖不足、Context 正确信息被提及或已拒绝 token 残留 |

所有七题共同适用权威底线：错误 relation/path、明确答反原文的肯定/否定边界、错误 citation identity 实际绑定、Context/未渲染 Source 进入最终 citation，或 O9 identity 不守恒一律 `BLOCKING_FAIL`。仅出现在回答正文、已被 binding authority 拒绝且未进入最终 identities 的 token，不再称为 unsupported final citation；Context Only 越界只指它被赋予引用资格、进入 binding/final snapshot，不能把“模型看见并提到正确 Context 信息”当作可确定检测的 citation authority 失败。KG/path/Source 正确时的模型语义加强或关系综合同样不反推 S14 构建失败。S14 进入 `DONE` 必须在排除上述非阻断观察后 `BLOCKING_FAIL=0`；`OBSERVATION_ONLY` 可以保留，但不能伪装成已验证的高级 completeness。

状态子流为 `IN_PROGRESS -> HUMAN_ACTION_REQUIRED -> 用户完成上传/重建/对话 -> IN_PROGRESS（AI 只读验收）-> DONE`。本轮人工 cohort 和 AI 只读验收已完成，用户已确认剩余模型生成风险不修复且只作观察；S14 按该口径完成，不需要再次上传、重建或对话。

退出条件：S14 开工事实核验和项目内数据流审计完成，所有长期实现形状均由 `CODE_CONFIRMED` 或目标红灯支持；未证实的 journal/head/receipt/revision/schema/config 改义没有进入生产代码。唯一 outcome owner 能区分 KG persistence、derived、observation 和 outer task，不存在按 count/exception/旧状态形成的第二解释器。Python transport/核心 v1 contract、request-level D/I/chunk lineage 和 KG rollback/lease lost 整包 fail closed；layer/reason/source-count 等观测 mismatch 保留合法 grounded candidate 并显式降级，advisor 整体失败按 Python 是否有 grounded entity 提交 `DEGRADED` 或可观测 `EMPTY`。已解析 advisor entity/relation/evidence 逐候选 fail closed，非法候选及依赖项不入库。单个 advisor schema/endpoint/quote 噪声被拒绝且可观测，不能入库、产生 orphan 或阻断同包合法余项。no-commit 零本轮 KG，post-commit failure 保留已提交 KG且不冒充完整成功；repair pending 不完成 task、不推进 pointer；terminal disposition 在重复投递/恢复下幂等落地。同 D/I 受控并发最多一个 active writer，cross-document stale rebuild 不能覆盖较新 active projection，retry/reentry 不预删已提交 KG或复用旧成功。entity/relation/evidence/community 与 D/I/raw chunk/quote 守恒，无 orphan，陌生领域 fixture 通过。六个 D/I 逐一满足核心 contract、lineage、持久化完整性和 outcome 守恒，relation/relation-evidence 数量只作覆盖度观测；最终 derived/observation 成功、outer task 继续且完整 index task 成功，S14 诊断 O6 在排除已定义的非阻断模型输出观察后 `BLOCKING_FAIL=0`；人工 cohort 按最终实际采用的权威状态和水位隔离并只读验收通过。S18 只统计这些观察，不把它们计入最终 `BLOCKING_FAIL`，且不为其创建 S15 实现要求。
