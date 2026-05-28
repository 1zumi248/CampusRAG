# CampusRAG — 校园智能平台

基于 **RAG（检索增强生成）** + **Tool Calling（工具调用）** 架构的校园知识问答系统。上传校园规章制度、教务流程等文档后，即可通过自然语言提问，系统自动检索相关内容并由大模型生成带来源引用的回答。同时支持天气查询、课表查询、图书馆座位、空闲教室等实用工具。

---

## 核心功能

- **多格式文档上传** — 支持 PDF、Word（doc/docx）、Markdown、TXT 等格式
- **智能文档解析** — Apache Tika 解析，保留标题层级与表格结构
- **语义分块与向量化** — 递归分块（段落/句子级）+ 50 字重叠窗口，标题路径前置，表格独立处理
- **混合检索** — 向量检索 + Elasticsearch 关键词检索，RRF（Reciprocal Rank Fusion）融合排序
- **Agent 工具调用** — LLM 自主判断并调用工具：知识库检索、天气、课表、座位、教室、时间
- **SSE 流式输出** — 基于 Reactor 逐 Token + 工具状态实时推送
- **多轮对话记忆** — LangChain4j ChatMemory 持久化至 PostgreSQL，支持 5 轮（10 条消息）上下文
- **Redis 热问缓存** — 基于问题 MD5 哈希，1 小时 TTL，覆盖流式/非流式双通路
- **会话管理** — 新建、切换、删除会话，自动以首条提问为标题

---

## 技术栈

### 后端

| 组件 | 技术 | 版本 |
|------|------|------|
| 语言/框架 | Java + Spring Boot | 17 / 3.5 |
| LLM 编排 | LangChain4j | 1.9.0-beta16 |
| 对话模型 | 阿里云 DashScope 通义千问 | qwen-plus |
| 向量模型 | 阿里云 DashScope 文本嵌入 | text-embedding-v4 (1024维) |
| 向量存储 | PostgreSQL + pgvector | — |
| 全文检索 | Elasticsearch | 8.x |
| ORM | MyBatis-Plus | 3.5.16 |
| 文档解析 | Apache Tika | 3.2.2 |
| 缓存 | Redis (Lettuce) | — |
| 流式响应 | Reactor (Spring WebFlux) | — |
| HTML 解析 | Jsoup | 1.18.1 |
| 工具库 | Hutool | 5.8.26 |
| 天气服务 | 高德地图天气 API | — |

### 前端

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Vue 3 + TypeScript | 3.5 |
| UI 库 | Element Plus | 2.14 |
| 构建工具 | Vite | 8.0 |
| Markdown 渲染 | marked | 14.x |
| HTTP 客户端 | Axios | 1.16 |
| 路由 | Vue Router | 5.0 |

---

## 系统架构

```
┌──────────────────────────────────────────────────────────┐
│                        Frontend                          │
│              Vue 3 + Element Plus + Axios                │
│               SSE Stream / REST API                      │
└──────────────────────┬───────────────────────────────────┘
                       │  Vite Proxy (:5173 → :8080)
┌──────────────────────▼───────────────────────────────────┐
│                    Spring Boot 3.5                        │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌─────────────────────┐    │
│  │Controller│  │ChatService│  │   RagAssistant      │    │
│  │ (REST)   │  │ (编排层)  │  │ (AiServices.builder)│    │
│  └──────────┘  └──────────┘  └──────────┬──────────┘    │
│                      │                  │                │
│         ┌────────────▼─────┐    ┌───────▼──────────┐    │
│         │   Agent Tools    │    │   ChatMemory      │    │
│         │ ┌──────────────┐ │    │ (对话记忆持久化)  │    │
│         │ │RagRetrieval  │ │    └──────────────────┘    │
│         │ │Weather       │ │                             │
│         │ │Schedule      │ │                             │
│         │ │LibrarySeat   │ │                             │
│         │ │Classroom     │ │                             │
│         │ │CurrentTime   │ │                             │
│         │ └──────────────┘ │                             │
│         └────────┬─────────┘                             │
│                  │                                       │
│  ┌───────────────▼────────────────────────────────┐     │
│  │            RetrievalService                     │     │
│  │  ┌──────────────┐  ┌───────────────────┐       │     │
│  │  │VectorStrategy│  │  EsStrategy       │       │     │
│  │  │(pgvector)    │  │(Elasticsearch)    │       │     │
│  │  └──────────────┘  └───────────────────┘       │     │
│  │              RRF 融合排序                        │     │
│  └────────────────────────────────────────────────┘     │
│                                                          │
│  文档入库流水线:                                          │
│  MultipartFile → Tika(HTML) → HtmlParser → ChunkService  │
│  → EmbeddingService → pgvector + Elasticsearch            │
└──────────────────────┬───────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────┐
│                      数据层                               │
│  ┌────────────┐  ┌───────────┐  ┌────────────┐         │
│  │ PostgreSQL │  │  pgvector  │  │   Redis    │         │
│  │ (业务数据) │  │ (向量索引) │  │ (热问缓存) │         │
│  └────────────┘  └───────────┘  └────────────┘         │
│  ┌────────────────┐                                      │
│  │ Elasticsearch  │                                      │
│  │  (全文检索)    │                                      │
│  └────────────────┘                                      │
└──────────────────────────────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────┐
│              阿里云 DashScope (兼容 OpenAI)               │
│         qwen-plus  │  text-embedding-v4                  │
│                                                          │
│              高德地图 API (天气)                          │
└──────────────────────────────────────────────────────────┘
```

