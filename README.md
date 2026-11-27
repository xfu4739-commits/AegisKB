# AegisKB

<p align="center">
  <strong>企业级 RAG 知识库与可溯源对话</strong><br />
  五通道混合检索 · GraphRAG / RAPTOR · 显式引用 · RAG + ReAct 双执行器
</p>

<p align="center">
  <a href="https://github.com/xfu4739-commits/AegisKB"><img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk&logoColor=white" alt="Java 17" /></a>
  <a href="https://github.com/xfu4739-commits/AegisKB"><img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?logo=springboot&logoColor=white" alt="Spring Boot 3.5" /></a>
  <a href="https://github.com/xfu4739-commits/AegisKB"><img src="https://img.shields.io/badge/Spring%20AI-1.1-6DB33F?logo=spring&logoColor=white" alt="Spring AI" /></a>
  <a href="https://github.com/xfu4739-commits/AegisKB"><img src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3" /></a>
  <a href="https://github.com/xfu4739-commits/AegisKB"><img src="https://img.shields.io/badge/Python-rag--tools-3776AB?logo=python&logoColor=white" alt="Python rag-tools" /></a>
  <a href="http://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="Apache 2.0" /></a>
</p>

<p align="center">
  作者 <a href="https://github.com/xfu4739-commits">xfu-4739</a> · 北京交通大学<br />
  仓库：<a href="https://github.com/xfu4739-commits/AegisKB">github.com/xfu4739-commits/AegisKB</a>
</p>

---

## 目录

