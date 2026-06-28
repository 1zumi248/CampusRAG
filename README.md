# CampusRAG — 校园智能问答平台

基于 **RAG（检索增强生成）** + **Agent Tool Calling** 的校园知识问答系统。上传校园规章制度、教务流程等文档后，通过自然语言提问，系统自动检索相关内容并由大模型生成带来源引用的回答。同时集成天气、课表、图书馆座位、空闲教室等实用工具。

## 核心功能

- **多格式文档上传** — PDF、Word、Markdown、TXT，Apache Tika 解析
- **混合检索** — pgvector 向量检索 + Elasticsearch BM25 关键词检索，RRF 融合排序
- **Agent 工具调用** — LLM 自主判断并调用 6 个工具（知识库检索、天气、课表、座位、教室、时间）
- **SSE 流式输出** — 逐 Token 推送 + 工具调用状态实时反馈
- **多轮对话记忆** — ChatMemory 持久化至 PostgreSQL，5 轮（10 条消息）上下文窗口
- **Redis 热问缓存** — 问题 MD5 哈希，1 小时 TTL
- **会话管理** — 新建、切换、删除会话，自动以首条提问为标题

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Java 17 + Spring Boot 3.5 |
| LLM 编排 | LangChain4j 1.9.0-beta16 |
| 对话模型 | 阿里云 DashScope 通义千问 (qwen-plus) |
| 向量模型 | text-embedding-v4 (1024维) |
| 向量存储 | PostgreSQL + pgvector |
| 全文检索 | Elasticsearch 8.x |
| ORM | MyBatis-Plus 3.5 |
| 文档解析 | Apache Tika 3.2 + Jsoup |
| 缓存 | Redis (Lettuce) |
| 流式响应 | Reactor (Spring WebFlux) |
| 天气服务 | 高德地图天气 API |
| 前端框架 | Vue 3 + TypeScript |
| UI 库 | Element Plus |
| 构建工具 | Vite |
| Markdown | marked |

## 系统架构

```
用户浏览器 (Vue 3)
  │  SSE / REST API (Vite Proxy :5173 → :8080)
  ▼
ChatController (REST)
  │
ChatService (编排层)
  ├─ RagAssistant (AiServices.builder + 6 Tools)
  │   ├─ searchKnowledgeBase → RetrievalService
  │   │   ├─ VectorRetrievalStrategy (pgvector)
  │   │   └─ EsRetrievalStrategy (Elasticsearch)
  │   │   └─ RRF 融合 (k=60)
  │   ├─ getWeather → 高德天气 API
  │   ├─ getSchedule / getLibrarySeats / getEmptyClassrooms → Mock
  │   └─ getCurrentTime → 系统时钟
  ├─ ChatMemory (PostgreSQL, 10条消息窗口)
  └─ QueryCache (Redis, 1h TTL)

文档入库: MultipartFile → Tika → HtmlParser → ChunkService → EmbeddingService → pgvector + ES
```

### Agent 调用流程

```
用户提问 → Redis 缓存检查
  ├─ 命中 → 直接返回
  └─ 未命中 → RagAssistant.stream()
       │  LLM 自主选择工具调用
       │  SSE: conversation → tool → token → sources
       └─ 结果缓存至 Redis
```

| SSE 事件 | 说明 |
|----------|------|
| `conversation` | 会话 ID |
| `tool` | 工具调用状态（名称、中文显示名、完成状态） |
| `token` | 逐 Token 推送回答 |
| `sources` | 引用来源列表 |

## 可用工具

| 工具 | 功能 | 数据来源 |
|------|------|---------|
| `searchKnowledgeBase` | 检索校园知识库 | pgvector + ES 混合检索 |
| `getWeather` | 查询实时天气 | 高德地图天气 API |
| `getSchedule` | 查询课程表 | Mock |
| `getLibrarySeats` | 查询图书馆座位 | Mock |
| `getEmptyClassrooms` | 查询空闲教室 | Mock |
| `getCurrentTime` | 获取当前时间 | 系统时钟 |

## 快速开始

### 环境要求

- JDK 17+ / Node.js 20+ / Maven 3.9+
- PostgreSQL 14+（需 [pgvector](https://github.com/pgvector/pgvector) 扩展）
- Redis 7+ / Elasticsearch 8.x

### 1. 数据库初始化

```sql
CREATE DATABASE campus_rag;
CREATE EXTENSION vector;
\i campus-rag-backend/src/main/resources/sql/schema.sql
```

### 2. Elasticsearch 索引

```bash
curl -X PUT "http://localhost:9200/document_chunks" -H 'Content-Type: application/json' -d '{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "documentId": { "type": "long" },
      "content": { "type": "text", "analyzer": "ik_max_word" },
      "chunkIndex": { "type": "integer" },
      "metadata": { "type": "keyword" }
    }
  }
}'
```

### 3. 启动后端

```bash
cd campus-rag-backend
export DASHSCOPE_API_KEY=sk-your-key
export AMAP_API_KEY=your-amap-key  # 可选，不设置则天气功能不可用
mvn spring-boot:run
# 默认启动在 http://localhost:8080/api
```

### 4. 启动前端

```bash
cd campus-rag-frontend
npm install
npm run dev
# http://localhost:5173，自动代理 /api 到 :8080
```

## 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API 密钥 | 是 |
| `AMAP_API_KEY` | 高德地图 Web 服务 API Key | 否 |

数据库/Redis/ES 连接信息在 `application.yaml` 中配置，默认均为 `localhost` 默认端口。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 普通问答 |
| POST | `/api/chat/stream` | 流式问答（SSE） |
| GET | `/api/conversations` | 会话列表 |
| POST | `/api/conversations` | 新建会话 |
| GET | `/api/conversations/{id}/messages` | 历史消息 |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| POST | `/api/documents/upload` | 上传文档 |
| GET | `/api/documents` | 文档列表 |
| DELETE | `/api/documents/{id}` | 删除文档 |
| GET | `/api/test/all` | 模型连通性测试 |

## 项目结构

```
CampusRAG/
├── campus-rag-backend/           # Spring Boot 后端
│   └── src/main/java/com/hznu/campusragbackend/
│       ├── agent/tools/          # 6 个 Agent 工具
│       ├── config/               # LangChain4j、MyBatis 等配置
│       ├── controller/           # REST 接口
│       ├── service/              # ChatService、ConversationService、DocumentService
│       ├── rag/
│       │   ├── assistant/        # RagAssistant 接口
│       │   ├── retrieval/        # 混合检索（向量 + ES + RRF）
│       │   ├── chunk/            # 分块策略
│       │   ├── embedding/        # 向量化
│       │   └── parser/           # 文档解析（Tika + Jsoup）
│       └── resources/
│           └── sql/schema.sql
│
├── campus-rag-frontend/          # Vue 3 前端
│   └── src/
│       ├── views/                # ChatView（问答页）、ManageView（文档管理）
│       ├── components/           # MessageList、ChatSidebar、ChatInput
│       └── api/                  # Axios 封装
│
└── README.md
```

## 已知局限

- 扫描件/图片型 PDF 无法提取文字
- 复杂合并单元格表格转 Markdown 后可能丢失跨行/跨列语义
- 上传文件限制 50MB
- 课表、座位、教室为 Mock 数据，需接入学校实际系统
- DashScope API 有 QPS 限制，高并发需添加限流

## License

MIT
