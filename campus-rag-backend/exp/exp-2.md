# RAG 项目开发经验记录 (Part 2)

## 问题 1：Spring Data JPA vs MyBatis Plus 的选择
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
Spring Data JPA 这个有必要吗？我依赖应该是都导入好了。

### 解释
您的项目已经使用了 **MyBatis Plus**，不需要 Spring Data JPA。两者都是 ORM 框架，不能共存。

#### 技术选型对比
| 特性 | MyBatis Plus | Spring Data JPA |
|------|-------------|-----------------|
| SQL 控制 | 灵活，可写原生 SQL | 自动生成，灵活性较低 |
| 学习曲线 | 较低（熟悉 MyBatis） | 较高（需理解 JPA 规范） |
| 性能优化 | 容易优化 | 需要理解懒加载、缓存等 |
| 社区生态 | 国内活跃 | 国际主流 |

#### 实际使用示例
```java
// MyBatis Plus - 继承 BaseMapper 即可获得 CRUD
@Mapper
public interface DocumentRepository extends BaseMapper<Document> {
    // 无需写任何方法，自动获得：
    // insert(), updateById(), deleteById(), selectById(), selectList()...
}

// 使用时直接注入
@Autowired
private DocumentRepository documentRepository;

// 调用内置方法
documentRepository.insert(document);
documentRepository.selectById(1L);
```

---

## 问题 2：LangChain4j 管理向量的含义
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
"向量由 LangChain4j 自动管理"没看懂，什么叫由 langchain4j 管理，怎么进行管理，以及不应该需要实现持久化吗？

### 解释
LangChain4j 提供了 `EmbeddingStore` 抽象层，负责向量的存储、检索和持久化。

#### 核心概念
**LangChain4j 管理 = LangChain4j 自动处理：**
1. 向量存储到数据库
2. 向量相似度检索
3. 表结构自动创建
4. 类型转换（vector 类型）

#### 双表架构设计
```
业务表（MyBatis Plus 管理）          向量表（LangChain4j 管理）
┌─────────────────────┐            ┌────────────────────────┐
│ documents           │            │ embeddings             │
│ - id                │            │ - embedding_id (UUID)  │
│ - title             │            │ - embedding (vector)   │
│ - content           │            │ - text (文本内容)       │
│ - content_hash      │            │ - metadata (JSONB)     │
└─────────────────────┘            └────────────────────────┘

┌─────────────────────┐
│ document_chunks     │
│ - id                │
│ - document_id       │
│ - chunk_index       │
│ - content           │
│ - metadata          │
│ ❌ 无 embedding 字段 │
└─────────────────────┘
```

#### 实际使用示例
```java
@Service
public class DocumentService {
    
    @Autowired
    private PgVectorEmbeddingStore embeddingStore;  // LangChain4j 管理
    
    public void saveChunk(String content, Long docId) {
        // 1. 保存到您的业务表
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent(content);
        chunk.setDocumentId(docId);
        documentChunkRepository.insert(chunk);
        
        // 2. 保存到 LangChain4j 向量库（自动持久化到 embeddings 表）
        Embedding embedding = embeddingModel.embed(content).content();
        Metadata metadata = Metadata.from(Map.of("documentId", docId));
        TextSegment segment = TextSegment.from(content, metadata);
        embeddingStore.add(embedding, segment);  // ← 自动持久化
    }
    
    public List<String> search(String query) {
        // 向量检索（LangChain4j 自动执行 SQL）
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> matches = 
            embeddingStore.findRelevant(queryEmbedding, 5);
        
        return matches.stream()
            .map(match -> match.embedded().text())
            .collect(Collectors.toList());
    }
}
```

#### 持久化说明
- ✅ **数据持久化到磁盘**：向量存储在 PostgreSQL 的 embeddings 表中
- ✅ **重启不丢失**：因为数据在数据库中
- ✅ **支持事务**：可以和其他数据库操作放在同一事务

