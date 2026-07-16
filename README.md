# CampusRAG — 校园智能问答平台

CampusRAG 是一个面向校园知识服务的 RAG（检索增强生成）应用。系统将规章制度、教务流程等文档转为可检索知识库，并通过自然语言问答提供带引用、可回看原文的回答。

项目通过 OpenAI 兼容接口连接模型服务。服务地址、对话模型和向量模型均由使用者在配置文件中指定，README 不绑定具体模型。

## 核心亮点

- **可追溯回答** — 回答附带文档来源，点击来源即可查看原文并定位到对应分块
- **可解释检索** — 展示向量检索、关键词检索及 RRF 融合后的排名与分数
- **结构化文档入库** — 解析 PDF、Word、Markdown 和 TXT，保留标题路径并单独处理表格
- **混合检索** — 使用 pgvector 进行语义检索，使用 Elasticsearch BM25 进行关键词检索，再通过 RRF 融合排序
- **Agent 工具调用** — 根据问题选择知识库、天气、课表、图书馆座位、空闲教室或时间工具
- **流式交互** — 通过 SSE 推送回答、工具执行状态和引用来源；生成失败时返回明确错误并正常结束连接
- **会话能力** — 支持多轮对话记忆、会话管理和 Redis 热问缓存
- **知识工作台** — 提供聊天、文档管理、来源预览和响应式布局

> 课表、图书馆座位和空闲教室当前使用 Mock 数据，接入学校实际系统后才能返回真实信息。

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.5、Spring WebFlux |
| LLM 编排 | LangChain4j、OpenAI 兼容接口 |
| 数据访问 | MyBatis-Plus |
| 向量存储 | PostgreSQL、pgvector |
| 全文检索 | Elasticsearch 8.x |
| 文档解析 | Apache Tika、Jsoup |
| 缓存 | Redis、Lettuce |
| 前端 | Vue 3、TypeScript、Element Plus、Vite |
| 内容渲染 | marked、highlight.js |
| 外部服务 | 高德地图天气 API |

具体依赖版本以 `pom.xml` 和 `package.json` 为准。

## 系统架构

```text
用户浏览器（Vue 3）
  │  REST / SSE
  ▼
Spring Boot API
  ├─ ChatService
  │   ├─ Agent + Tools
  │   ├─ ChatMemory（PostgreSQL）
  │   └─ QueryCache（Redis）
  ├─ RetrievalService
  │   ├─ pgvector 语义检索
  │   ├─ Elasticsearch BM25 检索
  │   └─ RRF 融合排序（k=60）
  └─ DocumentService
      └─ Tika → 结构解析 → 分块 → 向量化 → pgvector + Elasticsearch
```

### 问答与 SSE 流程

```text
用户提问 → 检查 Redis 缓存
  ├─ 命中 → 返回会话、回答和来源
  └─ 未命中 → Agent 判断是否调用工具
       ├─ conversation：返回会话 ID
       ├─ tool：返回工具执行信息
       ├─ token：逐段推送回答
       ├─ sources：返回引用及检索调试信息
       └─ error：返回可读错误并正常关闭流
```

| SSE 事件 | 说明 |
|----------|------|
| `conversation` | 当前会话 ID |
| `tool` | 工具名称、参数、结果和执行状态 |
| `token` | 增量回答内容 |
| `sources` | 引用来源；实时回答包含向量、ES 和 RRF 调试字段 |
| `error` | 模型服务连接或回答生成失败信息 |

## 可用工具

| 工具 | 功能 | 数据来源 |
|------|------|---------|
| `searchKnowledgeBase` | 检索校园知识库 | pgvector + Elasticsearch |
| `getWeather` | 查询实时天气 | 高德地图天气 API |
| `getSchedule` | 查询课程表 | Mock |
| `getLibrarySeats` | 查询图书馆座位 | Mock |
| `getEmptyClassrooms` | 查询空闲教室 | Mock |
| `getCurrentTime` | 获取当前时间 | 系统时钟 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20.19+ 或 22.12+
- PostgreSQL 14+，并安装 pgvector 扩展
- Redis 7+
- Elasticsearch 8.x，并安装与版本匹配的 IK 分词插件
- 一个可通过 OpenAI 兼容接口访问的模型服务

### 1. 初始化 PostgreSQL

以下命令需在仓库根目录通过 `psql` 执行。创建数据库后，必须先连接到 `campus_rag`，再启用 pgvector 扩展和导入表结构。

```text
CREATE DATABASE campus_rag;
\connect campus_rag
CREATE EXTENSION IF NOT EXISTS vector;
\i campus-rag-backend/src/main/resources/sql/schema.sql
```

### 2. 创建 Elasticsearch 索引

项目不会自动创建索引。以下映射依赖 IK 分词插件：