### Agent Tool Calling 流程

```
用户提问
  → Redis 缓存检查 (MD5 哈希)
  → 命中 → 直接返回
  → 未命中:
      → resolveConversationId()  // 无 ID 则创建新会话
      → RagAssistant.stream(question, @MemoryId)
      │   LLM 自主决定是否需要调用工具:
      │   ├─ 校园制度/教务问题 → searchKnowledgeBase (混合检索)
      │   ├─ 天气问题 → getWeather (高德API)
      │   ├─ 课表问题 → getSchedule (Mock)
      │   ├─ 座位问题 → getLibrarySeats (Mock)
      │   ├─ 教室问题 → getEmptyClassrooms (Mock)
      │   ├─ 时间问题 → getCurrentTime
      │   └─ 无关问题 → 直接回答
      │
      │   SSE 事件流:
      │   ├─ event:conversation → 会话ID
      │   ├─ event:tool → 工具调用状态（前端显示"正在检索知识库..."）
      │   ├─ event:token → 逐Token推送
      │   └─ event:sources → 引用来源（仅检索类问题）
      │
      └─ 回答缓存至 Redis (1h TTL)
```

### 混合检索策略

| 策略 | 技术 | 说明 |
|------|------|------|
| 向量检索 | pgvector cosine 相似度 | 语义匹配，适合长文本自然语言查询 |
| 关键词检索 | Elasticsearch BM25 | 精确匹配，适合专业术语、编号、日期 |
| RRF 融合 | Reciprocal Rank Fusion (k=60) | 两种结果按倒数排名加权合并，互补优劣 |

### ChatMemory 双表存储

| 表 | 用途 | 内容 |
|----|------|------|
| `messages` | 前端渲染 + 持久化 | 供 `ConversationService` 查询，供前端展示历史 |
| `chat_memory` | LLM 上下文 | LangChain4j 读写，JSONB 格式，10 条消息窗口 |

### 文档入库流程

```
文件上传 (MultipartFile)
  → DocumentService.uploadDocument()
  │   ├─ 捕获文件名，投喂 Apache Tika (ToHTMLContentHandler)
  │   └─ 若解析失败 → 抛出 DocumentParseException → 422
  │
  → HtmlParserService 提取结构:
  │   ├─ 标题层级 (h1-h6) → 维护 headingStack
  │   ├─ 表格 → 转 Markdown 格式 → 独立 chunk 不切分
  │   └─ 段落 → 关联标题路径 (section_path)
  │
  → ChunkService 分块:
  │   ├─ 段落: 标题路径 + 内容 → 按\n\n切段 → 按句子细切 (300-800字)
  │   ├─ 表格: 标题路径 + Markdown 表格 → 单 chunk 不切分
  │   └─ 50字重叠 + 短块合并
  │
  → EmbeddingService 批量向量化 (每批10条)
  → 存入 pgvector + Elasticsearch + document_chunks 表
```

---

## 快速开始

### 环境要求

