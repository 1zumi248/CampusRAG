# RAG 项目开发经验记录 (Part 3)

## 问题 1：文件上传完整流程的调用关系
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
如果我现在上传一个文件，为我讲解下整个流程（文件之间的调用关系）。

### 解释
文件上传涉及多层架构的协同工作，从 HTTP 请求到数据库持久化的完整链路。

#### 完整调用链
```
用户/前端
    ↓ HTTP POST /api/documents/upload (multipart/form-data)
    
[1] DocumentController.uploadDocument()
    ├─ 接收 MultipartFile 参数
    ├─ 调用 documentService.uploadDocument(file)
    └─ 返回 Result<Document> JSON响应
    
    ↓ 调用
    
[2] DocumentServiceImpl.uploadDocument()
    ├─ ① 调用 documentParserService.parse(file) → 解析文档
    │       ↓
    │   [3] DocumentParserService.parse()
    │       ├─ tika.detect() 检测文件类型
    │       ├─ extractText() 提取纯文本
    │       │   └─ AutoDetectParser + BodyContentHandler
    │       └─ 返回 ParsedDocumentResult {content, fileType, fileName, fileSize}
    │
    ├─ ② 计算内容哈希: DigestUtil.md5Hex(content)
    ├─ ③ 检查是否重复: documentRepository.selectOne(按 contentHash 查询)
    │       ↓
    │   [4] DocumentRepository (MyBatis Plus Mapper)
    │       └─ 执行 SQL: SELECT * FROM documents WHERE content_hash = ?
    │
    ├─ ④ 如果已存在 → 直接返回已有文档
    │
    └─ ⑤ 如果不存在 → 创建 Document 对象并保存
            ↓
        [4] DocumentRepository.insert(document)
            └─ 执行 SQL: INSERT INTO documents (...) VALUES (...)
    
    ↓ 返回 Document 对象
    
[1] DocumentController 封装为 Result.ok(document)
    ↓
返回 JSON 给前端
```

#### 各层职责详解

**第1层：Controller 层**
📁 `DocumentController.java`

```java
@PostMapping("/upload")
public Result<Document> uploadDocument(@RequestParam("file") MultipartFile file) {
    return Result.ok(documentService.uploadDocument(file));
}
```

**职责**：
- 接收 HTTP 请求（`POST /api/documents/upload`）
- 从请求中提取 `MultipartFile` 对象
- 调用 Service 层处理业务逻辑
- 将结果包装成统一的 `Result` 格式返回

**关键点**：
- `@PostMapping("/upload")`：映射到 `/api/documents/upload`（因为配置了 `context-path: /api`）
- `@RequestParam("file")`：从表单数据中获取名为 `file` 的文件
- 不做任何业务逻辑，纯粹是接口适配层

---

**第2层：Service 层**
📁 `DocumentServiceImpl.java`

这是**核心业务逻辑层**，负责协调各个组件：

```java
@Override
public Document uploadDocument(MultipartFile file) {
    try {
        // 步骤1: 解析文档
        ParsedDocumentResult parsedResult = documentParserService.parse(file);
        
        // 步骤2: 计算内容哈希（用于去重）
        String contentHash = DigestUtil.md5Hex(parsedResult.getContent());
        
        // 步骤3: 检查是否已存在相同内容的文档
        Document existingDoc = documentRepository.selectOne(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getContentHash, contentHash)
        );
        
        if (existingDoc != null) {
            log.warn("文档已存在: {}", existingDoc.getTitle());
            return existingDoc;  // 直接返回，避免重复
        }
        
        // 步骤4: 创建文档对象
        Document document = Document.builder()
                .title(parsedResult.getFileName())
                .fileName(parsedResult.getFileName())
                .fileType(parsedResult.getFileType())
                .fileSize(parsedResult.getFileSize())
                .content(parsedResult.getContent())
                .contentHash(contentHash)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 步骤5: 保存到数据库
        documentRepository.insert(document);
        
        log.info("文档上传成功: ID={}, 标题={}", document.getId(), document.getTitle());
        return document;
        
    } catch (Exception e) {
        log.error("文档上传失败", e);
        throw new RuntimeException("文档上传失败: " + e.getMessage(), e);
    }
}
```

