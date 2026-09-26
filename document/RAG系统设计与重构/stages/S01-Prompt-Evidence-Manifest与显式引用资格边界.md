# S01：Prompt Evidence Manifest 与显式引用资格边界

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S01 的工程实施合同；具体执行结果、偏差和证据见 [records/S01.md](../records/S01.md)。

## 阶段设计依据

旧链路中 Prompt 实际渲染的 Source 与事后引用候选不是同一集合，未展示给模型的内容仍可能获得最终来源资格。本阶段建立同轮 Prompt Evidence Manifest，要求 rendered/reused Source 与显式引用资格完全相等。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

## 2026-07-15 现行协议勘误（REOPENED 时优先）

S01 最初实施时使用的 citation repair/matcher/NLI 最终协议，已由 S02 在 2026-07-15 删除并替换为 explicit `[n]` token binding。下方约束、测试和退出条件均按现行协议表述；当时真实发生过的旧协议演进只保留在 `records/S01.md` 和 `records/S02.md`，不得从历史记录恢复为生产方案。

现行责任边界：S01 只拥有 Prompt manifest、rendered/reused Source 和 `explicitCitationEligibleIdentities` 的资格边界；S02 是 token binding、拒绝 reason、FINALIZE 和 final source snapshot 的唯一 owner。Context、omitted 和未渲染条目不得取得最终绑定资格。

唯一目标：对每条 Prompt 输入建立完整去向账本，并让 explicit citation eligibility 只包含模型同轮实际看到的 Source Evidence。

前置阶段：S00。

必读：

- [改造计划与进度](../改造计划与进度.md) 中“问题基线与阶段归属”和“目标架构与权威边界”的对应条目。
- [改造计划与进度](../改造计划与进度.md) 的 Q-TABLE-05 冻结反例与全局完成清单，以及 [验证规则](../验证规则.md) 的 O9 守恒目标。
- Q-TABLE-05 旧链路和验收方案 O4/table 部分。

核心文件：

- `rag/model/RagPromptAssemblyResult.java`
- `rag/service/RagPromptAssemblyService.java`
- `rag/executor/RagChatExecutor.java`
- `service/TaskInfo.java`
- `service/BusinessChatService.java`
- `rag/service/ExplicitCitationBindingService.java`（只作为 S01 输出契约的下游消费者）
- `RagPromptAssemblyServiceTest.java`
- `RagChatExecutorTest.java`
- `ExplicitCitationBindingServiceTest.java`

本阶段新增模型：`PromptReferenceDisposition`、`PromptReferenceDecision`、`PromptRenderedSourceEvidence`。这些模型用于承载受控 manifest/projection，不得退化为散乱 Map。

约束：

- 每条输入 reference 恰有一个 disposition。
- 至少表达 `PROMPT_RENDERED_SOURCE`、`PROMPT_RENDERED_CONTEXT`、`PROMPT_REUSED_SOURCE`、`OMITTED_BUDGET`、`OMITTED_DUPLICATE`、`OMITTED_INVALID_IDENTITY`、`OMITTED_NOT_APPLICABLE`。
- rendered/reused Source identity 集合去重且稳定，`explicitCitationEligibleIdentities` 与它完全相等；展示型 `referenceId` 只用于答案 token 回绑，稳定 evidence identity 才是守恒主键。
- Web Source Evidence 使用稳定 `WEB:<url>` 进入 generation-visible source 集合，并与 document Source 服从同一个 Prompt manifest 和 eligibility 边界；不得为 Web 或 document 建第二套 citation 候选权威。
- S01 只收口资格边界，不解析答案 token、不产生最终 source snapshot，也不判断答案语义支持方向；这些责任分别属于 S02 explicit binding 和 S18 人工/验收判定。

实施清单：

- [x] 先写“首个超预算项后仍遍历并记账”的失败测试。
- [x] 先写“每个输入只有一个最终去向”的守恒测试。
- [x] Assembly result 返回 manifest、rendered source identities 和 rendered context identities。
- [x] `TaskInfo` 不再把完整 flatten references 当作最终 citation 权威。
- [x] `explicitCitationEligibleIdentities` 只由 rendered/reused Source identities 投影，不保留旧候选字段或双写模型。
- [x] S02 下游 binding 只消费同一 Prompt manifest/rendered Source；final source snapshot 是合法 token 绑定结果，不由 S01 追加。
- [x] O9 保存 manifest、rendered source/context 和 `explicitCitationEligibleIdentities`。
- [x] 下游 binding 保持候选 source identity，不接受任何投影改写 `chunkId`/`parentBlockId` 等 provenance identity。

定向测试：

~~~bash
mvn -pl nexus-agent-business/nexus-agent-business-chat -am \
  -DfailIfNoTests=false \
  -Dtest=RagPromptAssemblyServiceTest,RagChatExecutorTest,ExplicitCitationBindingServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

运行时验收：新跑 Q-TABLE-05。正确证据要么以 `PROMPT_RENDERED_SOURCE` / `PROMPT_REUSED_SOURCE` 进入 `explicitCitationEligibleIdentities`，要么具有唯一 omitted disposition 且绝不能进入 binding/final source snapshot；不得再出现 reference 5 无账本记录。

退出条件：

- `input reference count = 所有 disposition count`。
- `explicitCitationEligibleIdentities = rendered/reused source identities`。
- 合法 token binding 和 `source snapshot identities` 都只能来自 `explicitCitationEligibleIdentities`；无合法 token 时 snapshot 为空。
- manifest -> binding -> source snapshot 的 source identity 不变，展示型 `referenceId` 不得替代稳定 evidence identity。
- Context Only、omitted 和未渲染 reference 均无法进入 binding 或 final citation。
- 新 exchange、Prompt snapshot 和 SQL/O9 对账已记录。

停止点：答案 token 解析、拒绝 reason、首次出现顺序、FINALIZE/archive 守恒属于 S02；“引用证据是否真正支持答案事实”由 S18 依据原文人工/验收判定。S01 不恢复相似度/NLI 补引，也不实现第二个 citation owner。