- **JDK** 17+
- **Node.js** 20.19+ 或 22.12+
- **PostgreSQL** 14+（需安装 [pgvector](https://github.com/pgvector/pgvector) 扩展）
- **Redis** 7+
- **Elasticsearch** 8.x（需安装并启动）
- **Maven** 3.9+

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd CampusRAG
```

### 2. 数据库初始化

```sql
-- 创建数据库
CREATE DATABASE campus_rag;

-- 启用 pgvector 扩展
CREATE EXTENSION vector;

-- 执行建表脚本
\i campus-rag-backend/src/main/resources/sql/schema.sql
```

### 3. Elasticsearch 索引初始化

```bash
# 创建文档分块索引
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

### 4. 后端配置与启动

```bash
cd campus-rag-backend

# 设置环境变量
export DASHSCOPE_API_KEY=sk-your-api-key-here
export AMAP_API_KEY=your-amap-api-key    # 高德地图 Web服务 Key（可选，不设置则天气功能不可用）

# 修改 application.yaml 中的数据库、Redis、ES 连接信息（如需要）
# 默认: localhost:5432 (postgres/123456), localhost:6379, localhost:9200

# 启动
mvn spring-boot:run
```

服务启动在 `http://localhost:8080/api`。

### 5. 前端配置与启动

```bash
cd campus-rag-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端启动在 `http://localhost:5173`，自动代理 `/api` 请求到后端 8080 端口。

### 6. 验证

```bash
# 测试后端健康状态
curl http://localhost:8080/api/test/all

# 浏览器访问
open http://localhost:5173
```

---

## 环境变量

| 变量名 | 说明 | 必填 |
|--------|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API 密钥 | 是 |
| `AMAP_API_KEY` | 高德地图 Web 服务 API Key | 否（不设置则天气功能不可用） |

数据库、Redis、Elasticsearch 连接信息在 `application.yaml` 中配置，默认值：

| 配置项 | 默认值 |
|--------|--------|
| PostgreSQL URL | `localhost:5432/campus_rag` |
| PostgreSQL 用户/密码 | `postgres` / `123456` |
| Redis Host | `localhost:6379` |
| Redis 密码 | （空） |
| Elasticsearch Host | `localhost:9200` |
| ES 索引名 | `document_chunks` |

---

## 可用工具

| 工具名 | 功能 | 数据来源 | 触发场景 |
|--------|------|---------|---------|
| `searchKnowledgeBase` | 检索校园知识库 | pgvector + ES 混合检索 | 规章制度、教务流程等校园问题 |
| `getWeather` | 查询实时天气 | 高德地图天气 API | 天气相关提问 |
| `getSchedule` | 查询课程表 | Mock | 课表、课程安排提问 |
| `getLibrarySeats` | 查询图书馆座位 | Mock | 图书馆座位、自习位置提问 |
| `getEmptyClassrooms` | 查询空闲教室 | Mock | 空闲教室、教室占用提问 |
| `getCurrentTime` | 获取当前日期时间 | 系统时钟 | 日期、时间相关提问 |

---

## API 接口

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 普通问答（阻塞式） |
| POST | `/api/chat/stream` | 流式问答（SSE） |

### SSE 事件类型

| 事件 | 说明 |
|------|------|
| `conversation` | 返回会话 ID（首条消息时创建） |
| `tool` | 工具调用状态，`{"name":"searchKnowledgeBase","displayName":"检索知识库","status":"done"}` |
| `token` | 逐 Token 推送回答内容 |
| `sources` | 引用来源列表（仅检索类问题） |

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/conversations` | 获取会话列表 |
| POST | `/api/conversations` | 创建新会话 |
| GET | `/api/conversations/{id}/messages` | 获取会话历史消息 |
| DELETE | `/api/conversations/{id}` | 删除会话 |

### 文档管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/documents/upload` | 上传文档（multipart/form-data） |
| GET | `/api/documents` | 获取文档列表 |
| GET | `/api/documents/{id}` | 获取文档详情 |
| DELETE | `/api/documents/{id}` | 删除文档及其分块和向量 |

### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/test/chat` | 测试对话模型连通性 |
| GET | `/api/test/embedding` | 测试向量模型连通性 |
| GET | `/api/test/all` | 测试全部模型连通性 |

---

## 项目结构

```
CampusRAG/
├── campus-rag-backend/                  # 后端 (Spring Boot)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/hznu/campusragbackend/
│       │   ├── agent/
│       │   │   └── tools/              # Agent 工具集（6个工具）
│       │   │       ├── RagRetrievalTool    # 知识库检索工具（混合检索）
│       │   │       ├── WeatherTool         # 高德天气 API
│       │   │       ├── ScheduleTool        # 课表查询（Mock）
│       │   │       ├── LibrarySeatTool     # 图书馆座位（Mock）
│       │   │       ├── ClassroomTool       # 空闲教室（Mock）
│       │   │       └── CurrentTimeTool     # 当前时间
│       │   ├── config/                  # 配置 (LangChain4j, MyBatis, Web)
│       │   ├── controller/              # REST 接口
│       │   ├── model/                   # 数据模型
│       │   ├── repository/              # MyBatis-Plus Mapper
│       │   ├── service/                 # 业务服务
│       │   │   ├── ChatService          # 问答编排（Tool Calling + SSE 事件流）
│       │   │   ├── ConversationService  # 会话管理
│       │   │   ├── DocumentService      # 文档 CRUD + 解析
│       │   │   └── QueryCache           # Redis 热问缓存
│       │   ├── common/                  # 全局异常处理、统一返回格式
│       │   └── rag/
│       │       ├── assistant/
│       │       │   └── RagAssistant     # AiService 接口（SystemMessage + Tools）
│       │       ├── chunk/              # 分块策略
│       │       ├── embedding/          # 向量化服务
│       │       ├── parser/             # 文档解析
│       │       └── retrieval/          # 检索服务
│       │           ├── RetrievalService     # 混合检索编排
│       │           ├── VectorRetrievalStrategy  # pgvector 向量检索
│       │           └── EsRetrievalStrategy      # ES 关键词检索
│       └── resources/
│           ├── application.yaml         # 主配置
│           └── sql/schema.sql           # 建表脚本
│
├── campus-rag-frontend/                 # 前端 (Vue 3)
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── api/                         # Axios 封装
│       ├── components/
│       │   ├── ChatSidebar.vue          # 会话列表
│       │   ├── MessageList.vue          # 消息渲染（Markdown + 工具状态 + 引用来源）
│       │   └── ChatInput.vue            # 输入框
│       ├── views/
│       │   ├── ChatView.vue             # 问答页（SSE 流式编排）
│       │   └── ManageView.vue           # 文档管理页
│       ├── router/index.ts
│       ├── App.vue
│       └── main.ts
│
├── .architecture-backend.html
├── .architecture-frontend.html
└── README.md
```

### 架构设计原则

| 原则 | 说明 |
|------|------|
| 薄 Controller | `@Valid` 校验 + 参数提取，无手写 null/blank 检查 |
| 具体类优先 | service 层只有具体类，无冗余接口 |
| Agent 自治 | LLM 自主判断是否调用工具，检索从固定前置步骤变为可选工具 |
| AiServices.builder | 手动构建代理注入工具，替代 `@AiService` 自动代理 |
| 工具职责单一 | 每个工具类只做一件事，便于测试和扩展 |
| 混合检索互补 | 向量（语义）+ ES（关键词）RRF 融合，互补短板 |
| 异常分类 | 自定义异常按 HTTP 状态码区分，全局 handler 统一映射 |
| 前端组件拆分 | ChatView 拆为 3 个独立组件（Props/Emits/Expose 契约清晰） |
| SSE 直连 | 流式问答不走 Axios，直接 `fetch() + ReadableStream` 实时解析 |

---

## 错误处理体系

| 异常 | HTTP 状态 | 说明 |
|------|----------|------|
| `DocumentNotFoundException` | 404 | 文档不存在或已删除 |
| `DocumentParseException` | 422 | 文件解析失败 |
| `RetrievalException` | 502 | 向量/ES 检索失败 |
| `MethodArgumentNotValidException` | 400 | 请求参数校验失败 |
| `RuntimeException` | 500 | 其他未预料异常 |

---

## 分块策略

| 步骤 | 方法 | 说明 |
|------|------|------|
| 1 | 结构提取 | Tika HTML → Jsoup 解析，提取 h1-h6 标题层级和 table 表格 |
| 2 | 标题前置 | 每个段落块/表格块拼接当前章节路径（如 "第六章 > 奖学金评定"） |
| 3 | 段落切分 | 按双换行切段 → 超 800 字按句子细切 |
| 4 | 表格处理 | 转 Markdown 表格格式，独立成块，不参与进一步切分 |
| 5 | 重叠窗口 | 块间保留 50 字重叠，保障语义连贯 |
| 6 | 短块合并 | 不足 300 字的小块合并到前一块（合并后 ≤ 800 字） |

---

## 检索配置

```yaml
rag:
  retrieval:
    top-k: 3         # 每次检索返回的最相关 chunk 数
    min-score: 0.4   # 最低相似度阈值 (0.0–1.0)

elasticsearch:
  host: localhost
  port: 9200
  index-name: document_chunks
```

---

## 已知局限

- PDF 文档经 Tika 解析后标题质量取决于原始排版，扫描件/图片型 PDF 无法提取文字
- 合并单元格的复杂表格转 Markdown 后可能丢失跨行/跨列语义
- 上传文件限制 50MB，单次解析上限约 50 万字符
- DashScope API 调用有 QPS 限制，高并发场景需添加限流机制
- 课表、座位、教室为 Mock 数据，需接入学校实际信息系统才能提供真实数据

---

## License

MIT
