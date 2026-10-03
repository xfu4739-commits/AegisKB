# AegisKB

企业知识库检索与问答系统。支持文档解析与索引构建、多通道检索、以及基于 Prompt 可见证据的显式引用。

作者：xfu-4739，北京交通大学  
仓库：https://github.com/xfu4739-commits/AegisKB

## 能力概览

- 知识库与文档管理：上传、解析路由、策略确认、索引构建
- 检索：向量 / 关键词 / 结构导航等多通道，融合与重排序后选出可引用证据
- 问答：Source Evidence 与 Context Only 分池，答案中的 `[n]` 只绑定当轮渲染过的 Source
- 前端：知识库管理与对话界面（`vue/`）
- 算法工具：Python `aegis-kb-rag-tools` 负责解析、OCR、rerank 等，不决定业务 scope 或最终引用

## 模块

| 模块 | 说明 |
| :--- | :--- |
| `aegis-kb-business-chat` | 文档管理、检索编排、会话与引用主链路 |
| `aegis-kb-common` | 通用枚举、Web 与基础组件 |
| `aegis-kb-rag-tools` | Python 解析与模型工具 |
| `vue` | 管理端与对话前端 |
| `ai-example` | Spring AI / RAG 示例，不进入主产品链路 |

## 启动

真实环境启动、Python 工具安装和验收步骤见 `document/RAG系统验收与运行/`。

本地密钥请用环境变量注入，不要写入仓库，例如 `ALI_BAI_LIAN_API_KEY`、`TAVILY_API_KEY`。

### 1. 用 Docker 启动中间件

`docker-compose.yaml` 会按 `application.yaml` 的本机端口拉起 MySQL、Redis、PostgreSQL/pgvector、Elasticsearch（含 IK）、Neo4j、Kafka、MinIO。Java、rag-tools、前端仍在本机启动。

```bash
docker compose up -d
docker compose ps
```

首次构建 Elasticsearch 前，若 `docker/elasticsearch/plugins/` 下没有 IK 插件包，先下载（官网证书过期时加 `-k`）：

```bash
curl -k -L -o docker/elasticsearch/plugins/elasticsearch-analysis-ik-8.5.2.zip \
  https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.5.2.zip
```

然后会构建带 IK 的 Elasticsearch 镜像，并自动执行：

- `sql/表结构/Mysql/create_table_mysql.sql`
- `sql/表数据/Mysql/init_system_config.sql`
- `sql/表结构/PostgresSql/create_table_postgres_sql.sql`

知识路由初始化脚本不要在空库自动执行，等文档解析完成后再手工跑。

| 服务 | 宿主机地址 | 账号 |
| :--- | :--- | :--- |
| MySQL | `127.0.0.1:3307` / `aegis_kb` | `root` / `root` |
| Redis | `127.0.0.1:6379` | 无密码 |
| PostgreSQL | `127.0.0.1:5432` / `aegis_kb_pgvector` | `postgres` / `postgres` |
| Elasticsearch | `http://127.0.0.1:9200` | `elastic` / `elastic` |
| Neo4j | `bolt://127.0.0.1:7687`（浏览器 `7474`） | `neo4j` / `12345678` |
| Kafka | `127.0.0.1:9092` | 无 |
| MinIO | `http://127.0.0.1:9000`（控制台 `9001`） | `minioadmin` / `minioadmin` |

Docker Desktop 建议至少 8GB 内存。Linux/WSL2 需要 `vm.max_map_count >= 262144`。

### 2. 本机启动应用

中间件需先自行 `docker compose up -d`（见上文）。配置好 `start-local.secrets.ps1` 后，在同一终端用 `concurrently` 拉起 Java `9083`、rag-tools `18080`、Vue `5174`（`Ctrl+C` 一次停全部）：

```bat
scripts\start-local.cmd
```

或在仓库根目录：

```powershell
.\scripts\start-local.ps1
```

脚本只启动 Java、rag-tools、Vue，不再执行 `docker compose`。

浏览器打开 `http://localhost:5174/`。管理端默认账号见 `application.yaml` 的 `app.admin-auth`。