```bash
curl -X PUT "http://localhost:9200/document_chunks" \
  -H "Content-Type: application/json" \
  -d '{
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "mappings": {
      "properties": {
        "id": { "type": "keyword" },
        "documentId": { "type": "long" },
        "content": {
          "type": "text",
          "analyzer": "ik_max_word",
          "search_analyzer": "ik_smart"
        },
        "documentTitle": { "type": "text" },
        "chunkIndex": { "type": "integer" },
        "chunkType": { "type": "keyword" },
        "sectionPath": { "type": "text" }
      }
    }
  }'
```

### 3. 配置并启动后端

在 `campus-rag-backend/src/main/resources/application.yaml` 中配置数据库、Redis、Elasticsearch、模型服务地址，以及对话模型和向量模型标识。

模型由配置决定，不由 API Key 自动决定。API Key 只负责鉴权，并且需要与所使用的服务端点及区域匹配。

Linux/macOS：

```bash
cd campus-rag-backend
export DASHSCOPE_API_KEY=your-api-key
export AMAP_API_KEY=your-amap-key  # 可选
mvn spring-boot:run
```

PowerShell：

```powershell
Set-Location campus-rag-backend
$env:DASHSCOPE_API_KEY = "your-api-key"
$env:AMAP_API_KEY = "your-amap-key" # 可选
mvn spring-boot:run
```

后端默认地址为 `http://localhost:8080/api`。

### 4. 启动前端

```bash
cd campus-rag-frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会将 `/api` 请求代理到后端 8080 端口。

### 5. 验证服务

```bash
curl http://localhost:8080/api/test/all
```

该接口用于检查对话服务和向量服务是否可访问。

## 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `DASHSCOPE_API_KEY` | 当前模型服务实现使用的鉴权密钥 | 是 |
| `AMAP_API_KEY` | 高德地图 Web 服务 API Key | 否 |

数据库、Redis、Elasticsearch、模型服务地址和模型标识目前在 `application.yaml` 中配置。提交代码前请确认没有把真实密钥写入版本控制。

## API 接口

所有路径均以 `/api` 为前缀。

### 问答与会话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat` | 普通问答 |
| POST | `/chat/stream` | 流式问答（SSE） |
| GET | `/conversations` | 获取会话列表 |
| POST | `/conversations` | 新建会话 |
| GET | `/conversations/{id}/messages` | 获取会话历史 |
| DELETE | `/conversations/{id}` | 删除会话 |

### 文档管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/documents/upload` | 上传并索引文档 |
| GET | `/documents` | 分页获取文档列表 |
| GET | `/documents/{id}` | 获取文档详情 |
| GET | `/documents/{id}/chunks` | 获取文档分块，用于来源预览 |
| DELETE | `/documents/{id}` | 删除文档及相关索引 |

### 连通性与维护

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/test/chat` | 检查对话服务连通性 |
| GET | `/test/embedding` | 检查向量服务连通性 |
| GET | `/test/all` | 检查全部模型服务 |
| POST | `/admin/migrate-to-es` | 将已有文档分块迁移到 Elasticsearch |

### 检索评测

以下接口仅在非 `prod` Profile 下注册：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/eval/generate` | 生成评测集 |
| GET | `/admin/eval/run?topK=5` | 运行检索评测 |
| GET | `/admin/eval/testset` | 查看评测集 |
| GET | `/admin/eval/compare?q=...` | 对比向量、ES 和混合检索结果 |

## 项目结构

```text
CampusRAG/
├── campus-rag-backend/
│   └── src/main/
│       ├── java/com/hznu/campusragbackend/
│       │   ├── admin/             # 索引迁移等维护能力
│       │   ├── agent/tools/       # Agent 工具
│       │   ├── common/            # 返回结构与异常处理
│       │   ├── config/            # 模型、数据库和 Web 配置
│       │   ├── controller/        # REST 接口
│       │   ├── evaluation/        # 非生产环境检索评测
│       │   ├── rag/               # 解析、分块、向量化与检索
│       │   ├── repository/        # PostgreSQL 与 ES 数据访问
│       │   └── service/           # 问答、会话和文档服务
│       └── resources/
│           ├── application.yaml
│           └── sql/schema.sql
├── campus-rag-frontend/
│   └── src/
│       ├── api/                   # REST API 封装
│       ├── components/            # 对话、输入、侧栏和来源预览
│       └── views/                 # 问答与文档管理页面
├── docs/                          # 项目说明与评审材料
├── experiments/                   # 本地评测输出
└── README.md
```

## 已知局限与故障排查

- 扫描件或图片型 PDF 无法直接提取文字，需要先进行 OCR
- 复杂合并单元格表格转为 Markdown 后，可能丢失跨行或跨列语义
- 单个上传文件限制为 50 MB，文档解析内容也有字符数上限
- 课表、图书馆座位和空闲教室当前使用 Mock 数据
- 高并发场景需要根据模型服务限制增加限流、重试和熔断策略
- 若返回“AI 服务连接失败”，请检查服务地址、凭证区域、网络、代理和 TLS 连通性
- 切换模型服务时，需要同时确认对话与向量接口的协议、模型标识和向量维度兼容

## License

MIT
