# RAG 项目开发经验记录 (Part 5)

## 问题 1：文件上传的 Content-Type 与 boundary 分隔符
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
前端 FormData 上传文件，手动设置了 `Content-Type: multipart/form-data`，后端始终无法解析，为什么？

### 解释
`multipart/form-data` 格式需要在 Content-Type 头中附带一个随机 `boundary` 分隔符，用于在请求体中标记各个字段的边界。

#### 正确 vs 错误的请求头

```
❌ 手动指定（丢失 boundary）:
Content-Type: multipart/form-data

✅ Axios 自动生成（带 boundary）:
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW
```

#### 原理
```
请求体由 boundary 分隔：
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="file"; filename="test.pdf"

(文件二进制数据)
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

后端（Tomcat/Servlet）通过 boundary 字符串来切分各个 form 字段。如果没有 boundary，后端不知道数据从哪里开始、从哪里结束，解析必然失败。

#### 正确做法
```typescript
// ✅ Axios 检测到 FormData 对象时自动设置正确的 Content-Type
const formData = new FormData()
formData.append('file', file)
axios.post('/upload', formData)  // 不要手动加 headers
```

---

## 问题 2：Element Plus 的 auto-upload 机制
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
`el-upload` 设置了 `:auto-upload="false"`，选文件后什么也没发生，为什么？

### 解释
`auto-upload` 控制的是**文件选择后是否立即触发上传**。

| 配置 | 行为 |
|------|------|
| `auto-upload="true"` | 选择文件 → 立即调用 `http-request` / 发起 HTTP 上传 |
| `auto-upload="false"` | 选择文件 → 文件暂存在组件内部的 fileList → **等待手动触发**（如调用 `submit()`） |

当 `auto-upload="false"` 且 `show-file-list="false"`（隐藏文件列表）时，用户选了文件后看不到任何反馈，也没有提交按钮，上传永远不会触发。

**结论**：如果想让用户选择文件就立刻上传，必须用 `auto-upload="true"`。

---

## 问题 3：Vite 代理的 ECONNREFUSED 和 502 错误
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
前端控制台报 `ECONNREFUSED` 和 `502 Bad Gateway`，是什么意思？

### 解释

| 错误 | 含义 | 原因 |
|------|------|------|
| `ECONNREFUSED` (AggregateError) | Vite 代理无法连接到后端端口 | 后端服务没启动，8080 端口无人监听 |
| `502 Bad Gateway` | Vite 代理收到了后端的无效响应 | 同上，或者后端启动后又挂了 |

#### Vite 代理工作流程
```
浏览器 → Vite Dev Server (:5173)
           ↓ 匹配 proxy 规则 '/api'
           ↓ HTTP 请求转发到 target
         Spring Boot (:8080)
           ↓
         返回响应 → Vite 转发回浏览器
```

当 `:8080` 没有服务在监听时，Vite 的 `http-proxy` 中间件连接失败，向前端返回 502。刷新浏览器不能解决问题，需要启动后端。

---

## 问题 4：Spring Boot Multipart 文件大小限制的拦截链路
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
`application.yaml` 里配置了 `max-file-size: 50MB`，它是怎么拦截大文件的？代码在哪里？

### 解释
整个过程分三层，全部是框架内置行为，无需开发者写代码。

#### 三层链路

**第一层：Spring Boot 读取配置 → 创建限制对象**
```java
// MultipartAutoConfiguration（Spring Boot 内置自动配置类）
@Bean
public MultipartConfigElement multipartConfigElement() {
    // multipartProperties 自动从 yaml 读取 max-file-size/max-request-size
    return this.multipartProperties.createMultipartConfig();
}
// 内部把 "50MB" 转为 52428800 字节
```

**第二层：Tomcat 在读取网络流时执行拦截**
```java
// org.apache.catalina.connector.Request
private void parseParts(boolean explicit) {
    MultipartConfigElement mce = getWrapper().getMultipartConfigElement();
    long maxFileSize = mce.getMaxFileSize();   // 52428800 字节
    
    // 逐段读取 multipart 请求体
    // 如果某个 part 的尺寸 > maxFileSize：
    //   抛出 IOException → 外层转为 IllegalStateException
}
```

**第三层：Spring MVC 捕获异常 → 返回 500**
```java
// StandardMultipartHttpServletRequest
// 调用 request.getParts()，捕获 Tomcat 抛出的异常
// Spring 包装为 MultipartException → 返回 HTTP 500
```

#### 关键结论
拦截发生在 **Tomcat 读取网络字节流阶段**，比 Controller 代码执行早得多。Controller 的 `uploadDocument()` 方法根本不会被调用——请求在到达 Controller 之前就已经 500 了。所以业务层再加文件大小校验是多余的。

---

## 问题 5：文件类型校验——白名单 vs 黑名单
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
上传文件时应该用白名单（只放行特定格式）还是黑名单（只拦截危险格式）？

### 解释

| 策略 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| 白名单 | 只允许 PDF、Word、Markdown 等几种 | 安全性高 | 把 Tika 能解析的其他 90+ 种格式拒之门外 |
| 黑名单 | 只拦截 .exe、.sh、.jar 等可执行文件 | 灵活，Tika 能解析什么就能上传什么 | 可能漏掉未知危险类型 |
| 黑名单 + Tika 兜底 | 黑名单拦截 + 解析后检查文本是否为空 | 兼顾安全与灵活 | — |

#### 项目当前方案（黑名单 + 兜底）

```java
private static final Set<String> BLOCKED_MIME_PREFIXES = Set.of(
    "application/x-msdownload",     // .exe
    "application/x-msdos-program",  // .com
    "application/x-dosexec",        // PE 可执行文件
    "application/x-msi",            // .msi 安装包
    "application/x-sh",             // shell 脚本
    "application/x-bat",            // batch 脚本
    "application/x-powershell",     // PowerShell 脚本
    "application/x-perl",           // Perl 脚本
    "application/x-python",         // Python 脚本
    "application/java-archive"      // .jar
);