**职责分解**：

| 步骤 | 操作 | 调用的组件 | 目的 |
|------|------|-----------|------|
| ① | 解析文档 | `DocumentParserService.parse()` | 从文件中提取纯文本 |
| ② | 计算哈希 | `DigestUtil.md5Hex()` | 生成内容指纹，用于去重 |
| ③ | 查重 | `DocumentRepository.selectOne()` | 检查数据库中是否已有相同内容 |
| ④ | 构建对象 | `Document.builder()` | 创建实体对象 |
| ⑤ | 持久化 | `DocumentRepository.insert()` | 保存到 PostgreSQL |

---

**第3层：Parser 服务层**
📁 `DocumentParserService.java`

```java
public ParsedDocumentResult parse(MultipartFile file) {
    try {
        String originalFilename = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.info("开始解析文件: {}, 大小: {} bytes", originalFilename, fileSize);

        // 1. 检测文件类型
        String fileType = tika.detect(originalFilename);
        log.debug("检测到文件类型: {}", fileType);

        // 2. 提取文本内容
        String content = extractText(file);

        log.info("文件解析成功: {}, 提取文本长度: {} 字符", originalFilename, content.length());

        // 3. 构建返回结果
        return ParsedDocumentResult.builder()
                .content(content)
                .fileType(fileType)
                .fileName(originalFilename)
                .fileSize(fileSize)
                .build();

    } catch (Exception e) {
        log.error("文档解析失败: {}", file.getOriginalFilename(), e);
        throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
    }
}
```

**核心技术：Apache Tika**

```java
private String extractText(MultipartFile file) throws Exception {
    AutoDetectParser parser = new AutoDetectParser();  // 自动检测解析器
    BodyContentHandler handler = new BodyContentHandler(Constants.DOC_PARSE_MAX_CHARS);  // 限制50万字符
    Metadata metadata = new Metadata();  // 元数据容器
    ParseContext context = new ParseContext();  // 解析上下文

    try (InputStream inputStream = file.getInputStream()) {
        parser.parse(inputStream, handler, metadata, context);
        return handler.toString();  // 返回提取的纯文本
    }
}
```

**支持的格式**：
- PDF（`.pdf`）
- Word（`.doc`, `.docx`）
- Excel（`.xls`, `.xlsx`）
- PowerPoint（`.ppt`, `.pptx`）
- 纯文本（`.txt`）
- Markdown（`.md`）
- HTML（`.html`）
- ...等 100+ 种格式

---

**第4层：Repository 层**
📁 `DocumentRepository.java`

```java
@Mapper
public interface DocumentRepository extends BaseMapper<Document> {
}
```

**职责**：
- 继承 MyBatis Plus 的 `BaseMapper`，自动获得 CRUD 方法
- 无需编写 SQL，框架自动生成

**使用的方法**：
- `selectOne(LambdaQueryWrapper)` → 生成条件查询 SQL
- `insert(Document)` → 生成插入 SQL
- `selectList(null)` → 查询所有记录
- `selectById(Long)` → 根据 ID 查询
- `deleteById(Long)` → 根据 ID 删除

---

#### 数据库操作示例

**1. 查重查询（步骤③）**
```sql
SELECT id, title, file_name, file_type, file_size, content, 
       content_hash, created_at, updated_at
FROM documents
WHERE content_hash = 'abc123def456...'
LIMIT 1;
```

**2. 插入新文档（步骤⑤）**
```sql
INSERT INTO documents (title, file_name, file_type, file_size, 
                       content, content_hash, created_at, updated_at)
VALUES ('校园规章.pdf', '校园规章.pdf', 'application/pdf', 1234567,
        '提取的文本内容...', 'abc123def456...', 
        '2026-05-13 10:30:00', '2026-05-13 10:30:00');
```

