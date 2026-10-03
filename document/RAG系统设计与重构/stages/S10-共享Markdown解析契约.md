# S10：共享 Markdown 解析契约

> 状态只在 [改造计划与进度.md](../改造计划与进度.md) 维护。本文件是 S10 的工程实施合同；具体执行结果、偏差和证据见 [records/S10.md](../records/S10.md)。

## 阶段设计依据

Python 和 Java 分别解释 Markdown 会让标题、编号、列表、表格和 source span 在跨语言边界漂移。本阶段先定义共享 syntax event/AST/source-span 契约，后续结构和表格投影只能消费这一份语法事实。

本阶段所有长期设计必须由本项目的 `CODE_CONFIRMED`、`DATA_CONFIRMED`、明确不变量或目标红灯支持。外部资料只能作为一般性研究背景，不能替代本项目事实，也不进入学员版设计权威。

> 本阶段状态只在 [改造计划与进度](../改造计划与进度.md) 维护；本文件只保存阶段范围、实施清单和退出条件。全局施工与人工操作规则见 [AGENTS.md](../../../AGENTS.md)，通用命令与测试矩阵见 [验证规则](../验证规则.md)。

唯一目标：Python 使用成熟 Markdown/CommonMark/GFM 解析能力输出共享事件/AST，完整保留 structure 和 table 所需语法，不在本阶段做 Java 业务投影。

前置阶段：S09。

必读：[S09 的 Q-ROUTE-03 冻结诊断](S09-Fusion-Rerank-Budget正式切流.md)中失败链路和测试约束；完整列表被持久化为 `TITLE` 是待追溯的跨层反例，不能在未核对 provider 输入和 parser 事件前先验指定错误 producer。

核心文件：

- `aegis-kb-rag-tools/rag_tools/document_parser.py`
- Python parser schema/DTO。
- `aegis-kb-rag-tools/tests/test_document_parser_providers.py`
- 新增 `tests/test_document_parser_markdown.py`
- Java `RagToolsDocumentParserService` 及 DTO 映射测试。

约束：

- 优先采用成熟 CommonMark/GFM parser，不手写整套正则解析器。
- 保留 heading level、原始编号、list/table 类型、source span、原文和顺序。
- PDF/OCR/provider 路径不得因 Markdown 改造回归。
- structure 和 table 必须消费同一解析结果，不能各写一套 Markdown 解释器。

实施清单：

- [x] fixture 覆盖 `#` 层级、中文章、10.1/10.2、列表、表格、转义 pipe、空 cell 和相邻标题。
- [x] 定义稳定 versioned schema，非法事件 fail fast。
- [x] Java DTO 无损接收，不在映射层猜父子或 table schema。
- [x] round-trip 测试证明 source span 和 token 顺序不丢失。
- [x] auto keyword/auto question 若保留，只作为构建期 chunk enrichment 持久化，并在检索时作为 rank feature；不得变成运行时硬路由或 channel 开关。
- [x] 构建期 parser/chunk 参数只影响新上传或重建 task，不通过运行时配置偷偷改变已有产物语义。
- [x] 现有 PDF/图片 provider tests 全绿。

## O8-RD-PARSE-01：原因列表语法事实

必读：[S03 新代码真实 cohort 与阶段退出](../records/S03.md#2026-07-12-新代码真实-cohort-与阶段退出)。内部诊断 `O8-RD-PARSE-01` 映射既有验收 `Q-O8-01`，不新增 case。旧 chunk `2313953728534752602` 包含“检索命中率突然下降”的四项原因且持久化为 `TITLE` 是 `DATA_CONFIRMED`。

2026-07-14 S10 开工复核已确认实际 producer：上传源对应 Python `str` 字符下标半开区间 `[7786,7851)` 是没有 heading marker 的合法 ordered list；native provider 原样接收同一 22,711-byte Markdown；`_parse_plain_text()` 调用 `_classify_text_block()` 后把完整多行列表标为 `TITLE`，`_normalize_blocks()` 再把整段写入 `sectionPath`，Java `DocumentStrategyServiceImpl.renderBlockContent()` 才补出后续 chunk 中的 `# `。该链由当前源码、旧 block/task trace 和同源文件当前代码复现交叉确认为 `CODE_CONFIRMED` / `DATA_CONFIRMED`；“provider 引入 heading marker”这一 `HYPOTHESIS` 已被否定。完整输入、事件和 DTO 对账进入 `records/S10.md`。

上述旧字符区间只用于复现改造前数据，不是 `markdown-syntax.v1` 的定位单位。共享契约的 `startByte/endByte` 是原始 `sourceText` UTF-8 编码后的 0-based 半开区间 `[startByte,endByte)`；`startLine/startColumn/endLine/endColumn` 是 1-based，column 按 Unicode code point 而不是 UTF-8 字节计数，end line/column 表示半开区间终点。源内容相同且 `sourceSha256=4113c18c82dd7b971db4664ea1ade603b9e76725ed04cc4fcca7982546675d57` 时，冻结 ordered-list node `md-000764` 的契约 byte span 是 `[16872,17039)`、位置是 `486:1 -> 491:1`，包含列表后的两个换行。S11 及后续真实重建验收必须按新 artifact 的 source hash 和 UTF-8 byte span 重新定位，禁止直接拿 `[7786,7851)` 与 byte span 对账。

本阶段修改生产代码前必须逐层保存并对账：源文件对应 source span、provider 规范化后的 Markdown、共享 parser event/AST，以及 Java 接收到的 DTO。只有沿这条链确认 producer 后，才能在实际责任层修复。

- 如果 provider 输入字面就是合法 CommonMark heading（例如 `# 1.`），不得为了该样例破坏 CommonMark 语义或把它特判成 list；必须定位是谁引入了 heading marker，或输出足够可靠的 origin/source-span 事实供 S11 投影，不能依赖业务词猜测。
- 新增通用 fixture，至少同时覆盖真正 heading、heading 后的 ordered list、相邻正文和 provider 引入标记的来源信息；不得出现三问文本、验收文档名、业务章节号或旧 chunk ID。
- S10 只输出 heading/list/text、level、ordinal、source span、origin 等语法事实，不决定 citation capability、final selection 或回答内容。
- 本阶段不预先承诺新 task 的唯一 chunk type；下游必须服从本阶段确认的语法/provenance 事实，但最终要保留四项 source-authored 内容，不能只留下一个不可引用的 `TITLE` Context 作为唯一承载。
- `records/S10.md` 必须记录实际确认的 producer、输入/事件对账证据和偏差；若 hypothesis 被否定，要勘误阶段结论，不能为了符合旧诊断修改正确 parser。

退出条件：共享契约和 provider 回归通过；本阶段不重建 15 文档、不宣称 structure/table 已完成。