// 1. MIME 类型在黑名单中 → 拒绝
// 2. Tika 解析后 content.isBlank() → 拒绝（图片、扫描件、损坏文件）
// 3. 其他一切格式 → 放行
```

---

## 问题 6：Embedding 向量缓存的作用
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
向量缓存是什么意思？是为文件的向量信息做备份方便误删恢复吗？

### 解释
**不是备份，是省钱。** 核心场景：

```
第一次上传《学生手册.pdf》→ 500 个 chunk → 调 500 次 Embedding API → 付费
删除该文档
重新上传《学生手册.pdf》（内容完全一样）
  → 500 个 chunk 的文本没变
  → 没有缓存：再调 500 次 API → 再付一次费
  → 有缓存：chunk 文本 MD5 → Redis 查到旧向量 → 0 次 API 调用
```

#### 存储结构
```
Key:   "embed:cache:" + MD5(chunk_text)
Value: float[] 向量数据（如 1024 个 float，约 4KB）
TTL:   7 天
```

#### 本质
用 Redis 存储空间（便宜）换 Embedding API 调用次数（花钱）。文档量大、重复上传频繁时收益明显。

---

## 问题 7：热点问答缓存——MD5 精确匹配的局限性
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
将问题的回答存在 Redis 中，怎么确保类似问题也能命中缓存？用 MD5 的话，"食堂几点开门"和"食堂什么时候开门"会是完全不同的 key。

### 解释

#### MD5 精确匹配（简单版）
```java
String cacheKey = "rag:cache:" + DigestUtil.md5Hex(question);
String cached = redisTemplate.opsForValue().get(cacheKey);
```
- "食堂几点开门" → MD5: a1b2c3... ✅ 命中
- "食堂什么时候开门" → MD5: d4e5f6... ❌ 未命中（但语义完全一样）

**适用场景**：校园场景中大量学生会用**完全相同**的措辞提问（"校历在哪看""寒假什么时候开始"），精确匹配能覆盖一部分高频问题。

#### 语义缓存（进阶版）
```
用户提问 → 生成问题 Embedding 向量
  → 与 Redis 中所有已缓存问题的向量做余弦相似度
  → 相似度 > 0.92 → 命中语义相同的缓存
```

**实现成本对比**：
| 方案 | 改动量 | 命中率 | 复杂度 |
|------|--------|--------|--------|
| MD5 精确匹配 | 5 行代码 | 低（只有字面相同命中） | 极低 |
| 语义缓存 | 新增一个 Service | 高 | 中 |

**建议**：先上 MD5 精确匹配快速见效，后续升级语义缓存。

---

## 问题 8：滑动窗口限流 vs 固定窗口限流
**提出时间：** 2026-05-15
**提出次数：** 1

### 问题
什么叫滑动窗口限流？和固定窗口有什么区别？

### 解释

#### 固定窗口（存在边界漏洞）
```
窗口: 按墙钟分钟划分 [12:00, 12:01)
限制: 10 次/分钟

12:00:58  发 10 次 ✅  ← 属于 [12:00, 12:01) 窗口，合法
12:01:01  发 10 次 ✅  ← 属于 [12:01, 12:02) 窗口，合法
```
**实际 3 秒内发了 20 次**，但因为跨了两个窗口，全部合法。高并发场景下这就是漏洞。

#### 滑动窗口（堵住漏洞）
```
不看墙钟分钟，看"最近 60 秒内有几次请求"

12:00:58  发 10 次 → 最近 60s: 10 次 ✅
12:01:01  发第 11 次 → 最近 60s: 11 次 > 10 → 拒绝 ❌
```

#### Redis 实现（ZSET）
```java
String key = "rate:" + userId;
long now = System.currentTimeMillis();
long windowStart = now - 60_000;  // 60 秒前

// 1. 删除窗口外的旧记录
redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

// 2. 计数窗口内剩余请求
Long count = redisTemplate.opsForZSet().zCard(key);

if (count != null && count >= limit) {
    throw new RuntimeException("请求过于频繁");
}

// 3. 记录本次请求
redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
redisTemplate.expire(key, Duration.ofSeconds(60));
```

#### 对比
| 特性 | 固定窗口 | 滑动窗口 |
|------|---------|---------|
| 实现 | 简单（INCR + TTL） | 中等（ZSET + 清理） |
| 边界漏洞 | 有（2 秒可发 20 次） | 无 |
| 内存占用 | 低（1 个 key） | 中（1 个 ZSET，N 条记录） |
| 适用场景 | 低精度 | 生产环境 API 限流 |

---

**文档说明：**
- 本文档为 RAG 项目开发经验记录 Part 5
- 记录了 2026-05-15 的技术疑问与解答
- 涵盖前端上传组件、Vite 代理、Spring Multipart 拦截链路、安全校验策略、Redis 缓存设计、限流算法等
- 持续更新中...