---

#### 当前流程的局限性

**❌ 缺失的关键步骤**

根据 RAG 系统的完整流程，当前实现**只完成了前半部分**：

```
✅ 已完成：
用户上传 → Controller → Service → Parser 解析 → 保存到 documents 表

❌ 未完成（需要后续实现）：
→ 分块（Chunking）→ 向量化（Embedding）→ 存入 pgvector → 可供检索
```

**🔧 完整流程应该是**：

```
1. 用户上传文件
2. 解析文档提取文本 ✅ 已实现
3. 保存到 documents 表 ✅ 已实现
4. 【待实现】将文本切分为 chunks
5. 【待实现】为每个 chunk 生成向量 embedding
6. 【待实现】将 chunks + vectors 存入 document_chunks 表和 pgvector
7. 【待实现】用户可以通过问答接口检索这些知识
```

---

## 问题 2：DocumentParserService 代码详解
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
解释下 `DocumentParserService.java` 这个文件中的代码。

### 解释
这是一个**文档解析服务类**，负责将上传的文件（PDF、Word、TXT等）提取出纯文本内容。它使用了 **Apache Tika** 库来实现多格式文档的统一解析。

#### 整体功能
将各种格式的文档统一转换为纯文本，是 RAG 系统的**数据入口**。后续的**分块、向量化、检索**都依赖于这里提取的文本质量。

---

#### 逐部分解析

**1. 类注解和依赖注入**

```java
@Slf4j
@Service
public class DocumentParserService {
    private final Tika tika = new Tika();
```

- **`@Slf4j`**：Lombok 注解，自动生成日志对象 `log`，用于记录解析过程
- **`@Service`**：Spring 注解，标识这是一个服务层组件，会被 Spring 容器管理
- **`Tika tika`**：Apache Tika 的核心类，用于检测文件类型

---

**2. 主方法：`parse(MultipartFile file)`**

这是对外提供的核心方法，接收上传的文件，返回解析结果。

**执行流程**：

```java
// 第 23-24 行：获取文件基本信息
String originalFilename = file.getOriginalFilename();
long fileSize = file.getSize();
```
- 获取原始文件名和文件大小，用于日志记录和元数据保存

```java
// 第 28 行：检测文件类型
String fileType = tika.detect(originalFilename);
```
- 通过文件扩展名检测 MIME 类型（如 `application/pdf`、`application/vnd.openxmlformats-officedocument.wordprocessingml.document`）
- **作用**：后续可以根据文件类型做不同处理（比如 PDF 和 Word 的解析策略可能不同）

```java
// 第 31 行：提取文本内容
String content = extractText(file);
```
- 调用私有方法 `extractText()` 真正执行文本提取
- 这是最核心的步骤

```java
// 第 35-40 行：构建返回结果
return ParsedDocumentResult.builder()
        .content(content)
        .fileType(fileType)
        .fileName(originalFilename)
        .fileSize(fileSize)
        .build();
```
- 使用 Builder 模式创建 `ParsedDocumentResult` 对象
- 包含：提取的文本、文件类型、文件名、文件大小
- **返回给调用者**（通常是 `DocumentServiceImpl`），用于后续保存到数据库

```java
// 第 42-45 行：异常处理
catch (Exception e) {
    log.error("文档解析失败: {}", file.getOriginalFilename(), e);
    throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
}
```
- 如果解析过程中出错（文件格式损坏、不支持的格式等），记录错误日志并抛出运行时异常
- **注意**：这里抛的是 `RuntimeException`，Spring 的事务管理器会自动回滚

---

**3. 私有方法：`extractText(MultipartFile file)`**

这是真正执行文本提取的核心逻辑。

