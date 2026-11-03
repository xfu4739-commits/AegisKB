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

### 前置：中间件与密钥

**1. Docker 中间件**

`docker-compose.yaml` 会按 `application.yaml` 的本机端口拉起 MySQL、Redis、PostgreSQL/pgvector、Elasticsearch（含 IK）、Neo4j、Kafka、MinIO。Java、rag-tools、前端仍在本机进程启动。

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

**2. 本机密钥（勿提交 Git）**

在仓库根目录复制 `.env.example` 为 `.env`，至少填写 `ALI_BAI_LIAN_API_KEY`（DashScope / 百炼）。可选：`TAVILY_API_KEY`、阿里云 Document Mind 密钥等。启动脚本通过 `scripts/_load-local-config.ps1` 加载该文件。

**3. Python rag-tools 虚拟环境（首次一次）**

```powershell
cd aegis-kb-rag-tools
python -m venv .venv
.\.venv\Scripts\pip install -e .
```

**4. 前端依赖（首次一次）**

```powershell
cd vue
npm install
```

### 本机两种启动方式

以下两种方式都**不会**再执行 `docker compose`，需先完成上文中间件与 `.env` 配置。

| 端口 | 服务 |
| :--- | :--- |
| `9083` | Java 后端（Spring Boot） |
| `18080` | Python rag-tools |
| `5173` | Vue 开发服务器（Vite 默认端口） |

#### 方式 A：一键拉起（推荐）

在仓库根目录双击或执行 `start.cmd`。会依次打开 **三个独立 PowerShell 窗口**（标题栏可区分：Java / rag-tools / Vue）。当前 Cursor 或终端窗口只负责编排，按 Enter 可关闭编排窗口，**不会**停止那三个服务窗口。

```bat
.\start.cmd
```

等价于 `powershell -File scripts\start-all.ps1`。Vue 窗口内会等待 `http://127.0.0.1:9083/actuator/health` 为 `UP` 后再启动 Vite，避免代理 `/api` 时出现 `ECONNREFUSED`。

浏览器打开：**http://localhost:5173/**。管理端默认账号见 `application.yaml` 的 `app.admin-auth`。

#### 方式 B：按服务单独启动

适合只调试某一端：在仓库根目录分别执行（每个命令占一个终端窗口）。

```powershell
.\scripts\start-java.ps1
.\scripts\start-rag.ps1
.\scripts\start-vue.ps1
```

- `start-java.ps1`：若无现成 jar 会先 `mvn package`（首次较慢）；9083 已被占用且 health 已为 UP 时会提示无需重复启动。
- `start-rag.ps1`：**不会**自动创建 `.venv`，需先完成上文 rag-tools 一次性安装。
- `start-vue.ps1`：会轮询 Java 9083 就绪后再 `npm run dev`。

Java 启动后日志可能停在 MinIO / Kafka 附近而不再滚动，属正常现象；可用浏览器访问 `http://127.0.0.1:9083/actuator/health` 确认 `"status":"UP"`。请勿在 Java 控制台窗口内拖选文字（Windows QuickEdit 会暂停进程）。

### 与旧脚本的关系

已移除 `scripts/start-local.ps1`、`start-local.cmd` 及 `start-local.secrets.ps1.example`，密钥统一改为根目录 `.env`（见 `.env.example`）。
