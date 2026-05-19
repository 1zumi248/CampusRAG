# RAG 项目开发经验记录 (Part 4)

## 问题 1：JSONUtil.toJsonStr() 方法
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
JSONUtil.toJsonStr(meta) 介绍下这个方法。

### 解释
`JSONUtil.toJsonStr()` 是 **Hutool** 工具库提供的方法，用于将 Java 对象转换为 JSON 字符串。

#### 所属库
```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.26</version>
</dependency>
```

#### 作用与等价方法
```java
Map<String, Object> meta = Map.of(
    "document_id", 1,
    "document_title", "学生手册2025",
    "chunk_index", 3
);

// Hutool 方式（无需 try-catch）
String json = JSONUtil.toJsonStr(meta);
// 输出: {"document_id":1,"document_title":"学生手册2025","chunk_index":3}

// Spring Boot 等价方式（需要 try-catch）
ObjectMapper mapper = new ObjectMapper();
String json2 = mapper.writeValueAsString(meta);

// Fastjson 等价方式
String json3 = JSON.toJSONString(meta);
```

#### 为什么使用 Hutool
- 不需要处理 `JsonProcessingException` 异常
- API 简洁，一行搞定
- Hutool 已经是项目依赖，无需额外引入

#### 使用场景
项目中用于将 `document_chunks` 表的 `metadata` JSONB 字段的 Java 数据转为 JSON 字符串存入数据库。

---

## 问题 2：TextSegment.from() 与 Metadata 的正确用法
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
`TextSegment segment = TextSegment.from(chunk.getContent(), Map.of(...))` 这里报错了，为什么？

### 解释
`TextSegment.from()` 第二个参数要求的是 LangChain4j 的 `Metadata` 类型，不能直接传 `Map`。

#### 错误写法
```java
// ❌ 错误：Map 不能直接传给 TextSegment.from()
TextSegment segment = TextSegment.from(
    chunk.getContent(),
    Map.of(
        "document_id", documentId.toString(),
        "chunk_index", chunk.getChunkIndex().toString()
    )
);
```

#### 正确写法
```java
import dev.langchain4j.data.document.Metadata;

// ✅ 正确：使用 Metadata.from() 包装 Map
TextSegment segment = TextSegment.from(
    chunk.getContent(),
    Metadata.from(Map.of(
            "document_id", documentId.toString(),
            "chunk_index", chunk.getChunkIndex().toString(),
            "chunk_db_id", chunk.getId().toString()
    ))
);
```

#### 说明
- `Metadata` 是 `dev.langchain4j.data.document.Metadata`，本质上是一个 `Map<String, String>` 的封装。
- `Metadata.from(Map)` 是静态工厂方法，将 Map 转为 Metadata 对象。
- `TextSegment` 是 LangChain4j 中表示文本片段 + 元数据的标准对象，用于与 `EmbeddingStore` 配合存储。

---

## 问题 3：ChatModel.chat() 替换 ChatLanguageModel.generate()
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
`ChatLanguageModel` 用不了了，`generate()` 方法也报红，新版本应该怎么调用？

### 解释
LangChain4j 新版本中，`ChatLanguageModel` 已被标记为过时，`ChatModel` 是新的接口，方法从 `generate()` 改为 `chat()`。

#### API 变更

| 旧 API | 新 API |
|--------|--------|
| `ChatLanguageModel` | `ChatModel` |
| `generate(String)` → 返回 `String` | `chat(String)` → 返回 `String` |
| `generate(List<ChatMessage>)` | `chat(ChatMessage...)` → 返回 `ChatResponse` |

#### 代码对比

```java
// ❌ 旧写法（已废弃）
import dev.langchain4j.model.chat.ChatLanguageModel;

@Autowired
private ChatLanguageModel chatLanguageModel;

String answer = chatLanguageModel.generate("你好");

// ✅ 新写法
import dev.langchain4j.model.chat.ChatModel;

@Autowired
private ChatModel chatModel;

String answer = chatModel.chat("你好");  // 直接返回 String
```

#### 注意事项
- `chat(String userMessage)` 直接返回 `String`，不需要 `.content().text()`。
- 如果使用 `chat(ChatMessage...)` 多消息版本，返回的是 `ChatResponse`，需要 `.aiMessage().text()` 获取文本。

---

## 问题 4：PostgreSQL JSONB 列写入报错
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
`ERROR: column "metadata" is of type jsonb but expression is of type character varying` —— 为什么 MyBatis Plus 写入 JSONB 列会报错？

### 解释
Java 的 `String` 类型通过 JDBC 驱动传到 PostgreSQL 时被识别为 `VARCHAR`，PostgreSQL 不接受将 `VARCHAR` 隐式转换为 `JSONB`，需要显式声明类型。

#### 解决方案：自定义 TypeHandler

**创建 JsonbTypeHandler.java**：
```java
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

public class JsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, 
                                     String parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");   // ← 关键：声明类型为 jsonb
        pgObject.setValue(parameter);
        ps.setObject(i, pgObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
```

**在实体类中使用**：
```java
@TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
private String metadata;
```

