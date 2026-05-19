# 项目待改进项记录

本文档记录项目中发现的潜在问题和优化建议，按优先级排序，供后续迭代参考。

---

## 🔴 高优先级（影响核心功能）

### 1. 文件类型校验缺失
**位置**: `DocumentParserService.parse()`  
**问题**: 当前没有限制允许上传的文件类型，可能接受任意格式文件  
**风险**: 
- 用户上传不支持的格式导致解析失败
- 恶意文件上传安全风险

**建议方案**:
```java
// 在 parse 方法开头添加白名单校验
private static final List<String> ALLOWED_TYPES = Arrays.asList(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
    "text/markdown",
    "text/html"
);

if (!ALLOWED_TYPES.contains(fileType)) {
    throw new IllegalArgumentException("不支持的文件类型: " + fileType);
}
```

**预计工作量**: 30分钟

---

### 2. 空文件名处理
**位置**: `DocumentParserService.parse()`  
**问题**: `file.getOriginalFilename()` 可能返回 `null`，导致 `tika.detect()` 出错  

**建议方案**:
```java
if (originalFilename == null || originalFilename.isEmpty()) {
    throw new IllegalArgumentException("文件名不能为空");
}
```

**预计工作量**: 10分钟

---

### 3. 删除文档时未清理关联数据
**位置**: `DocumentServiceImpl.deleteDocument()`  
**问题**: 只删除了 `documents` 表记录，未清理：
- `document_chunks` 表中的分片记录
- pgvector 中的向量数据

**风险**: 
- 数据不一致
- 向量库中存在孤立向量，影响检索准确性

**建议方案**:
```java
@Override
public void deleteDocument(Long id) {
    // 1. 查询该文档的所有 chunks
    List<DocumentChunk> chunks = chunkRepository.selectByDocumentId(id);
    
    // 2. 从 pgvector 中删除对应的向量
    for (DocumentChunk chunk : chunks) {
        embeddingStore.remove(chunk.getId().toString());
    }
    
    // 3. 删除 chunks 表记录
    chunkRepository.deleteByDocumentId(id);
    
    // 4. 删除 documents 表记录
    documentRepository.deleteById(id);
    
    log.info("文档及其关联数据已删除: ID={}", id);
}
```

**依赖**: 需要先实现 `ChunkRepository` 和注入 `PgVectorEmbeddingStore`  
**预计工作量**: 1小时

---

## 🟡 中优先级（性能和体验优化）

### 4. AutoDetectParser 性能优化
**位置**: `DocumentParserService.extractText()`  
**问题**: 每次调用都创建新的 `AutoDetectParser` 实例，开销较大  

**建议方案**:
- 方案A: 将 `AutoDetectParser` 改为单例（类级别常量）
- 方案B: 通过 Spring 注入为 Bean

```java
// 方案A示例
private static final AutoDetectParser PARSER = new AutoDetectParser();

private String extractText(MultipartFile file) throws Exception {
    BodyContentHandler handler = new BodyContentHandler(Constants.DOC_PARSE_MAX_CHARS);
    Metadata metadata = new Metadata();
    ParseContext context = new ParseContext();

    try (InputStream inputStream = file.getInputStream()) {
        PARSER.parse(inputStream, handler, metadata, context);
        return handler.toString();
    }
}
```

**预计工作量**: 20分钟

---

### 5. 元数据未充分利用
**位置**: `DocumentParserService.extractText()`  
**问题**: Tika 提取的 `Metadata` 对象包含丰富信息（作者、标题、页数、创建时间等），但当前未使用  

**价值**: 
- 可用于文档分类和检索增强
- 提升用户体验（展示文档详细信息）

**建议方案**:
1. 在 `Document` 表中添加元数据字段（如 `author`, `page_count`, `created_date`）
2. 在 `ParsedDocumentResult` 中增加 `Map<String, String> metadata` 字段
3. 解析时提取关键元数据并保存

