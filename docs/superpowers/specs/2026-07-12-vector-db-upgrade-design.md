# 智能客服多Agent系统 — 向量数据库升级设计文档

> 基于 Milvus 的 RAG 知识库 + Advisor 溯源 + 文件上传
> 升级日期：2026-07-12

---

## 一、升级目标

在现有智能客服多Agent系统基础上，引入向量数据库，实现三大能力：

| 能力 | 说明 | 优先级 |
|------|------|--------|
| **① Milvus 基础接入** | 向量数据库连接、Collections 初始化、Embedding 服务 | **P0 — 基础** |
| **② RAG 知识库** | 替换 Mock 数据，从向量库检索商品/政策/FAQ 知识 | P1 |
| **③ Advisor 溯源** | 每个回复绑定引用来源，支持前端展示 | P1 |
| **④ Metadata 管理** | 按分类/标签/时间过滤知识检索 | P1 |
| **⑤ 文件上传** | 上传 PDF/TXT/MD，自动切片向量化 | P2 |
| **⑥ 长期记忆 + 语义缓存** | 跨会话记忆 + 相似问题缓存 | P2 |

**本次实现范围：①~⑤**，⑥视情况后续迭代。

---

## 二、总体架构

```
                    ┌────────────────────────────────────────┐
                    │              Milvus (Standalone)         │
                    │                                         │
                    │  集合: cs_knowledge  (知识库)             │
                    │  集合: cs_session_log (溯源日志)          │
                    │  集合: cs_memory     (长期记忆)           │
                    └──────────────┬─────────────────────────┘
                                   │
                    ┌──────────────▼─────────────────────────┐
                    │         Vector Service Layer             │
                    │                                         │
                    │  EmbeddingService (向量化)               │
                    │  MilvusClientWrapper (CRUD 封装)         │
                    │  KnowledgeBaseStore (知识检索)            │
                    │  KnowledgeAdvisor (检索+溯源)             │
                    │  FileUploadService (上传处理)             │
                    └──────────────┬─────────────────────────┘
                                   │
        ┌──────────────────────────┼─────────────────────────┐
        │     Supervisor           │    Workers                │
        │                          │  (通过 Advisor 检索知识)  │
        └──────────────────────────┴─────────────────────────┘
```

---

## 三、实现阶段拆分

### Phase 1: 基础设施（Milvus + Embedding）

**核心文件：**
- `config/MilvusConfig.java` — Milvus 连接 + 集合初始化
- `vector/EmbeddingService.java` — DeepSeek Embedding API 封装
- `vector/MilvusClientWrapper.java` — 统一 CRUD 封装

**Milvus Docker Compose：**
```yaml
services:
  etcd:
    image: quay.io/coreos/etcd:v3.5.16
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
    volumes:
      - etcd_data:/etcd

  minio:
    image: minio/minio:RELEASE.2023-12-30T07-34-18Z
    volumes:
      - minio_data:/minio_data

  milvus:
    image: milvusdb/milvus:v2.4.x
    depends_on: [etcd, minio]
    ports:
      - "19530:19530"
```

**集合 Schema 定义：**
```java
// cs_knowledge — 知识库主集合
collection: "cs_knowledge"
fields:
  - id              (Int64, PK, auto_id=true)
  - chunk_id        (VarChar, 文档块唯一ID)
  - content         (VarChar, 文本内容，max 8192)
  - embedding       (FloatVector, dim=1536)
  - metadata        (JSON)
index: IVFFlat (nlist=128), COSINE 距离

// metadata 结构示例
{
  "sourceFile": "退货政策.pdf",
  "fileName": "退货政策.pdf",
  "category": "policy",
  "tags": "退货,退款,电子产品",
  "chunkIndex": 3,
  "totalChunks": 12,
  "uploadedAt": "2026-07-12T10:00:00",
  "pageNumber": 5  // PDF 特有
}

// cs_session_log — 溯源日志
collection: "cs_session_log"
fields:
  - id              (Int64, PK)
  - reply_id        (VarChar)
  - session_id      (VarChar)
  - worker_name     (VarChar)
  - cited_docs      (JSON)  // 引用的文档列表
  - reply_content   (VarChar)
  - created_at      (VarChar)

// cs_memory — 长期记忆（Phase 2）
collection: "cs_memory"
fields:
  - id              (Int64, PK)
  - session_id      (VarChar)
  - memory_text     (VarChar)
  - embedding       (FloatVector, dim=1536)
  - metadata        (JSON)
```

### Phase 2: RAG 知识库 + Advisor

**核心文件：**
- `vector/advisor/KnowledgeAdvisor.java` — 检索+溯源
- `vector/advisor/RetrievalSpec.java` — 检索规格
- `vector/advisor/CitedReply.java` + `Citation.java`
- `vector/advisor/metadata/` — 元数据管理层
- `vector/store/KnowledgeBaseStore.java` — 知识库向量存储
- `tool/ProductTools.java` 等 — 注入 Advisor 替换 Mock

**Worker 使用 KnowledgeAdvisor 流程：**
```
Worker.apply()
  → advisor.retrieve(query, spec)    // 检索知识库
  → LLM.generate(prompt + context)   // 生成回复
  → advisor.cite(reply, sources)     // 绑定引用
  → advisor.trackConsumption(...)    // 记录溯源
  → return finalReply + citations
```

### Phase 3: 文件上传