---

## 问题 3：MyBatis Plus Mapper 是否需要实现类
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
`@Autowired private DocumentRepository documentRepository;` 这个会需要一个实现类吗？

### 解释
**不需要！** MyBatis Plus 通过动态代理自动生成实现类。

#### 工作原理
```java
// 您只需定义接口
@Mapper
public interface DocumentRepository extends BaseMapper<Document> {
    // 无需实现类
}

// Spring 启动时：
// 1. 扫描 @Mapper 注解
// 2. 为接口创建动态代理对象
// 3. 自动实现所有 CRUD 方法
// 4. 注册到 Spring 容器

// 使用时直接注入
@Autowired
private DocumentRepository documentRepository;  // ← 注入的是代理对象
```

#### 对比传统 MyBatis
```xml
<!-- 传统 MyBatis 需要 XML -->
<mapper namespace="com.xxx.DocumentMapper">
    <select id="selectById" resultType="Document">
        SELECT * FROM documents WHERE id = #{id}
    </select>
</mapper>

<!-- MyBatis Plus 无需 XML -->
<!-- BaseMapper 已提供所有 CRUD -->
```

---

## 问题 4：YAML 配置层级差异的原因
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
为什么 yaml 里的配置，有些是跟 spring 同级的（mp, langchain4j），有些配置不是（servlet, datasource）？

### 解释
配置层级取决于**配置的归属方**。

#### 配置分类
**1. Spring 框架管理的配置（在 `spring:` 下）**
```yaml
spring:
  datasource:        # ← Spring Boot 自动配置
  data:
    redis:          # ← Spring Data Redis
  servlet:
    multipart:      # ← Spring MVC
```

**2. 第三方库的配置（与 `spring:` 同级）**
```yaml
mybatis-plus:       # ← MyBatis Plus 自己的配置
  configuration:

langchain4j:        # ← LangChain4j 自己的配置
  open-ai:

pgvector:           # ← 自定义配置
  host: localhost
```

#### 读取方式对比
| 配置项 | 位置 | 谁读取 |
|--------|------|--------|
| `spring.datasource.url` | spring 下 | Spring Boot 自动配置类 |
| `mybatis-plus.configuration` | 顶级 | MyBatis Plus 配置类 |
| `langchain4j.open-ai.api-key` | 顶级 | LangChain4j Starter |
| `pgvector.host` | 顶级 | 您的 `@Value` 注解 |

---

## 问题 5：LangChain4jConfig 配置类的作用
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
再解释下 LangChain4jConfig.java 这个文件的作用。

### 解释
该配置类负责创建并注册 `PgVectorEmbeddingStore` Bean，作为向量存储的核心组件。

#### 核心作用
1. **连接配置的桥梁**：从 YAML 读取配置 → 创建对象 → 注册到 Spring 容器
2. **自动创建向量表**：首次启动时自动创建 embeddings 表
3. **单例管理**：整个应用只有一个 embeddingStore 实例

#### 工作流程
```
启动应用
   ↓
Spring 扫描 @Configuration
   ↓
发现 LangChain4jConfig
   ↓
读取 @Value 配置
   ↓
执行 @Bean 方法
   ↓
创建 PgVectorEmbeddingStore 对象
   ↓
连接 PostgreSQL
   ↓
检查 embeddings 表是否存在
   ↓
如果不存在 → 自动创建表 + 索引
   ↓
注册到 Spring 容器
   ↓
其他 Service 可以 @Autowired 注入使用
```

#### 实际使用示例
```java
@Configuration
public class LangChain4jConfig {
    
    @Value("${pgvector.host}")
    private String host;
    
    @Bean
    public PgVectorEmbeddingStore embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(5432)
                .database("campus_rag")
                .user("postgres")
                .password("123456")
                .table("embeddings")
                .dimension(1024)
                .createTable(true)  // ⭐ 自动创建表
                .build();
    }
}

// 在其他地方注入使用
@Service
public class RagService {
    @Autowired
    private PgVectorEmbeddingStore embeddingStore;  // ← 直接使用
}
```