```java
// 提取常见元数据
String author = metadata.get("Author");
String title = metadata.get("title");
String pageCount = metadata.get("xmpTPg:NPages");
```

**预计工作量**: 2小时

---

### 6. 缺少文件大小校验
**位置**: `DocumentController.uploadDocument()`  
**问题**: 虽然 `application.yaml` 配置了 `max-file-size: 50MB`，但业务层未做二次校验  

**建议方案**:
```java
@PostMapping("/upload")
public Result<Document> uploadDocument(@RequestParam("file") MultipartFile file) {
    // 业务层校验
    if (file.getSize() > 50 * 1024 * 1024) {
        return Result.error(400, "文件大小不能超过50MB");
    }
    
    return Result.ok(documentService.uploadDocument(file));
}
```

**预计工作量**: 15分钟

---

## 🟢 低优先级（长期优化）

### 7. 异步解析支持
**位置**: `DocumentServiceImpl.uploadDocument()`  
**问题**: 大文件解析+分块+向量化可能耗时较长（数十秒），阻塞 HTTP 请求  

**建议方案**:
- 使用 Spring `@Async` 异步处理
- 返回任务ID，前端轮询查询处理状态
- 或使用消息队列（RabbitMQ/Kafka）解耦

**适用场景**: 当系统需要处理大量大文件时  
**预计工作量**: 4小时

---

### 8. 解析进度反馈
**位置**: 整个上传流程  
**问题**: 用户无法感知处理进度（解析→分块→向量化）  

**建议方案**:
- 使用 WebSocket 推送进度
- 或在 Redis 中存储进度，前端轮询

**预计工作量**: 3小时

---

### 9. OCR 支持（扫描版PDF）
**位置**: `DocumentParserService`  
**问题**: 当前 Tika 无法识别扫描版PDF中的图片文字  

**建议方案**:
- 集成 Tesseract OCR 引擎
- 检测PDF是否为扫描件（通过元数据或图像密度）
- 对扫描件启用OCR处理

**注意**: 会显著增加处理时间和资源消耗  
**预计工作量**: 1天

---

### 10. 表格和图表特殊处理
**位置**: `DocumentParserService`  
**问题**: Tika 提取表格时会丢失结构信息，转为纯文本后难以理解  

**建议方案**:
- 使用专门的表格解析库（如 Tabula for PDF）
- 将表格转换为 Markdown 格式或 CSV
- 为表格添加特殊标记，便于后续检索

**预计工作量**: 1天

---

## 📊 统计汇总

| 优先级 | 数量 | 预计总工作量 |
|--------|------|-------------|
| 🔴 高   | 3    | ~1.5小时     |
| 🟡 中   | 3    | ~2.5小时     |
| 🟢 低   | 4    | ~2.5天       |

---

## 🎯 建议实施顺序

1. **立即修复**（本周内）:
   - ✅ 第1项：文件类型校验
   - ✅ 第2项：空文件名处理
   - ✅ 第6项：文件大小校验

2. **核心功能完成后**（下周）:
   - ⏳ 第3项：删除文档时清理关联数据
   - ⏳ 第4项：AutoDetectParser 性能优化

3. **长期优化**（有空闲时）:
   - ⏳ 第5项：元数据利用
   - ⏳ 其他低优先级项

---

## 🚀 竞争力升级方向（2026 校招视角）

> 2026 年纯 RAG 项目已不算新颖，需通过技术深度和场景复杂度拉开差距。以下三个方向与现有栈差距最小，优先推荐。

### 11. RAG → RAG Agent（多步推理）
**优先级**: ⭐⭐⭐ 最高推荐
**现状**: 当前系统是"单次检索→生成"，无法处理需要多步推理的问题
**升级目标**: 让系统自动判断是否需要二次检索、是否需要调用外部接口（如课表 API）