```java
AutoDetectParser parser = new AutoDetectParser();
```
- **`AutoDetectParser`**：Tika 的自动检测解析器
- **作用**：根据文件内容自动选择合适的解析器（PDF 解析器、Word 解析器等）
- **优势**：不需要手动判断文件类型，Tika 会自动处理

```java
BodyContentHandler handler = new BodyContentHandler(Constants.DOC_PARSE_MAX_CHARS);
```
- **`BodyContentHandler`**：Tika 的内容处理器，负责接收解析出的文本
- **参数 `Constants.DOC_PARSE_MAX_CHARS`**：限制最大解析字符数（500,000 字符）
  - **为什么需要限制？**
    - 防止超大文件（如几百页的 PDF）导致内存溢出
    - 避免解析时间过长阻塞线程
    - 符合 RAG 场景需求（超长文档可以后续分块处理）

```java
Metadata metadata = new Metadata();
ParseContext context = new ParseContext();
```
- **`Metadata`**：存储文档元数据（作者、标题、创建时间等），这里没有使用但可以扩展
- **`ParseContext`**：解析上下文，可以传递自定义配置（如 OCR 引擎、编码等）

```java
try (InputStream inputStream = file.getInputStream()) {
    parser.parse(inputStream, handler, metadata, context);
    return handler.toString();
}
```
- **`try-with-resources`**：自动关闭输入流，防止资源泄漏
- **`parser.parse()`**：执行解析
  - 参数1：输入流（文件内容）
  - 参数2：内容处理器（接收提取的文本）
  - 参数3：元数据（可选）
  - 参数4：解析上下文（可选）
- **`handler.toString()`**：从处理器中获取提取的纯文本

---

#### 设计亮点

**1. 单一职责原则**
- 只负责"解析文档提取文本"，不涉及数据库操作、分块、向量化等其他逻辑
- 符合分层架构设计规范

**2. 支持多格式**
- 通过 Tika 的 `AutoDetectParser`，自动支持 100+ 种格式

**3. 安全性考虑**
- 限制最大解析字符数（500,000），防止恶意大文件攻击
- 使用 `try-with-resources` 确保资源释放

**4. 可观测性**
- 详细的日志记录（开始解析、文件类型、解析成功/失败）
- 方便排查问题和性能监控

---

## 问题 3：分块策略（Chunk Strategy）的作用
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
分块策略（Chunk Strategy）是干什么的？为什么要做分块？

### 解释
分块策略是 RAG 系统中非常关键的环节，决定了如何将长文档切分成合适的小片段（Chunks）。

#### 核心作用

1. **解决上下文窗口限制**
   - LLM（大模型）的输入长度有限。
   - Embedding 模型也有最大 Token 限制（通常 512~8192 tokens）。
   - 无法将整本几百页的 PDF 直接输入模型。

2. **提高检索精度**
   - **小块**：精准匹配问题，但可能丢失上下文。
   - **大块**：保留更多上下文，但可能引入噪声，降低相似度得分。
   - **策略**：寻找平衡点（项目中设置为 300~800 字符）。

3. **保持语义完整性**
   - 避免在句子中间切断。
   - 优先在段落、标题、标点等自然边界处分割。
   - 确保每个 Chunk 表达一个完整的语义单元。

#### 常见分块策略对比

| 策略 | 原理 | 优点 | 缺点 |
|:---|:---|:---|:---|
| **固定大小** | 每 N 个字符切一刀 | 实现简单 | 容易切断语义，效果差 |
| **按段落/标题** | 以段落或章节为界 | 语义完整 | 长度不均匀 |
| **滑动窗口** | 块与块之间重叠部分字符 | 避免边界信息丢失 | 数据冗余 |
| **递归/混合策略** | 先按结构分，再按大小分 | 兼顾结构与语义 | 实现稍复杂 |

#### 项目中的实现

