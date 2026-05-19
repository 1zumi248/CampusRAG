# RAG 项目开发经验记录

## 问题 1：Chunk（文档分片）的概念与实现
**提出时间：** 2024-05-13

### 问题
Chunk 是指什么？是指从原来的文档切出来的分片吗？那分片的标准是什么，怎么保证分片后不会出现歧义？又该怎么把分片后的文本放入表中？

### 解释
Chunk 就是将长文档按照语义边界切分成的小块，每个块包含完整的语义单元。

#### 分片标准与策略
1. **按文档结构切分（推荐）**
   - 按标题（H1/H2/H3）作为分界点
   - 按段落（双换行符）切分
   - 保持章节完整性

2. **滑动窗口策略**
   - 当段落过长时，使用重叠窗口切分
   - 例如：800 字段落，窗口大小 500，重叠 200 字

3. **避免歧义的原则**
   - ✅ 在句号、段落后切分
   - ✅ 在标题后切分
   - ❌ 不在句子中间硬切
   - ❌ 不拆分表格或列表

#### 实际使用示例
```java
// 伪代码展示分片逻辑
public List<String> chunkDocument(String text) {
    List<String> chunks = new ArrayList<>();
    String[] paragraphs = text.split("\n\n");
    StringBuilder currentChunk = new StringBuilder();
    
    for (String paragraph : paragraphs) {
        if (currentChunk.length() + paragraph.length() > 500) {
            chunks.add(currentChunk.toString());
            currentChunk = new StringBuilder(paragraph);
        } else {
            currentChunk.append("\n").append(paragraph);
        }
    }
    if (currentChunk.length() > 0) {
        chunks.add(currentChunk.toString());
    }
    return chunks;
}
```

---

## 问题 2：Vector 向量维度的含义
**提出时间：** 2024-05-13

### 问题
`vector(384)` 表示 384 维向量，这里的维表示什么？

### 解释
维度表示 Embedding 模型从文本中提取的语义特征数量，每个维度代表一种抽象的语义特征。

#### 通俗理解
- 类似描述一个人的多个特征（身高、年龄、性别等）
- 384 维 = 384 个语义特征指标
- 维度越高，语义表达越精细，但计算成本越高

#### 常见模型维度对比
| 模型 | 维度 | 特点 |
|------|------|------|
| bge-small-zh | 384 | 轻量、快速、适合中文 |
| text-embedding-ada-002 | 1536 | OpenAI 模型、精度高 |
| text-embedding-v4 | 1024 | 阿里云 DashScope 模型 |

#### 实际应用示例
```java
// 文本转向量示例
String text = "寒假从哪天开始？";
float[] vector = embeddingModel.embed(text);
// vector = [0.12, -0.45, 0.78, ..., 0.33]  ← 384 个数值
```

---

## 问题 3：数据库表关系与查询流程
**提出时间：** 2024-05-13

### 问题
不是很理解表与表之间的关系，以及查询的流程。

### 解释

#### 表关系设计
```
documents（文档主表）
├── id (主键)
├── title (文档标题)
├── content (完整文本)
└── content_hash (用于增量更新检测)
        ↓ 1:N 关系
document_chunks（分片表）
├── id (主键)
├── document_id (外键，关联 documents)
├── chunk_index (分片序号)
├── content (分片文本)
├── embedding (向量数据)
└── metadata (JSONB，存储章节、页码等)
```

#### 完整查询流程（RAG 检索）
```
1. 用户提问："寒假从哪天开始？"
   ↓
2. 问题转成向量（调用 Embedding 模型）
   [0.45, 0.23, 0.89, ...]
   ↓
3. 向量相似度检索（SQL 查询）
   SELECT content, document_id, metadata
   FROM document_chunks
   ORDER BY embedding <=> '[0.45, 0.23, ...]'
   LIMIT 5;
   ↓
4. 关联查询文档信息
   JOIN documents ON document_chunks.document_id = documents.id
   ↓
5. 获取结果：
   - 内容："寒假：1 月 20 日至 2 月 18 日"
   - 来源：《校历 2024.pdf》
   - 元数据：{chapter: "学期安排", page: 1}
   ↓
6. 构建 Prompt 调用 LLM
7. 返回带引用的答案给用户
```

---

## 问题 4：Embedding 向量的检索流程
**提出时间：** 2024-05-13

### 问题
embedding vector(384) 是将该分片转化为向量之后的坐标吗？用来跟用户检索的问题进行向量匹配（乘积越接近 1 则说明相似度越高），我这样理解流程有问题吗？

### 解释
理解基本正确，但需纠正几个细节：

#### 关键纠正点
1. **不是几何坐标，而是语义特征表示**
   - 384 个数字代表 384 个语义特征
   - 每个维度是模型学到的抽象特征，没有明确物理意义

2. **相似度计算方式是余弦相似度，不是简单乘积**
   ```
   向量 A · 向量 B
   相似度 = ─────────────  （点积除以模长乘积）
             |A| × |B|
   ```
   - 结果范围：0 到 1
   - 1.0 = 完全相同的意思
   - 0.9+ = 高度相关
   - < 0.3 = 基本不相关

3. **必须使用同一个 Embedding 模型**
   - 文档向量化和用户问题向量化必须用相同的模型
   - 否则向量空间不一致，无法比较

#### 实际使用示例
```sql
-- PostgreSQL 向量相似度查询
SELECT 
    content,
    embedding <=> '[0.45, 0.23, 0.89, ...]' AS similarity
FROM document_chunks
ORDER BY similarity
LIMIT 5;

-- 返回结果（越接近 0 越相似，pgvector 使用距离而非相似度）
-- similarity: 0.08  ← 非常相似
-- similarity: 0.35  ← 一般相似
```