---

## 问题 6：Spring Boot 3 与 MyBatis Plus 兼容性问题
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
`Invalid bean definition with name 'documentChunkRepository'`: Invalid value type for attribute 'factoryBeanObjectType': java.lang.String 这是为什么，为什么非法？

### 解释
这是 **Spring Boot 3.x 与 MyBatis Plus 版本不兼容**导致的。

#### 根本原因
Spring Boot 3.x 从 Java EE 迁移到 Jakarta EE：
- `javax.*` → `jakarta.*`

MyBatis Plus 提供了两个不同的 starter：
- `mybatis-plus-boot-starter` → 仅支持 Spring Boot 2.x
- `mybatis-plus-spring-boot3-starter` → 支持 Spring Boot 3.x ✅

#### 解决方案
```xml
<!-- ❌ 错误：Spring Boot 3.x 不能使用这个 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.16</version>
</dependency>

<!-- ✅ 正确：必须使用 Spring Boot 3 专用 starter -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>
```

---

## 问题 7：阿里云通义千问模型配置
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
现在我要调整下大模型的设置，检查下目前的 langchain4j 设置。

### 解释
您使用的是阿里云 DashScope（通义千问）的最新模型。

#### 当前配置
```yaml
langchain4j:
  open-ai:
    api-key: ${DASHSCOPE_API_KEY:sk-xxx}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    chat-model:
      model-name: qwen3.6-flash      # 最新聊天模型
      temperature: 0.7
      timeout: PT30S
    embedding-model:
      model-name: text-embedding-v4  # 最新向量模型
      timeout: PT30S

pgvector:
  dimension: 1024  # text-embedding-v4 默认维度
```

#### 模型说明
| 模型类型 | 模型名称 | 维度 | 特点 |
|---------|---------|------|------|
| 聊天模型 | `qwen3.6-flash` | - | 最新快速版本，性价比高 |
| 向量模型 | `text-embedding-v4` | 1024 | Qwen3-Embedding 系列，支持 100+ 语种 |

#### text-embedding-v4 支持的维度
- 可选：2048、1536、**1024（默认）**、768、512、256、128、64
- 默认使用 1024 维，无需额外配置

---

## 问题 8：文档解析大小限制的实现
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
修复 `BodyContentHandler handler = new BodyContentHandler(-1)`，用一个常量来限制上传的文件大小。

### 解释
需要添加文件大小限制，防止超大文件导致内存溢出。

#### 初始实现（硬编码）
```java
// ❌ 不推荐：硬编码
private static final int MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
BodyContentHandler handler = new BodyContentHandler(MAX_DOCUMENT_SIZE);
```

#### 改进方案（配置优先）
```java
// ✅ 推荐：从配置文件读取
@Value("${spring.servlet.multipart.max-file-size}")
private String maxFileSizeStr;

private int parseSize(String sizeStr) {
    if (sizeStr == null || sizeStr.isEmpty()) {
        return Constants.DEFAULT_MAX_PARSE_SIZE;
    }
    
    sizeStr = sizeStr.toUpperCase().trim();
    
    if (sizeStr.endsWith("MB")) {
        long mb = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2));
        return (int) (mb * 1024 * 1024);
    } else if (sizeStr.endsWith("KB")) {
        long kb = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2));
        return (int) (kb * 1024);
    } else {
        return Integer.parseInt(sizeStr);
    }
}

// 使用
BodyContentHandler handler = new BodyContentHandler(parseSize(maxFileSizeStr));
```

#### 配置示例
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB      # ← 只需改这里
      max-request-size: 50MB
