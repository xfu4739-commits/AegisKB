# S02：显式 Citation Binding

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S02 的工程实施合同；具体执行结果、偏差和证据见 [records/S02.md](../records/S02.md)。

## 阶段设计依据

历史尝试曾用相似度和方向模型修复 citation，但真实反例证明模型阈值不能成为最终事实权威。本阶段将协议收敛为答案合法 1-based ASCII [n] 对同轮 manifest 的确定性绑定，并删除最后一个 citation 猜测消费者。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：答案中的合法显式 reference token 确定性绑定到同一轮 Prompt 实际渲染的 Source；该绑定是最终 citation 的唯一业务权威。

前置阶段：S01。

核心文件：

- `rag/service/ExplicitCitationBindingService.java`
- `rag/model/RagPromptAssemblyResult.java`
- `service/BusinessChatService.java`
- `model/SearchReference.java`
- `rag/executor/RagChatExecutor.java`
- `ragtools/client/RagToolsClient.java`
- `ExplicitCitationBindingServiceTest.java`
- `RagChatExecutorTest.java`
- `nexus-agent-rag-tools/rag_tools/main.py`
- `nexus-agent-rag-tools/rag_tools/semantic_model.py`

约束：

- 正式 token 语法冻结为 1-based ASCII `[` + 非零十进制整数 + `]`，即 `\\[([1-9][0-9]*)]`；不修复圆括号、`ID:`、`ref`、全角符号、零、前导零或其他近似格式。
- token 只能映射到同一轮 Prompt manifest 中 disposition 为 `PROMPT_RENDERED_SOURCE` 或 `PROMPT_REUSED_SOURCE`、且拥有稳定 citation identity 的 `referenceId`。
- Context Only、未渲染 Source、未知或越界编号、无稳定 identity 的条目必须 fail closed；拒绝原因只有显式绑定 owner 解释，不能由检索、NLI 或归档层重新判断。
- 最终 source snapshot 只包含答案实际显式引用且合法的 rendered Source，按 token 在答案中第一次出现的顺序去重；同一 token 重复出现不得重复归档。
- 答案没有合法显式引用时 source snapshot 为空；禁止按答案相似文本、候选顺序或模型分数自动补引。
- Prompt rendered Source identities、合法 explicit citation identities 和最终 source snapshot identities 必须逐项可对账，任何输出 identity 都不得超出 rendered Source。
- O9 必须记录解析到的 token、成功绑定、拒绝 reason、最终归档 identity 和守恒结果；旧 `SUPPORTS / CONTRADICTS / UNRELATED`、置信阈值和 `ACCEPTED_SUPPORT` 不再描述 final citation disposition。
- citation NLI 没有非 citation 长期消费者；必须删除主链最后消费者、O9 旧 schema、Java client/DTO、Python citation endpoint/schema/test 及 citation 专用模型加载，不保留 feature toggle、双路径或 compatibility facade。
- S01 的 Prompt rendered Source 边界和 S03 的 Source/Context 物理分池是只读输入契约，本阶段不得修改。

实施清单：

- [x] 冻结 Prompt 正式 1-based `[n]` token 语法并建立 characterization fixture。
- [x] 先补普通正文、多个 heading、连续/重复/跨段 token、拒绝分支、无显式引用和守恒的目标红灯。
- [x] 以单一深接口实现 explicit token 到 Prompt manifest/rendered Source 的确定性绑定。
- [x] 让 SSE reference、archive source snapshot 和 FINALIZE O9 消费同一绑定结果。
- [x] 将 citation O9 改为 token/binding/rejection/final identity 守恒，不再投影 NLI final disposition。
- [x] 删除旧 citation NLI Java/Python 主链、协议、模型和测试，并执行精确退出扫描。
- [x] 完成自动回归后停在 `HUMAN_ACTION_REQUIRED`，由用户使用新代码产生新 exchange。

测试：

~~~bash
mvn -pl nexus-agent-business/nexus-agent-business-chat -am \
  -DfailIfNoTests=false \
  -Dtest=ExplicitCitationBindingServiceTest,RagPromptAssemblyServiceTest,RagChatExecutorTest,TableRetrievalChannelTest,SearchReferenceMapperTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

运行时验收：自动验证完成后保持 `S02 / HUMAN_ACTION_REQUIRED`。用户用最新 Java 进程、既有 document `2334598914772058112`，在同一个全新会话依次重跑 O8-09、O8-10、O8-11；S11 验证会话只读核对新 cohort、task lineage、四个 required target、答案 token、manifest 和 source snapshot 映射，并返回 `S02_RUNTIME_PASS/FAIL`、conversationId 和 exchange IDs。本 S02 会话收到 PASS 后再次只读核对，不能自行制造 exchange。

退出条件：所有合法 token 只绑定到本轮 Prompt rendered/reused Source；未知/越界、Context Only、未渲染和无 identity 条目全部拒绝；最终 source snapshot 按答案首次出现顺序去重，且等于合法 explicit citation identities；无合法 token 时不补引；SSE、archive、FINALIZE O9 与绑定结果守恒；旧 citation NLI 最后消费者和死协议退出扫描为零；自动回归全部通过；新代码产生的新 exchange 完成跨会话只读验收。满足人工证据前不得把 S02 标为 `DONE`。