**核心文件：**
- `controller/KnowledgeController.java` — 文件上传 API
- `vector/file/FileUploadService.java` — 上传处理总入口
- `vector/file/parser/PdfParser.java` — PDF 解析
- `vector/file/parser/TextParser.java` — TXT 解析
- `vector/file/parser/MarkdownParser.java` — MD 解析
- `vector/file/chunker/DocumentChunker.java` — 文本切片

**支持文件类型：**
| 类型 | 限制 | 解析方案 |
|------|------|----------|
| PDF | ≤10MB, ≤100页 | Apache PDFBox 2.0 |
| TXT | ≤5MB, UTF-8 | 原生读取 |
| MD  | ≤5MB, UTF-8 | 正则提取标题层级 |

**切片策略：**
```
chunk_size = 500 tokens (约 375 中文字)
chunk_overlap = 50 tokens
分割优先级: 标题 > 段落 > 句子边界
```

---

## 四、API 设计

### 知识库管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/upload` | 上传文件到知识库 |
| GET | `/api/knowledge/documents` | 知识库文档列表 |
| GET | `/api/knowledge/documents/{id}` | 文档详情 + chunk 列表 |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档及其所有 chunk |
| GET | `/api/knowledge/search` | 关键词搜索知识库 |
| GET | `/api/knowledge/collections` | 集合统计信息 |

### 上传请求/响应

```json
POST /api/knowledge/upload
Content-Type: multipart/form-data
参数:
  file: (binary)
  category: "product"|"policy"|"faq"|"other"
  tags: "可选,逗号分隔"

响应 200:
{
  "docId": "kb_xxxx",
  "fileName": "退货政策.pdf",
  "chunks": 12,
  "status": "indexed",
  "collection": "cs_knowledge",
  "uploadedAt": "2026-07-12T10:00:00"
}
```

### SSE 新增事件 — 溯源推送

现有 SSE 流式回复中新增 `citations` 事件：

```
event: citations
data: {
  "replyId": "r_xxxx",
  "citations": [
    {
      "docId": "chunk_003",
      "fileName": "退货政策.pdf",
      "snippet": "电子产品享受7天无理由退货...",
      "score": 0.92,
      "metadata": {"category": "policy", "tags": "退货"}
    }
  ]
}

event: message
data: 您购买的漫步者G2蓝牙耳机[1]享受7天无理由退货[2]，可以退。
```

---

## 五、配置

```yaml
# application.yml 新增
milvus:
  host: localhost
  port: 19530
  database: default
  collections:
    knowledge: cs_knowledge
    knowledge-dim: 1536
    session-log: cs_session_log
    memory: cs_memory
    memory-dim: 1536

ai:
  embedding:
    model: text-embedding-ada-002   # DeepSeek 兼容 OpenAI Embedding
    base-url: https://api.deepseek.com
    dim: 1536
    batch-size: 16

knowledge:
  upload:
    max-file-size: 10MB
    allowed-types: pdf,txt,md
    storage-path: ./knowledge/files
  chunk:
    size: 500
    overlap: 50
```

---

## 六、依赖新增

```xml
<!-- pom.xml 新增 -->
<!-- Milvus SDK -->
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>2.4.1</version>
</dependency>

<!-- PDF 解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>

<!-- HTTP 客户端 (Embedding API) -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

---

## 七、种子数据

启动时自动写入 `cs_knowledge`，保持与现有 Mock 数据兼容：

```java
// 商品数据 → cs_knowledge (category=product)
"漫步者 G2 无线蓝牙耳机 299元 防汗佩戴稳固 适合运动跑步"
"小米 Buds 5 主动降噪 499元 音质出色"
"华为 FreeBuds 6i 入耳式 359元"
"iPhone 16 Pro Max 9999元 A18芯片钛金属"
"华为 Mate 70 Pro 7999元 鸿蒙系统"

// 政策数据 → cs_knowledge (category=policy)
"七天无理由退货：商品签收后7天内可申请退货"
"退款规则：退款原路返回，1-3个工作日到账"
"退换货条件：商品完好不影响二次销售"
"物流政策：满99元包邮，不满99元运费8元"

// FAQ 数据 → cs_knowledge (category=faq)
"如何查询订单：提供订单号可查询订单状态和物流信息"
"如何申请退款：联系客服提供订单号和退款原因"
```

---

## 八、实现顺序

```
Phase 1 ─── Milvus 基础设施
  1.1  docker-compose.yml (Milvus + Etcd + MinIO)
  1.2  pom.xml 新增依赖
  1.3  application.yml 新增配置
  1.4  config/MilvusConfig.java
  1.5  vector/EmbeddingService.java
  1.6  vector/MilvusClientWrapper.java
  1.7  种子数据初始化 + 首次启动自动写入
  ✅ 验证: 能连接 Milvus，写入/查询向量

Phase 2 ─── RAG 知识检索
  2.1  vector/store/KnowledgeBaseStore.java
  2.2  vector/advisor/ 包（检索+溯源模型）
  2.3  vector/advisor/metadata/ 包
  2.4  Worker 注入 Advisor，替换 Mock 数据
  ✅ 验证: 发一条消息，回复基于知识库

Phase 3 ─── 文件上传
  3.1  vector/file/parser/ 包
  3.2  vector/file/chunker/DocumentChunker.java
  3.3  vector/file/FileUploadService.java
  3.4  controller/KnowledgeController.java
  3.5  前端上传页面
  ✅ 验证: 上传PDF，Agent回复用到新知识

Phase 4 ─── 前端溯源展示
  4.1  新增 SSE citations 事件处理
  4.2  消息气泡下方引用标签组件
  ✅ 验证: 回复带引用标记，点击可查看来源
```
