# AegisKB

<p align="center">
  <strong>企业级 RAG 知识库与可溯源对话</strong><br />
  Java 编排 · Python 算法工具箱 · 五通道混合检索
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

面向技术文档与企业资料场景的 **检索增强生成（RAG）** 系统：文档解析与多路索引、知识路由、混合检索、可溯源对话与开放式 Agent **统一由 Java 主链路编排**；Python `aegis-kb-rag-tools` 提供解析、重排等能力，不决定最终 scope 与引用。

> 环境安装、启动步骤与 182 篇源码讲解见个人配套教程，本页仅展示**架构**与**亮点**。

## 项目亮点

| | 亮点 | 说明 |
| :---: | :--- | :--- |
| 1 | **五通道并行检索** | 向量 · 关键词（ES+IK）· 表格 · **GraphRAG** · **RAPTOR** 同计划并发；**加权融合 + RRF** 收敛窗口，Python **Cross-Encoder Rerank**（可降级） |
| 2 | **Neo4j 知识路由** | 文档结构图 + **三级漏斗**自动缩小检索范围；子问题改写与并发；**RetrievalPlan** 一次解释 scope / filter / 通道 / 预算 |
| 3 | **可溯源引用** | Source Evidence 与 Context Only **分池**；答案仅 **`[n]` 显式绑定**当轮 Prompt 中的 Source，前后端不猜、不补引用 |
| 4 | **GraphRAG & RAPTOR** | 构建期可选实体图谱与摘要树；检索期社区报告、关系遍历与原文下钻，与经典向量/关键词同一融合窗口 |
| 5 | **双执行器** | 库内问题走 **RAG 执行器**（SSE 流式）；开放/时效问题分流 **ReAct + Tavily**，检查点与工具轨迹可观测 |
| 6 | **全链路可观测** | 通道级 / 候选级观测、同轮快照守恒、执行链路可视化；支持 **RAGAS**、Hit@K 等评测闭环（示例：Faithfulness **0.81**、Context Recall **0.85**、Hit@6 **0.90**） |
| 7 | **清晰工程边界** | **Java**：范围、检索编排、终证据、Prompt、引用权威；**Python**：解析/OCR/重排工具；**Vue**：经 API 代理，不持模型密钥 |

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
    VUE["vue 生产端"]
    VNEXT["vue-next"]
  end

  subgraph L2["编排层 · Java / Spring Boot"]
    direction TB
    API["API · 会话 · 知识库"]
    PLAN["RetrievalPlan"]
    subgraph PIPE["RAG 闭环"]
      direction LR
      RET["多通道检索"]
      FUS["特征融合"]
      RER["重排序"]
      FIN["Source Evidence"]
    end
    PROMPT["Prompt<br/>Source / Context 分池"]
    CITE["显式引用 [n]"]
    API --> PLAN --> RET --> FUS --> RER --> FIN --> PROMPT --> CITE
  end

  subgraph L3["算法层 · Python rag-tools"]
    direction LR
    DOC["解析 · OCR · 表格"]
    RK["Rerank · 模型工具"]
    AG["ReAct 工具侧"]
  end

  subgraph L4["数据与中间件"]
    direction TB
    subgraph BIZ["业务态"]
      MYSQL[(MySQL)]
      REDIS[(Redis)]
    end
    subgraph SEARCH["检索索引"]
      ES[(Elasticsearch)]
      PGV[(pgvector)]
      NEO[(Neo4j)]
    end
    subgraph OBJ["文件与消息"]
      MINIO[(MinIO)]
      KAFKA>Kafka]
    end
  end

  subgraph L5["外部服务"]
    LLM["DashScope · Embedding / 生成"]
    WEB["Tavily 联网"]
  end

  VUE --> API
  VNEXT --> API
  PLAN -.解析任务.-> DOC
  RER --> RK
  RET --> ES & PGV & NEO
  DOC --> MINIO
  API --> MYSQL & REDIS
  PROMPT --> LLM
  LLM --> CITE
  API -.开放问答.-> AG
  AG --> WEB
  AG --> LLM

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
| 绿 | 前端 UI，只访问 Java API |
| 橙 | **业务权威**：计划 → 检索 → Prompt → 引用 |
| 蓝 | Python 工具箱（解析 / 重排 / Agent 工具） |
| 紫 | 业务库 + 多路检索索引 + 对象存储 |
| 红 | 大模型与外部搜索 |

### 文档入库与索引（构建链路）

```mermaid
flowchart LR
  subgraph IN["接入"]
    UP[上传 / 策略确认]
  end
  subgraph ASYNC["异步构建"]
    KF[Kafka 任务]
    PY[Python 解析路由]
    MD[统一 Markdown / 结构树]
    CH[父子块 · QA 切块]
    IDX[向量化 + ES + 结构图]
    GR[GraphRAG · RAPTOR 可选]
  end
  subgraph OUT["可检索态"]
    READY[多通道可读]
  end

  UP --> KF --> PY --> MD --> CH --> IDX --> GR --> READY

  style IN fill:#d1fae5,stroke:#059669,stroke-width:2px
  style ASYNC fill:#dbeafe,stroke:#2563eb,stroke-width:2px
  style OUT fill:#fde68a,stroke:#d97706,stroke-width:2px
```

### 单次问答（RAG 速讲）

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
  F->>J: SSE 流式 API
  J->>P: scope / filter / channel
  P->>R: 统一计划
  R->>I: 五通道并行召回
  R->>T: Rerank
  T-->>R: 分数
  R->>J: Source 窗口
  J->>M: Prompt（引用池仅含 Source）
  M-->>J: 流式答案 + [n]
  J->>C: manifest 对齐
  C-->>F: 答案 + 来源
```

---

<p align="center">
  <sub>Spring Boot 3.5 · Spring AI · MyBatis-Plus · Kafka · Neo4j · pgvector · FastAPI rag-tools</sub>
</p>