**涉及改动**:
- LangChain4j 已支持 Tool Calling，可定义 `SearchTool`、`QueryApiTool` 等工具
- `ChatService` 从单轮调用改为 Agent 循环（观察→思考→行动→观察...）
- Prompt 设计增加"你是否需要更多信息来回答？"的判断引导

**竞争力提升**: 从"我做了一个 RAG"变成"我实现了多步推理 Agent"，层级明显提升
**学习增量**: LangChain4j 的 `@Tool` 注解和 AiService Agent 模式，约 2-3 小时上手
**预计工作量**: 4-6 小时

---

### 12. 流式响应（SSE + 渐进渲染）
**优先级**: ⭐⭐⭐
**现状**: 答案一次性返回，用户等待体验差，且不符合真实 AI 产品的交互模式
**升级目标**: 逐字流式输出，前端边接收边渲染

**涉及改动**:
- `ChatController` 增加 SSE 端点（`SseEmitter`，SpringBoot 原生支持）
- LangChain4j 的 `StreamingChatModel` 替代 `ChatModel`（DashScope 兼容 OpenAI SSE 协议）
- 前端用 `EventSource` 接收流式数据（如果做前端的话）
- `ChatResponse` 结构需要适配流式场景（答案逐步拼接，引用来源在流结束时返回）

**竞争力提升**: 能讲"我实现了 LLM 流式传输 + 渐进渲染"，体现生产级思维
**学习增量**: SpringBoot `SseEmitter` + LangChain4j StreamingChatModel，约 1 小时上手
**预计工作量**: 3-4 小时

---

### 13. 多模型网关（智能路由 + 成本管控）
**优先级**: ⭐⭐
**现状**: 只调用一个 LLM（qwen3.6-flash），没有成本和性能的策略分层
**升级目标**: 简单问题走轻量模型，复杂问题走强模型，加上调用统计和限流

**涉及改动**:
- 新建 `LLMGatewayService`，根据问题复杂度（长度、关键词匹配、是否需要多步推理）选择模型
- `application.yaml` 配置多组模型参数（flash / plus / max 等）
- Redis 记录每个模型的调用次数和 token 消耗，用于成本统计
- 限流策略扩展：按用户、按模型双维度限流

**竞争力提升**: 场景从"校园问答"变成"企业级 AI 调用管理"，技术叙事更硬核
**学习增量**: 仅是业务逻辑层面的路由设计，无新技术栈
**预计工作量**: 4-5 小时

---

## 📊 剩余功能工时总览

| 功能 | 预估耗时 | 难度 | 状态 |
|------|---------|------|------|
| deleteDocument 关联清理 | 1h | 低 | ⏳ |
| Redis 热点缓存 | 1-2h | 低 | ⏳ |
| 多轮对话/会话历史 | 2-3h | 中 | ⏳ |
| ES 混合检索 + RRF | 3-4h | 中 | ⏳ |
| RAG Agent 多步推理 | 4-6h | 中 | ⏳ |
| 流式响应 SSE | 3-4h | 中 | ⏳ |
| 多模型网关 | 4-5h | 中 | ⏳ |
| 限流与监控 | 2h | 低 | ⏳ |
| JWT 用户权限 | 3-4h | 中 | ⏳ |
| 增量更新/爬虫 | 3-4h | 中 | ⏳ |
| Docker 部署 | 2-3h | 低 | ⏳ |
| 测试 + 评估指标 | 3-4h | 中 | ⏳ |
| 面试演示准备 | 2h | 低 | ⏳ |

**建议实施优先顺序**: 关联清理 → Redis 缓存 → 流式响应 → 多轮对话 → RAG Agent → ES 混合检索 → 多模型网关

---

## 📝 更新日志

- **2026-05-13**: 初始创建，记录10项待改进内容
- **2026-05-14**： 了解下pgSQL管理向量数据库的方法，以及双表结构到底是怎么运行的
- **2026-05-14**： 新增竞争力升级方向和剩余功能工时估算