- [这是什么](#这是什么)
- [核心能力](#核心能力)
- [架构一览](#架构一览)
- [技术栈](#技术栈)
- [仓库结构](#仓库结构)
- [快速开始](#快速开始)
- [本地开发详解](#本地开发详解)
- [相关文档](#相关文档)

## 这是什么

**AegisKB** 面向企业场景的知识库 **检索增强生成（RAG）** 系统：从文档上传、解析与索引，到多通道检索、证据筛选与对话问答，主链路在 **Java（Spring Boot）** 中统一编排；**Python `aegis-kb-rag-tools`** 提供解析、OCR、重排序等算法能力，**不**决定知识范围、路由或最终引用绑定。

答案中的引用采用 **显式 `[n]` 标记**，只绑定当轮 Prompt 中渲染过的 **Source Evidence**，与 Context 扩展内容分池，便于审计与复核。

## 核心能力

> 能力划分与项目学习路线（文档入库 → 准备编排 → RAG 执行器 → 观测评测）一致，便于对照源码阅读。

| 能力域 | 要点 |
| :--- | :--- |
| **文档入库与多路索引** | 上传至 MinIO、Kafka 异步解析；Python 路由（含 Document Mind）+ 统一 Markdown 产物；父子块 / QA 切块；**pgvector + Elasticsearch（IK）+ Neo4j 结构图**；可选 **GraphRAG 实体图谱** 与 **RAPTOR 摘要树** 构建 |
| **知识路由与查询理解** | **Neo4j 文档结构图** + 三级漏斗自动缩小知识范围；会话记忆（长期摘要 + 近期窗口）；检索问题改写与子问题并发；元数据过滤 → **RetrievalPlan** 一次固化 scope / 通道 / 预算 |
| **五通道混合检索** | **向量 · 关键词 · 表格 · GraphRAG · RAPTOR** 并行召回；父块提升与结构导航补全；**加权融合 + RRF** 收敛候选窗；Java 编排 **Python Cross-Encoder Rerank**（失败可降级） |
| **可溯源 RAG 对话** | Source Evidence 与 Context Only **物理分池**；Prompt 证据预算与引用清单；答案 **`[n]` 显式绑定**原文，禁止前端/模型猜引用；**SSE 流式**输出与会话取消 |
| **开放式 Agent** | 时间敏感 / 开放类问题分流至 **ReAct**；DashScope 流式 + **Tavily** 联网；MySQL 检查点、工具轨迹与调用上限 |
| **可观测与质量评测** | 执行链路可视化、通道/候选级观测与同轮快照守恒；**RAGAS** 与 Hit@K 等检索对比实验（示例基线：Faithfulness 0.81、Context Recall 0.85、Hit@6 0.90） |
| **工程边界** | **Java** 拥有范围、计划、融合、终证据、Prompt、引用权威；**Python rag-tools** 负责解析与模型工具；**vue / vue-next** 管理端与对话 UI |

## 架构一览

### 分层总览

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {
  'primaryColor': '#3f5ee8',
  'primaryTextColor': '#ffffff',
  'primaryBorderColor': '#2d47b8',
  'secondaryColor': '#ecfdf5',
  'tertiaryColor': '#fff7ed',
  'lineColor': '#64748b',
  'fontSize': '14px'
}}}%%
flowchart TB
  subgraph L1["接入层 · 前端"]
    direction LR
    VUE["vue 生产端<br/>:5173"]
    VNEXT["vue-next 新版<br/>:5174"]
  end

  subgraph L2["编排层 · Java / Spring Boot"]
    direction TB
    API["API · 会话 · 知识库"]
    PLAN["RetrievalPlan<br/>一次解释 scope / 通道 / 预算"]
    subgraph PIPE["RAG 闭环"]
      direction LR
      RET["多通道检索"]
      FUS["特征融合"]
      RER["重排序窗口"]
      FIN["Source Evidence 选定"]
    end
    PROMPT["Prompt 渲染<br/>Source ⊕ Context 分池"]
    CITE["显式引用 [n] 绑定"]
    API --> PLAN --> RET --> FUS --> RER --> FIN --> PROMPT --> CITE
  end

  subgraph L3["算法层 · Python rag-tools :18080"]
    direction LR
    DOC["解析 · OCR · 表格"]
    RK["Rerank · 模型工具"]
  end

  subgraph L4["数据与基础设施 · Docker Compose"]
    direction TB
    subgraph BIZ["业务态"]
      MYSQL[(MySQL)]
      REDIS[(Redis)]
    end
    subgraph SEARCH["检索索引"]
      ES[(Elasticsearch IK)]
      PGV[(pgvector)]
      NEO[(Neo4j)]
    end
    subgraph OBJ["文件与消息"]
      MINIO[(MinIO)]
      KAFKA>Kafka]
    end
  end

  subgraph L5["外部模型"]
    LLM["DashScope 百炼<br/>Embedding · 生成"]
  end

  VUE --> API
  VNEXT --> API
  PLAN -.文档任务.-> DOC
  RER --> RK
  RET --> ES & PGV & NEO
  DOC --> MINIO
  API --> MYSQL & REDIS
  PROMPT --> LLM
  LLM --> CITE

  style L1 fill:#a7f3d0,stroke:#059669,stroke-width:3px,color:#064e3b
  style L2 fill:#fed7aa,stroke:#ea580c,stroke-width:3px,color:#7c2d12
  style PIPE fill:#ffedd5,stroke:#fb923c,stroke-width:2px,color:#9a3412
  style L3 fill:#bfdbfe,stroke:#2563eb,stroke-width:3px,color:#1e3a8a
  style L4 fill:#ddd6fe,stroke:#7c3aed,stroke-width:3px,color:#3b0764
  style BIZ fill:#ede9fe,stroke:#a78bfa,color:#4c1d95
  style SEARCH fill:#e9d5ff,stroke:#a855f7,color:#581c87
  style OBJ fill:#f3e8ff,stroke:#c084fc,color:#6b21a8
  style L5 fill:#fecaca,stroke:#dc2626,stroke-width:3px,color:#7f1d1d

  classDef hub fill:#3f5ee8,stroke:#1e2a7a,color:#ffffff,font-weight:bold
  class API,PLAN,PROMPT hub
```

| 色块 | 含义 |
| :---: | :--- |
| 绿 | 前端双轨 UI，只连 Java，不持密钥 |
| 橙 | **业务权威**：计划、检索闭环、Prompt、引用 |
| 蓝 | Python 工具箱，被编排调用 |
| 紫 | 可替换的基础设施与索引 |
| 红 | 外部大模型（经服务端调用） |

### 单次问答链路（面试速讲）

```mermaid
sequenceDiagram
  autonumber
  box rgba(5,150,105,0.22) 接入
    participant U as 用户
    participant F as Vue
  end
  box rgba(234,88,12,0.22) Java 编排
    participant J as Spring Boot
    participant P as RetrievalPlan
    participant R as 检索 / 融合 / 重排
    participant C as 引用绑定
  end
  box rgba(37,99,235,0.22) Python
    participant T as rag-tools
  end
  box rgba(124,58,237,0.18) 索引
    participant I as ES · pgvector · Neo4j
  end
  box rgba(220,38,38,0.18) 模型
    participant M as 百炼 API
  end

  U->>F: 提问 + 知识范围
  F->>J: POST /api（SSE 流式）
  J->>P: 解析 scope / filter / channel
  P->>R: 下发统一计划
  R->>I: 并行多通道召回
  R->>T: 可选 Rerank
  T-->>R: 排序分数
  R->>J: Source Evidence 窗口
  J->>M: Prompt（仅可见 Source 进引用池）
  M-->>J: 流式答案 + [n]
  J->>C: 解析 [n] → manifest 对齐
  C-->>F: 答案 + 可点击来源
  F-->>U: 渲染（无前端猜引用）
```

**职责边界（简述）**

- **Java**：知识范围、会话、通道调度、融合/重排编排、最终证据与 Prompt、引用归档。
- **Python**：文档与模型侧工具；消费 Java 下发的计划，不回写业务决策。
- **前端**：经 Java API 代理访问；不在浏览器存放模型密钥。

## 技术栈

| 层级 | 选型 |
| :--- | :--- |
| 后端 | Java 17、Spring Boot 3.5、Spring AI、MyBatis-Plus、Redisson |
| 算法服务 | Python、`aegis-kb-rag-tools`（FastAPI 风格本地服务，默认 `18080`） |
| 前端（生产） | Vue 3、Vite、Tailwind、shadcn-vue（`vue/`） |
| 前端（新版） | Vue 3、Ant Design Vue / X Vue（`vue-next/`，独立端口 `5174`） |
| 中间件 | Docker Compose：MySQL、Redis、PostgreSQL、Elasticsearch（IK）、Neo4j、Kafka、MinIO |

## 仓库结构

```text
AegisKB/
├── aegis-kb-business/          # 业务模块（含 chat 主应用）
├── aegis-kb-common/            # 公共组件与 Web 基础
├── aegis-kb-rag-tools/         # Python 解析与模型工具
├── aegis-kb-*-framework/       # ID、Redis、Redisson 等基础框架
├── vue/                        # 管理端与对话（生产默认）
├── vue-next/                   # 新版 UI（与 vue/ 并行，不同端口）
├── ai-example/                 # Spring AI 学习与示例
├── docker-compose.yaml         # 本地中间件
├── scripts/                    # start-java / start-rag / start-vue 等
├── sql/                        # 库表初始化脚本
└── document/                   # 设计、验收与 RAG 改造文档
```

## 快速开始

适合第一次在本机跑通全流程；细节与验收见 [本地开发详解](#本地开发详解) 与 `document/RAG系统验收与运行/`。

| 步骤 | 操作 |
| :---: | :--- |
| 1 | 安装 **Docker Desktop**（建议 ≥ 8GB 内存）、**JDK 17**、**Maven**、**Node.js**、**Python 3.10+** |
| 2 | 仓库根目录：`docker compose up -d`（首次 ES 需按下文准备 IK 插件） |
| 3 | 复制 `.env.example` → `.env`，填写 `ALI_BAI_LIAN_API_KEY`（百炼 / DashScope） |
| 4 | 一次性：`aegis-kb-rag-tools` 创建 venv 并 `pip install -e .`；`vue/` 下 `npm install` |
| 5 | 根目录执行 `.\start.cmd`（或 `scripts\start-all.ps1`），浏览器打开 **http://localhost:5173/** |

**默认服务端口**

| 端口 | 服务 |
| :---: | :--- |
| `9083` | Java 后端 |
| `18080` | Python rag-tools |
| `5173` | Vue 开发服务器 |
| `5174` | vue-next（若单独启动） |

**健康检查**：`http://127.0.0.1:9083/actuator/health` 应为 `"status":"UP"`。  
**管理端默认账号**（可通过环境变量覆盖）：`application.yaml` 中 `app.admin-auth`，默认用户名 `admin`。

## 本地开发详解

### 前置：中间件与密钥

#### 1. Docker 中间件

`docker-compose.yaml` 按 `application.yaml` 中的本机端口启动 MySQL、Redis、PostgreSQL/pgvector、Elasticsearch（含 IK）、Neo4j、Kafka、MinIO。**Java、rag-tools、前端仍在宿主机进程运行。**

```bash
docker compose up -d
docker compose ps
```

首次构建 Elasticsearch 前，若 `docker/elasticsearch/plugins/` 下没有 IK 插件包，先下载（官网证书异常时可加 `-k`）：

```bash
curl -k -L -o docker/elasticsearch/plugins/elasticsearch-analysis-ik-8.5.2.zip \
  https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.5.2.zip
```

镜像构建后会自动执行：

- `sql/表结构/Mysql/create_table_mysql.sql`
- `sql/表数据/Mysql/init_system_config.sql`
- `sql/表结构/PostgresSql/create_table_postgres_sql.sql`

若参数配置页中文乱码（旧 volume 在修复前初始化），在仓库根目录执行一次：

```powershell
Get-Content -Encoding UTF8 sql/表数据/Mysql/repair_system_config_charset.sql |
  docker exec -i aegis-kb-mysql mysql -uroot -proot --default-character-set=utf8mb4 aegis_kb
```

> 知识路由初始化脚本**不要**在空库上自动执行；待文档解析完成后再手工运行。

| 服务 | 宿主机地址 | 默认账号 |
| :--- | :--- | :--- |
| MySQL | `127.0.0.1:3307` / 库 `aegis_kb` | `root` / `root` |
| Redis | `127.0.0.1:6379` | 无密码 |
| PostgreSQL | `127.0.0.1:5432` / `aegis_kb_pgvector` | `postgres` / `postgres` |
| Elasticsearch | `http://127.0.0.1:9200` | `elastic` / `elastic` |
| Neo4j | `bolt://127.0.0.1:7687`（浏览器 `7474`） | `neo4j` / `12345678` |
| Kafka | `127.0.0.1:9092` | 无 |
| MinIO | `http://127.0.0.1:9000`（控制台 `9001`） | `minioadmin` / `minioadmin` |

Linux / WSL2 上 Elasticsearch 需要 `vm.max_map_count >= 262144`。

#### 2. 本机密钥（勿提交 Git）

在仓库根目录：

```text
.env.example  →  复制为  .env
```

至少配置 `ALI_BAI_LIAN_API_KEY`。可选：`TAVILY_API_KEY`、阿里云 Document Mind 等。启动脚本通过 `scripts/_load-local-config.ps1` 加载。

#### 3. Python rag-tools（首次）

```powershell
cd aegis-kb-rag-tools
python -m venv .venv
.\.venv\Scripts\pip install -e .
```

#### 4. 前端依赖（首次）

```powershell
cd vue
npm install
```

### 启动方式

以下方式**不会**再次执行 `docker compose`，请先完成中间件与 `.env`。

#### 方式 A：一键拉起（推荐）

根目录双击或运行 `start.cmd`，会打开 **三个独立 PowerShell 窗口**（Java / rag-tools / Vue）。编排窗口按 Enter 可关闭，**不会**结束那三个服务。

```bat
.\start.cmd
```

等价于 `powershell -File scripts\start-all.ps1`。Vue 会等待 Java `9083` health 为 `UP` 后再启动，避免 `/api` 代理 `ECONNREFUSED`。

#### 方式 B：分服务启动

```powershell
.\scripts\start-java.ps1
.\scripts\start-rag.ps1
.\scripts\start-vue.ps1
```

| 脚本 | 说明 |
| :--- | :--- |
| `start-java.ps1` | 无 jar 时先 `mvn package`；9083 已 UP 会提示跳过 |
| `start-rag.ps1` | 不自动创建 `.venv`，需先完成 Python 一次性安装 |
| `start-vue.ps1` | 轮询 Java 就绪后 `npm run dev` |

**提示**：Java 日志可能停在 MinIO / Kafka 附近而不再滚动，属正常；勿在 Java 控制台拖选文字（Windows QuickEdit 会暂停进程）。

### 与旧脚本的关系

已移除 `scripts/start-local.ps1`、`start-local.cmd`、`start-local.secrets.ps1.example`；密钥统一使用根目录 `.env`（见 `.env.example`）。

## 相关文档

| 文档 | 用途 |
| :--- | :--- |
| `document/RAG系统验收与运行/` | 真实环境启动、Python 安装与产品验收 |
| `document/RAG系统设计与重构/` | RAG 架构设计与分阶段改造 |
| `AGENTS.md` | 仓库协作与 RAG 改造约定（贡献者与 AI 辅助开发） |
| `DESIGN.md` | 前端 `vue` / `vue-next` 设计与重写边界 |

---

<p align="center">
  <sub>若 README 与本地分支不一致，请以 <code>main</code> 或文档目录中的最新手册为准。</sub>
</p>