您的项目采用了**混合策略**：
1. 先按段落（`\n\n`）粗分。
2. 如果段落过长（> 800 字符），再按句子（`。！？`）细分。
3. 使用 `StringBuilder` 合并短句，控制每块大小。
4. 保留 **50 字符重叠**，防止语义在边界断裂。

---

## 问题 4：向量化数据的存储架构
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
向量化的数据存储位置在哪？是在 PostgreSQL 中吗？但是由 LangChain4j 管理？

### 解释
是的，向量数据存储在 PostgreSQL 中，但**由 LangChain4j 通过专用表自动管理**。

#### 双表架构设计

项目中存在两套存储体系：

1. **业务数据表（开发者管理）**
   - 表：`documents`（文档元数据）、`document_chunks`（分块文本）
   - 管理方式：MyBatis Plus (`DocumentRepository`)
   - 用途：保存业务数据、答案溯源、展示引用来源。

2. **向量数据表（LangChain4j 管理）**
   - 表：`embeddings`
   - 管理方式：`PgVectorEmbeddingStore` Bean
   - 用途：存储向量并执行相似度检索。
   - 特点：由 LangChain4j 自动建表和维护。

#### 数据流转过程

```text
1. 用户上传 PDF
   ↓
2. Parser 解析出文本
   ↓
3. 存入 documents 表 (MyBatis Plus)
   ↓
4. ChunkService 分块
   ↓
5. 存入 document_chunks 表 (MyBatis Plus)
   ↓
6. EmbeddingService 生成向量
   ↓
7. 存入 embeddings 表 (LangChain4j 管理)
```

#### 代码配置体现

在 `LangChain4jConfig.java` 中：
```java
@Bean
public PgVectorEmbeddingStore embeddingStore() {
    return PgVectorEmbeddingStore.builder()
            .table(table)  // 指定表名为 "embeddings"
            .createTable(true)  // 自动创建表
            .build();
}
```

---

## 问题 5：Java 基础与代码片段解析
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
解释以下代码片段及对象：`Pattern`、`trim()`、`addAll()` 逻辑、`StringBuilder`、`splitLongParagraph` 方法。

### 解释

#### 1. `Pattern` 对象
- **定义**：`Pattern` 是 Java 正则表达式的**编译版本**。
- **作用**：提高性能。避免每次调用 `split()` 时都重新解析正则。
- **用法**：
  ```java
  private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？；\\n])\\s*");
  String[] sentences = SENTENCE_SPLIT.split(text);
  ```

#### 2. `trim()` 方法
- **作用**：去除字符串首尾的空白字符（空格、`	`、`
` 等）。
- **示例**：`"  Hello  ".trim()` → `"Hello"`。
- **场景**：分块时去除多余的空白，节省 Token 并提高整洁度。

#### 3. 为什么使用 `addAll()`？
- **逻辑**：
  ```java
  if (paragraph.length() <= MAX_LENGTH) {
      result.add(paragraph); 
  } else {
      result.addAll(splitLongParagraph(paragraph)); // 长段落切分后返回的是 List
  }
  ```
- **原因**：`splitLongParagraph` 返回的是 `List<String>`（多个小块），`add()` 只能添加单个元素，而 `addAll()` 可以将整个集合的元素一次性加入结果列表。

#### 4. `StringBuilder` 对象
- **定义**：可变的字符序列。
- **优势**：字符串拼接更高效。`String` 每次拼接都会创建新对象，而 `StringBuilder` 在内存中原地修改。
- **常用方法**：`append()`（追加）、`length()`（长度）、`substring()`（截取）。

#### 5. `splitLongParagraph` 方法解析
该方法用于将超长段落切分为符合大小限制的块，并包含**重叠机制**。

**核心逻辑**：
- 先按句子分割段落。
- 使用 `StringBuilder buffer` 作为缓冲区累积句子。
- 当 `buffer` 长度即将超过上限时：
  1. 将 `buffer` 内容作为一个 Chunk 保存。
  2. **取末尾 50 个字符作为重叠部分**。
  3. 将重叠部分与新句子作为新 Chunk 的开头。