```

#### 优势
- ✅ **单一配置源**：上传限制和解析限制保持一致
- ✅ **灵活格式**：支持 "50MB"、"1024KB"、"1GB" 等
- ✅ **无需重复**：不需要维护两个相同的配置项

---

## 问题 9：parseSize 方法的实现逻辑
**提出时间：** 2026-05-13  
**提出次数：** 1

### 问题
解释下 `parseSize` 方法。

### 解释
该方法将 Spring 配置中的文件大小字符串转换为字节数。

#### 为什么需要这个方法？
- Spring 配置格式：`"50MB"`（字符串）
- Tika 要求：`52428800`（int 字节数）
- 需要转换！

#### 实现逻辑
```java
private int parseSize(String sizeStr) {
    // 1. 空值检查
    if (sizeStr == null || sizeStr.isEmpty()) {
        return Constants.DEFAULT_MAX_PARSE_SIZE;  // 50MB
    }
    
    // 2. 统一格式
    sizeStr = sizeStr.toUpperCase().trim();  // "50mb " → "50MB"
    
    // 3. 根据单位转换
    if (sizeStr.endsWith("MB")) {
        long mb = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2));
        return (int) (mb * 1024 * 1024);  // 50 × 1024 × 1024
        
    } else if (sizeStr.endsWith("KB")) {
        long kb = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2));
        return (int) (kb * 1024);
        
    } else if (sizeStr.endsWith("GB")) {
        long gb = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2));
        return (int) (gb * 1024 * 1024 * 1024);
        
    } else {
        // 无单位，直接按字节处理
        return Integer.parseInt(sizeStr);
    }
}
```

#### 转换示例
| 输入 | 输出（字节） | 计算过程 |
|------|------------|---------|
| `"50MB"` | 52,428,800 | 50 × 1024 × 1024 |
| `"1024KB"` | 1,048,576 | 1024 × 1024 |
| `"1GB"` | 1,073,741,824 | 1 × 1024³ |
| `"52428800"` | 52,428,800 | 直接解析 |
| `null` | 52,428,800 | 默认值 |

#### 注意事项
**为什么用 `long` 而不是 `int`？**
```java
long mb = Long.parseLong(...);  // 先用 long 计算
return (int) (mb * 1024 * 1024);  // 最后转 int
```
- 防止中间计算溢出
- 例如：50 × 1024 × 1024 可能超出 int 范围

---

## 附录：本次开发完成的功能清单

### 1. 基础架构搭建
- ✅ 实体类：`Document.java`, `DocumentChunk.java`
- ✅ Repository：`DocumentRepository`, `DocumentChunkRepository`
- ✅ 配置类：`LangChain4jConfig.java`

### 2. 文档解析功能
- ✅ `DocumentParserService.java` - 文档解析服务
- ✅ `ParsedDocumentResult.java` - 解析结果封装
- ✅ 集成 Apache Tika
- ✅ 文件大小限制（可配置）

### 3. 业务逻辑层
- ✅ `DocumentService.java` - 接口定义
- ✅ `DocumentServiceImpl.java` - 业务实现
- ✅ 文档上传、查询、删除功能

### 4. REST API
- ✅ `DocumentController.java` - 控制器
- ✅ POST `/api/documents/upload` - 上传文档
- ✅ GET `/api/documents` - 获取列表
- ✅ GET `/api/documents/{id}` - 查询单个
- ✅ DELETE `/api/documents/{id}` - 删除文档

### 5. 通用组件
- ✅ `Result.java` - 统一响应封装
- ✅ `Constants.java` - 系统常量定义

### 6. 依赖配置
- ✅ MyBatis Plus Spring Boot 3 Starter
- ✅ LangChain4j OpenAI Starter
- ✅ LangChain4j pgvector
- ✅ Apache Tika
- ✅ Hutool 工具库

---

**文档说明：**
- 本文档为 RAG 项目开发经验记录 Part 2
- 记录了 2026-05-13 的技术疑问与解答
- 涵盖架构设计、依赖配置、代码实现等方面
- 持续更新中...