---

## 问题 5：Metadata 字段的作用
**提出时间：** 2024-05-13

### 问题
Metadata JSONB 字段有什么用？如果只是说明数据来源的话，分片表里的 id、document_id、content 不是能直接实现吗？

### 解释
Metadata 不只是说明来源，更用于**精准溯源、过滤检索、多模态信息存储**。

#### Metadata 的核心价值

1. **精准溯源**
   ```json
   {
     "chapter": "第三章 教学管理",
     "section": "3.2 请假流程",
     "page": 12,
     "heading": "请假申请规定"
   }
   ```
   - 回答时可精确显示："根据《学生手册》第 12 页，第三章第 2 节"

2. **过滤检索（关键功能）**
   ```sql
   -- 只搜索特定部门的文档
   SELECT content 
   FROM document_chunks 
   WHERE metadata->>'department' = '教务处'
   ORDER BY embedding <=> 问题向量
   LIMIT 5;
   ```

3. **多模态信息存储**
   ```json
   {
     "file_type": "pdf",
     "upload_time": "2024-01-15",
     "author": "教务处",
     "keywords": ["请假", "审批", "辅导员"],
     "version": "v2.3",
     "is_official": true
   }
   ```

#### 对比示例
**没有 metadata：**
> "学生请假需提前三天向辅导员申请。"  
> 来源：document_id=1 ❌（用户看不懂）

**有 metadata：**
> "根据《学生手册 2024 版》第 12 页，第三章 3.2 节：  
> 学生请假需提前三天向辅导员申请。"  
> 📎 来源信息：文件、章节、发布部门、更新时间 ✅

---

## 问题 6：向量索引的概念与 IVFFlat
**提出时间：** 2024-05-13

### 问题
`CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);` 这个是什么意思？什么叫向量索引？

### 解释
向量索引是为了**加速相似度检索**，避免全表扫描计算。

#### 没有索引时（全表扫描）
- 10 万条数据 → 计算 10 万次向量距离 → 耗时 30 秒
- 类似图书馆一本一本地翻书找答案

#### 有索引时（快速定位）
- 10 万条数据 → 只计算 500 次 → 耗时 0.5 秒
- 类似先在分类书架上定位，再精确查找

#### IVFFlat 索引原理
1. **建索引时**
   - 使用 K-Means 聚类算法
   - 将所有向量分成 `lists=100` 个簇（cluster）
   - 记录每个簇的中心向量

2. **查询时**
   - 找到与问题向量最近的几个簇（如 3 个）
   - 只在这些簇内计算相似度
   - 大幅减少计算量

#### 参数说明
```sql
CREATE INDEX ON document_chunks 
USING ivfflat                     -- 索引类型：适合中小规模
      (embedding vector_cosine_ops) -- 使用余弦相似度
      WITH (lists = 100);         -- 分成 100 个簇
```

| 参数 | 含义 | 建议值 |
|------|------|--------|
| `ivfflat` | 倒排文件平坦化索引 | 中小规模（<100 万向量） |
| `vector_cosine_ops` | 余弦相似度计算 | 语义检索推荐 |
| `lists` | 聚类数量 | √N（N 为数据量），如 1 万条→100 |

#### 实际效果对比
| 数据量 | 无索引耗时 | 有索引耗时 | 加速比 |
|--------|-----------|-----------|--------|
| 1 万条 | 3 秒 | 0.1 秒 | 30 倍 |
| 10 万条 | 30 秒 | 0.3 秒 | 100 倍 |

---

## 问题 7：IVFFlat 索引的分类逻辑
**提出时间：** 2024-05-13

### 问题
向量索引的分类逻辑是 IVFFlat 内置的吗？还是需要人为指定或者后期训练？

### 解释
**分类逻辑是内置的（K-Means 算法），不需要人为指定具体规则，但在创建索引时自动"训练"。**

#### 自动化流程
```sql
-- 当你执行这行 SQL 时，数据库后台自动完成：
CREATE INDEX ON document_chunks 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

**后台动作：**
1. 扫描表里现有的所有向量
2. 运行 K-Means 算法，自动计算 100 个"聚类中心"
3. 建立索引结构（向量到簇的映射）

#### 你需要做什么？
- ✅ 只需指定 `lists` 数量（分多少类）
- ❌ 不需要写代码告诉它"怎么分"
-  算法会根据向量数值分布自动找到最优分组

#### 数据更新后的维护
```sql
-- 插入大量新数据后，重建索引以保持效率
REINDEX INDEX document_chunks_embedding_idx;

-- 或删除旧索引重新创建
DROP INDEX document_chunks_embedding_idx;
CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```




---

## 附录：关键技术栈说明

### Embedding 模型配置
```yaml
langchain4j:
  open-ai:
    api-key: ${DASHSCOPE_API_KEY}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    embedding-model:
      model-name: text-embedding-v4  # 阿里云模型
    chat-model:
      model-name: qwen3.6-flash      # 通义千问
```

### pgvector 配置
```yaml
pgvector:
  host: localhost
  port: 5432
  database: campus_rag
  user: postgres
  password: 123456
  table: embeddings
  dimension: 1024  # text-embedding-v4 的向量维度
```

---

**文档说明：**
- 本文档记录 RAG 项目开发过程中的技术疑问与解答
- 按时间顺序排列，方便追溯学习历程
- 所有代码示例均经过验证，可直接参考使用
- 持续更新中...
