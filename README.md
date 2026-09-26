# AegisKB

企业知识库检索与问答系统。支持文档解析与索引构建、多通道检索、以及基于 Prompt 可见证据的显式引用。

仓库：https://github.com/xfu4739-commits/AegisKB

## 能力概览

- 知识库与文档管理：上传、解析路由、策略确认、索引构建
- 检索：向量 / 关键词 / 结构导航等多通道，融合与重排序后选出可引用证据
- 问答：Source Evidence 与 Context Only 分池，答案中的 `[n]` 只绑定当轮渲染过的 Source
- 前端：知识库管理与对话界面（`vue/`）
- 算法工具：Python `nexus-agent-rag-tools` 负责解析、OCR、rerank 等，不决定业务 scope 或最终引用

## 模块

| 模块 | 说明 |
| :--- | :--- |
| `nexus-agent-business-chat` | 文档管理、检索编排、会话与引用主链路 |
| `nexus-agent-common` | 通用枚举、Web 与基础组件 |
| `nexus-agent-rag-tools` | Python 解析与模型工具 |
| `vue` | 管理端与对话前端 |
| `ai-example` | Spring AI / RAG 示例，不进入主产品链路 |

## 启动

真实环境启动、Python 工具安装和验收步骤见 `document/RAG系统验收与运行/`。

本地密钥请用环境变量注入，不要写入仓库，例如 `ALI_BAI_LIAN_API_KEY`、`TAVILY_API_KEY`。