#### 原理
```
Java String → PGobject(type="jsonb") → PostgreSQL 识别为 JSONB ✅
```

---

## 问题 5：PGobject 编译报错
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
`import org.postgresql.util.PGobject` 编译报红，找不到这个类。

### 解释
`pom.xml` 中 PostgreSQL 驱动被设为 `<scope>runtime</scope>`，编译时不可用。

#### Maven Scope 对比

| scope | 编译时 | 测试时 | 运行时 | 适用场景 |
|-------|--------|--------|--------|----------|
| `compile`（默认） | ✅ | ✅ | ✅ | 大部分依赖 |
| `runtime` | ❌ | ✅ | ✅ | 仅运行时需要的依赖 |
| `test` | ❌ | ✅ | ❌ | 测试专用 |
| `provided` | ✅ | ✅ | ❌ | 容器提供的依赖 |

#### 修复方法
```xml
<!-- ❌ 编译时不可用 -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>  <!-- 编译时不可见 -->
</dependency>

<!-- ✅ 改成默认或显式 compile -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <!-- 不写 scope，默认 compile -->
</dependency>
```

#### 原因
Spring Boot 初始化项目时默认将数据库驱动设为 `runtime`，因为大多数项目只在 yaml 里配置驱动类名（字符串），不需要在 Java 代码里 import 驱动类。但当编写 TypeHandler 使用 `PGobject` 时，必须在编译阶段可见。

---

## 问题 6：向量维度不匹配报错
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
`ERROR: expected 1536 dimensions, not 1024` —— 但我 yaml 里配置的维度是 1024，为什么还报 1536？

### 解释
`PgVectorEmbeddingStore` 的 `createTable: true` 只在**表不存在时**才建表。之前用 1536 维度运行过，`embeddings` 表已经存在且是 1536 维，yaml 改了 1024 但表结构不会自动跟着变。

#### 解决
```sql
-- 删掉旧表，重启应用后 LangChain4j 会用新维度重建
DROP TABLE IF EXISTS embeddings;
```

#### 关键认知
- `createTable: true` = "表不存在→创建"，不是 "每次启动→重建"。
- 改了 `pgvector.dimension` 配置后，需手动删表或执行 `ALTER TABLE embeddings ALTER COLUMN embedding TYPE vector(1024);`。

---

## 问题 7：file_type 列长度不足
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
`ERROR: value too long for type character varying(50)` —— 文件上传报错。

### 解释
Tika 检测出的 MIME 类型字符串很长，超过 `VARCHAR(50)` 的限制。

#### 示例
```
application/vnd.openxmlformats-officedocument.wordprocessingml.document
```
这个字符串 68 个字符，超过了数据库列 `file_type VARCHAR(50)`。

#### 修复
```sql
ALTER TABLE documents ALTER COLUMN file_type TYPE VARCHAR(255);
```

#### 预防
后续建表时，`file_type` 字段直接用 `VARCHAR(255)` 而非 `VARCHAR(50)`。MIME 类型字符串可能很长，255 是安全的默认值。

---

## 问题 8：文档服务接口设计模式
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
interface + 实现类的模式有必要吗？什么时候该用？

### 解释
**当一个实现就够用时，接口是多余抽象；当有多种实现或将来需要替换时，接口才有意义。**

#### 判断标准

| 场景 | 是否需要接口 |
|------|-------------|
| 只有一个实现，未来不会换 | ❌ 不需要 |
| 有多种实现（如多支付渠道） | ✅ 需要 |
| 需要 Mock 做单元测试 | ⚠️ Mockito 可直接 Mock 实现类 |
| 需要 JDK 动态代理（AOP） | ✅ Spring AOP 对接口更友好 |

#### 项目中的实际决策

| 服务 | 有无多实现 | 用接口？ |
|------|-----------|---------|
| `DocumentService` | 无 | 已写，不改动 |
| `ChunkService` | 无 | 直接用 @Service 类 |
| `EmbeddingService` | 无 | 直接用 @Service 类 |
| `RetrievalService`（后续） | 可能有（关键词 vs 向量） | 可以考虑接口 |

---

## 问题 9：`@Service` 注解误放在接口上
**提出时间：** 2026-05-14  
**提出次数：** 1

### 问题
接口上写了 `@Service` 注解会怎样？

### 解释
Spring 的 `@Service` 注解应该放在**实现类**上，不是接口上。放在接口上不会报错，但被 Spring 忽略（接口无法实例化），真正的 Bean 注册靠实现类上的注解。

```java
// ❌ 不需要，会被忽略
@Service
public interface DocumentService { ... }

// ✅ 实现类上的 @Service 才是生效的
@Service
public class DocumentServiceImpl implements DocumentService { ... }
```

---
**文档说明：**
- 本文档为 RAG 项目开发经验记录 Part 4
- 记录了 2026-05-14 的技术疑问与解答
- 涵盖 JSON 序列化、LangChain4j API 变更、PostgreSQL JSONB 处理、Maven 依赖管理、向量维度配置、接口设计模式等
- 持续更新中...