**重叠示例**：
```text
Chunk 1: "...学生必须遵守校规，否则"
Chunk 2: "遵守校规，否则将受到处分。"
```
即使只检索到 Chunk 2，也能看到完整的因果关系。

---

## 问题 6：向量表字段关联：embedding_id 与 chunk_db_id
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
截图中的 `embedding_id` 跟 `chunk_db_id` 是什么关系？

### 解释
它们是**不同表的标识 ID**，通过 `metadata` 建立关联。

| 字段 | 含义 | 来源 | 管理方 |
|:---|:---|:---|:---|
| **`embedding_id`** | `embeddings` 表的主键（UUID） | LangChain4j 自动生成 | LangChain4j |
| **`chunk_db_id`** | `document_chunks` 表的主键 ID | 开发者业务逻辑 | MyBatis Plus |

#### 关联方式
`embeddings` 表的 `metadata` 字段（JSONB）中存储了 `chunk_db_id`。

```json
// metadata 内容
{
  "chunk_db_id": "7",  // 指向 document_chunks 表的 ID 7
  "document_id": "7",
  "chunk_index": "0"
}
```

#### 作用
- 检索时，LangChain4j 返回 `embedding_id` 和对应的文本及 metadata。
- 业务层通过 metadata 中的 `chunk_db_id` 去 `document_chunks` 表查询更详细的业务信息（如精确的文档位置、创建时间等）。

---

## 问题 7：chunk_db_id 与 document_id 的作用区别
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
`chunk_id` 跟 `document_id` 的作用是什么？不是一样是溯源吗？

### 解释
它们不是重复的，而是**不同粒度的溯源**，配合使用才能实现完整的引用链。

| ID | 粒度 | 回答的问题 | 示例用途 |
|:---|:---|:---|:---|
| **`document_id`** | 粗（文档级） | "答案来自哪份文件？" | 显示文件名、统计文档引用热度 |
| **`chunk_db_id`** | 细（分块级） | "答案来自文件的哪个具体位置？" | 高亮原文段落、定位章节 |

#### 类比理解
- **`document_id`** = 书名（《Java 核心技术》）
- **`chunk_db_id`** = 页码（第 156 页）

**示例**：
> 答案：图书馆开放时间为 8:00-22:00
> 来源：《图书馆管理规定》（ID: 5）→ 第 2 章 开放时间（Chunk ID: 23）

---

## 问题 8：chunk_index 的作用
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
除了 `document_id` 和 `chunk_db_id`，为什么还有一个 `chunk_index`？

### 解释
`chunk_index` 是**文档内分块的顺序编号**（相对位置）。

#### 三者关系

| 字段 | 含义 | 范围 |
|:---|:---|:---|
| `document_id` | 文档 ID | 全局唯一标识文档 |
| `chunk_db_id` | 分块主键 | 全局唯一标识分块记录 |
| `chunk_index` | 分块索引 | 仅在**当前文档内**有意义 |

#### 作用场景
1. **展示进度**：显示"来源：第 5/12 节"。
2. **定位结构**：用户查看原文时，可以根据 index 快速跳转到对应段落。
3. **排序**：确保召回的多个片段在原文中是按顺序排列的。

#### 示例数据
假设文档 ID=7 被分为 8 块：
```text
document_chunks 表：
ID=7,  doc_id=7, index=0
ID=8,  doc_id=7, index=1
...
ID=11, doc_id=7, index=4  ← 当前查询的块
```

**建议**：在代码中存储 `chunk_index` 时尽量使用 `Integer` 而非 `String`，以便于后续排序和计算。

---

**文档说明：**
- 本文档为 RAG 项目开发经验记录 Part 3
- 记录了 2026-05-13 至 2026-05-14 的技术疑问与解答
- 涵盖文件上传流程、代码详解、分块策略、向量存储架构、溯源设计等核心概念
- 持续更新中...
