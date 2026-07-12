# 向量数据库升级实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为智能客服多Agent系统接入 Milvus 向量数据库，实现 RAG 知识检索 + Advisor 溯源 + 文件上传

**Architecture:** 在现有 LangGraph4j 编排的 Supervisor-Worker 架构上，新增向量服务层。Worker 节点不再使用硬编码 Mock 数据，改为通过 KnowledgeAdvisor 从 Milvus 检索知识。新增文件上传和解析链路。前端增加溯源引用展示。

**Tech Stack:** Milvus 2.4.x, milvus-sdk-java 2.4.1, Apache PDFBox 3.0.3, OkHttp 4.12.0, DeepSeek Embedding API

## 全局约束

- Java 21, Spring Boot 3.3.4
- Maven Central 仓库，不使用阿里云镜像（已有绕过配置）
- 使用 SLF4J 手动日志（项目中未使用 Lombok @Slf4j）
- 构造函数注入风格（项目中已有构造器注入）
- DeepSeek API Key 复用已有的 `sk-f29c7bc020e34393ad0ff8da7cd99c51`
- Milvus 默认端口 19530

---

## 文件结构

### 新增文件
```
src/main/java/com/cs/agent/
├── config/
│   └── VectorConfig.java                    # Milvus 连接 + 集合初始化
├── vector/
│   ├── EmbeddingService.java                # DeepSeek Embedding API 封装
│   ├── MilvusClientWrapper.java             # Milvus CRUD 统一封装
│   ├── advisor/
│   │   ├── KnowledgeAdvisor.java            # 检索+溯源核心
│   │   ├── AdvisedContext.java              # 检索上下文
│   │   ├── CitedReply.java                  # 溯源回复
│   │   ├── Citation.java                    # 单个引用
│   │   ├── RetrievalSpec.java               # 检索规格
│   │   └── metadata/
│   │       ├── DocMetadata.java             # 元数据接口
│   │       ├── CollectionSchema.java        # 集合 Schema 枚举
│   │       ├── ProductMeta.java
│   │       ├── PolicyMeta.java
│   │       ├── FaqMeta.java
│   │       └── MetadataFilter.java          # 过滤构建器
│   ├── store/
│   │   ├── KnowledgeBaseStore.java          # 知识库存储
│   │   └── SessionLogStore.java            # 溯源日志存储
│   └── file/
│       ├── FileUploadService.java           # 上传处理总入口
│       ├── parser/
│       │   ├── DocumentParser.java          # 解析器接口
│       │   ├── PdfParser.java               # PDF 解析
│       │   ├── TextParser.java              # TXT 解析
│       │   └── MarkdownParser.java          # MD 解析
│       ├── chunker/
│       │   └── DocumentChunker.java         # 文本切片
│       └── dto/
│           ├── UploadResult.java
│           ├── KnowledgeDoc.java
│           └── DocumentChunk.java
├── controller/
│   └── KnowledgeController.java             # 知识库管理 API
└── service/
    └── KnowledgeInitService.java            # 启动时种子数据写入

docker-compose.yml                           # Milvus 容器编排
```

### 修改文件
```
pom.xml                                      # + milvus-sdk, pdfbox, okhttp
src/main/resources/application.yml           # + milvus/embedding/knowledge 配置
src/main/java/com/cs/agent/
├── node/agent/OrderAgentNode.java           # +注入 Advisor
├── node/agent/ProductAgentNode.java         # +注入 Advisor
├── node/agent/ReturnAgentNode.java          # +注入 Advisor
├── node/agent/ComplaintAgentNode.java       # +注入 Advisor
├── orchestrator/ChatOrchestrator.java       # +注入 Advisor + 溯源处理
├── controller/ChatController.java           # + SSE citations 事件
└── state/CsAgentState.java                  # + citations 字段
web/src/store/chat.js                        # + SSE citations 事件处理
web/src/views/chat/index.vue                  # +引用标签展示
```

---

## Phase 1: Milvus 基础设施

### Task 1.1: Docker Compose + Maven 依赖

**Files:**
- Create: `C:\multi-agent-cs\docker-compose.yml`
- Modify: `C:\multi-agent-cs\pom.xml`

**Interfaces:**
- Consumes: 无
- Produces: Milvus 运行环境，可用 Maven 编译

- [ ] **Step 1: 创建 docker-compose.yml**

```yaml
version: '3.5'
name: multi-agent-cs

services:
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.16
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - etcd_data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    networks:
      - milvus-net

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2023-12-30T07-34-18Z
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio_data:/minio_data
    command: minio server /minio_data --console-address :9001
    ports:
      - "9000:9000"
      - "9001:9001"
    networks:
      - milvus-net

  milvus:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.4.17
    command: milvus run standalone
    depends_on:
      - etcd
      - minio
    ports:
      - "19530:19530"
      - "9091:9091"
    volumes:
      - milvus_data:/var/lib/milvus
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    networks:
      - milvus-net

volumes:
  etcd_data:
  minio_data:
  milvus_data:

networks:
  milvus-net:
    driver: bridge
```

- [ ] **Step 2: 添加 Milvus、PDFBox、OkHttp 依赖到 pom.xml**

在 `<properties>` 中添加版本号：
```xml
<milvus-sdk.version>2.4.1</milvus-sdk.version>
<pdfbox.version>3.0.3</pdfbox.version>
<okhttp.version>4.12.0</okhttp.version>
```

在 `<dependencies>` 末尾添加：
```xml
<!-- Milvus 向量数据库 SDK -->
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>${milvus-sdk.version}</version>
</dependency>

<!-- PDF 解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>${pdfbox.version}</version>
</dependency>

<!-- HTTP 客户端（Embedding API 调用） -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>${okhttp.version}</version>
</dependency>

<!-- JSON 处理（Embedding 响应解析） -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.11.0</version>
</dependency>
```

- [ ] **Step 3: 启动 Milvus 并验证可用**

```bash
cd /c/multi-agent-cs
docker compose up -d
sleep 15
docker logs milvus-standalone 2>&1 | tail -5
```
Expected: "milvus standalone started" 或类似就绪信息

- [ ] **Step 4: 编译验证**

```bash
cd /c/multi-agent-cs
mvn compile -q
```
Expected: BUILD SUCCESS

---

### Task 1.2: 配置文件

**Files:**
- Modify: `C:\multi-agent-cs\src\main\resources\application.yml`

**Interfaces:**
- Consumes: 无
- Produces: VectorConfig 使用的新配置项

- [ ] **Step 1: 在 application.yml 末尾添加 Milvus + Embedding 配置**

```yaml
# ==================== 向量数据库 ====================
milvus:
  host: localhost
  port: 19530
  database: default
  collections:
    knowledge: cs_knowledge
    knowledge-dim: 1536
    session-log: cs_session_log

# ==================== Embedding ====================
ai:
  embedding:
    model: text-embedding-v2
    dim: 1536
    batch-size: 16
  # 复用已有的 api-key 和 base-url

# ==================== 知识库 ====================
knowledge:
  upload:
    max-file-size: 10485760  # 10MB
    allowed-types: pdf,txt,md
    storage-path: ./knowledge/files
  chunk:
    size: 500
    overlap: 50
```

---

### Task 1.3: VectorConfig — Milvus 连接 + 集合初始化

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\config\VectorConfig.java`

**Interfaces:**
- Consumes: `application.yml` 中的 `milvus.*` 配置
- Produces: `@Bean MilvusClientWrapper` (其他组件的 Milvus 入口)

- [ ] **Step 1: 创建 VectorConfig.java**

```java
package com.cs.agent.config;

import com.cs.agent.vector.MilvusClientWrapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量数据库连接配置
 *
 * 初始化 Milvus 连接并创建需要的集合。
 */
@Configuration
public class VectorConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VectorConfig.class);

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.database:default}")
    private String database;

    @Value("${milvus.collections.knowledge:cs_knowledge}")
    private String knowledgeCollection;

    @Value("${milvus.collections.knowledge-dim:1536}")
    private int knowledgeDim;

    @Value("${milvus.collections.session-log:cs_session_log}")
    private String sessionLogCollection;

    @Bean
    public MilvusClientV2 milvusClient() {
        ConnectConfig config = ConnectConfig.builder()
                .host(host)
                .port(port)
                .databaseName(database)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        log.info("✅ Milvus 连接成功: {}:{}", host, port);
        return client;
    }

    @Bean
    public MilvusClientWrapper milvusClientWrapper(MilvusClientV2 milvusClient) {
        initCollections(milvusClient);
        return new MilvusClientWrapper(milvusClient, knowledgeCollection, knowledgeDim);
    }

    private void initCollections(MilvusClientV2 client) {
        initKnowledgeCollection(client);
        initSessionLogCollection(client);
        log.info("✅ Milvus 集合初始化完成");
    }

    private void initKnowledgeCollection(MilvusClientV2 client) {
        boolean exists = client.hasCollection(HasCollectionReq.builder()
                .collectionName(knowledgeCollection).build());
        if (exists) {
            log.info("  集合 [{}] 已存在，跳过创建", knowledgeCollection);
            return;
        }

        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("id").dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("chunk_id").dataType(DataType.VarChar).maxLength(256).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("content").dataType(DataType.VarChar).maxLength(8192).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("embedding").dataType(DataType.FloatVector).dimension(knowledgeDim).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("metadata").dataType(DataType.JSON).build());

        CreateCollectionReq req = CreateCollectionReq.builder()
                .collectionName(knowledgeCollection)
                .fieldSchemas(fields)
                .numShards(1)
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build();
        client.createCollection(req);
        log.info("  ✅ 创建集合 [{}] (dim={})", knowledgeCollection, knowledgeDim);
    }

    private void initSessionLogCollection(MilvusClientV2 client) {
        boolean exists = client.hasCollection(HasCollectionReq.builder()
                .collectionName(sessionLogCollection).build());
        if (exists) return;

        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("id").dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("reply_id").dataType(DataType.VarChar).maxLength(128).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("session_id").dataType(DataType.VarChar).maxLength(128).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("worker_name").dataType(DataType.VarChar).maxLength(64).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("cited_docs").dataType(DataType.JSON).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("reply_content").dataType(DataType.VarChar).maxLength(8192).build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("created_at").dataType(DataType.VarChar).maxLength(32).build());

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(sessionLogCollection)
                .fieldSchemas(fields)
                .build());
        log.info("  ✅ 创建集合 [{}]", sessionLogCollection);
    }
}
```

---

### Task 1.4: EmbeddingService — DeepSeek Embedding API

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\vector\EmbeddingService.java`

**Interfaces:**
- Consumes: `ai.api-key`, `ai.embedding.*` 配置
- Produces: `List<Float> embed(String text)`, `List<List<Float>> embedBatch(List<String>)`

- [ ] **Step 1: 创建 EmbeddingService**

```java
package com.cs.agent.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 向量化服务 —— 调用 DeepSeek Embedding API
 *
 * 兼容 OpenAI Embedding API 格式。
 */
@Service
public class EmbeddingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmbeddingService.class);

    private final OkHttpClient client;

    @Value("${ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.embedding.model:text-embedding-v2}")
    private String model;

    @Value("${ai.embedding.batch-size:16}")
    private int batchSize;

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    public EmbeddingService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("🧠 EmbeddingService 初始化，model={}, baseUrl={}", model, baseUrl);
    }

    /**
     * 将单条文本转为向量
     */
    public List<Float> embed(String text) {
        List<List<Float>> results = embedBatch(List.of(text));
        return results.isEmpty() ? emptyVector() : results.get(0);
    }

    /**
     * 批量向量化
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> allResults = new ArrayList<>();

        // 分批处理
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            allResults.addAll(callEmbeddingAPI(batch));
        }

        return allResults;
    }

    private List<List<Float>> callEmbeddingAPI(List<String> texts) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);

            JsonArray input = new JsonArray();
            for (String t : texts) {
                input.add(t);
            }
            payload.add("input", input);

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Embedding API 错误: {} {}", response.code(), response.body() != null ? response.body().string() : "");
                    return fallbackEmbeddings(texts.size());
                }

                String respBody = response.body() != null ? response.body().string() : "{}";
                return parseEmbeddingResponse(respBody);
            }
        } catch (IOException e) {
            log.error("Embedding API 调用失败", e);
            return fallbackEmbeddings(texts.size());
        }
    }

    /**
     * 解析 OpenAI 兼容的 Embedding 响应
     */
    private List<List<Float>> parseEmbeddingResponse(String json) {
        List<List<Float>> results = new ArrayList<>();
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");
            if (data == null) return fallbackEmbeddings(1);

            for (int i = 0; i < data.size(); i++) {
                JsonArray embedding = data.get(i).getAsJsonObject().getAsJsonArray("embedding");
                List<Float> vector = new ArrayList<>();
                for (int j = 0; j < embedding.size(); j++) {
                    vector.add(embedding.get(j).getAsFloat());
                }
                results.add(vector);
            }
        } catch (Exception e) {
            log.error("解析 Embedding 响应失败", e);
            return fallbackEmbeddings(results.isEmpty() ? 1 : results.size());
        }
        return results;
    }

    /**
     * API 调用失败时的 fallback：用零向量代替
     */
    private List<List<Float>> fallbackEmbeddings(int count) {
        log.warn("⚠️ 使用零向量 fallback (count={})", count);
        List<List<Float>> fallback = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fallback.add(emptyVector());
        }
        return fallback;
    }

    private List<Float> emptyVector() {
        List<Float> vec = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) vec.add(0.0f);
        return vec;
    }
}
```

---

### Task 1.5: MilvusClientWrapper — CRUD 统一封装

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\vector\MilvusClientWrapper.java`

**Interfaces:**
- Consumes: `MilvusClientV2`, collection name, dimension
- Produces: `insertChunks(...)`, `searchSimilar(...)`, `deleteDocument(...)`, `listDocuments(...)`, `getCollectionStats(...)`, `getIdAndScore()` 内部记录

- [ ] **Step 1: 创建 MilvusClientWrapper**

```java
package com.cs.agent.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.*;

/**
 * Milvus 统一封装
 *
 * 封装 Milvus 的增删查操作，上层代码不直接调 SDK。
 */
public class MilvusClientWrapper {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MilvusClientWrapper.class);

    private final MilvusClientV2 client;
    private final String collectionName;
    private final int dimension;
    private final Gson gson = new Gson();

    public MilvusClientWrapper(MilvusClientV2 client, String collectionName, int dimension) {
        this.client = client;
        this.collectionName = collectionName;
        this.dimension = dimension;
        createIndex();
        client.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());
    }

    private void createIndex() {
        try {
            IndexParam indexParam = IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.IVF_FLAT)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("nlist", 128))
                    .build();
            client.createIndex(CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(List.of(indexParam))
                    .build());
            log.info("  ✅ 创建索引 [{}].embedding (IVF_FLAT, COSINE)", collectionName);
        } catch (Exception e) {
            log.warn("  索引可能已存在: {}", e.getMessage());
        }
    }

    /**
     * 插入文档切片
     */
    public void insertChunks(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) return;

        List<String> chunkIds = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();
        List<String> metadataList = new ArrayList<>();

        for (KnowledgeChunk c : chunks) {
            chunkIds.add(c.chunkId);
            contents.add(c.content);
            embeddings.add(c.embedding);
            metadataList.add(c.metadataJson != null ? c.metadataJson : "{}");
        }

        InsertReq req = InsertReq.builder()
                .collectionName(collectionName)
                .data(List.of(
                        Map.of("chunk_id", chunkIds,
                                "content", contents,
                                "embedding", embeddings,
                                "metadata", metadataList)
                ))
                .build();
        client.insert(req);
        log.info("📥 插入 {} 条文档切片到 [{}]", chunks.size(), collectionName);
    }

    /**
     * 向量相似度搜索
     */
    public SearchResult searchSimilar(List<Float> queryVector, int topK, String expr) {
        SearchReq req = SearchReq.builder()
                .collectionName(collectionName)
                .annsField("embedding")
                .data(List.of(new FloatVec(queryVector)))
                .topK(topK)
                .expr(expr)
                .outputFields(List.of("chunk_id", "content", "metadata"))
                .params(Map.of("nprobe", 16))
                .build();

        SearchResp resp = client.search(req);
        if (resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) {
            return new SearchResult(List.of());
        }

        List<ScoredDoc> docs = new ArrayList<>();
        for (var result : resp.getSearchResults().get(0)) {
            Map<String, Object> entity = (Map<String, Object>) result.getEntity();
            docs.add(new ScoredDoc(
                    (String) entity.get("chunk_id"),
                    (String) entity.get("content"),
                    (String) entity.get("metadata"),
                    result.getScore()
            ));
        }
        return new SearchResult(docs);
    }

    /**
     * 按 docId 前缀删除文档（所有 chunk）
     */
    public void deleteDocument(String docIdPrefix) {
        client.delete(DeleteReq.builder()
                .collectionName(collectionName)
                .expr("chunk_id like \"" + docIdPrefix + "%\"")
                .build());
        log.info("🗑️ 删除文档: {}*", docIdPrefix);
    }

    /**
     * 列出文档（按分组去重）
     */
    public List<String> listDocuments() {
        QueryResp resp = client.query(QueryReq.builder()
                .collectionName(collectionName)
                .outputFields(List.of("chunk_id", "metadata"))
                .limit(10000)
                .build());
        if (resp.getQueryResults() == null) return List.of();

        Set<String> docs = new LinkedHashSet<>();
        for (var row : resp.getQueryResults()) {
            String chunkId = (String) row.get("chunk_id");
            if (chunkId != null && chunkId.contains("_")) {
                docs.add(chunkId.substring(0, chunkId.lastIndexOf('_')));
            }
        }
        return new ArrayList<>(docs);
    }

    /**
     * 集合统计
     */
    public Map<String, Object> getStats() {
        QueryResp resp = client.query(QueryReq.builder()
                .collectionName(collectionName)
                .outputFields(List.of("count(*)"))
                .build());
        long count = 0;
        if (resp.getQueryResults() != null && !resp.getQueryResults().isEmpty()) {
            Object val = resp.getQueryResults().get(0).get("count(*)");
            count = val instanceof Number ? ((Number) val).longValue() : 0;
        }
        return Map.of("collection", collectionName, "totalChunks", count);
    }

    // ====== 内部数据结构 ======

    public static class KnowledgeChunk {
        public final String chunkId;
        public final String content;
        public final List<Float> embedding;
        public final String metadataJson;

        public KnowledgeChunk(String chunkId, String content, List<Float> embedding, String metadataJson) {
            this.chunkId = chunkId;
            this.content = content;
            this.embedding = embedding;
            this.metadataJson = metadataJson;
        }
    }

    public static class SearchResult {
        public final List<ScoredDoc> docs;

        public SearchResult(List<ScoredDoc> docs) { this.docs = docs; }
        public boolean isEmpty() { return docs.isEmpty(); }
    }

    public static class ScoredDoc {
        public final String chunkId;
        public final String content;
        public final String metadataJson;
        public final double score;

        public ScoredDoc(String chunkId, String content, String metadataJson, double score) {
            this.chunkId = chunkId;
            this.content = content;
            this.metadataJson = metadataJson;
            this.score = score;
        }
    }
}
```

---

### Task 1.6: 种子数据初始化

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\service\KnowledgeInitService.java`

**Interfaces:**
- Consumes: `MilvusClientWrapper`, `EmbeddingService`
- Produces: 启动时自动写入种子数据到 cs_knowledge

- [ ] **Step 1: 创建 KnowledgeInitService**

```java
package com.cs.agent.service;

import com.cs.agent.vector.EmbeddingService;
import com.cs.agent.vector.MilvusClientWrapper;
import com.cs.agent.vector.MilvusClientWrapper.KnowledgeChunk;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 启动时初始化种子知识库数据
 *
 * 如果 cs_knowledge 集合为空，自动写入预设的种子数据。
 * 种子数据与原项目的 Mock 数据兼容。
 */
@Service
public class KnowledgeInitService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeInitService.class);

    private final MilvusClientWrapper milvusClientWrapper;
    private final EmbeddingService embeddingService;

    public KnowledgeInitService(MilvusClientWrapper milvusClientWrapper, EmbeddingService embeddingService) {
        this.milvusClientWrapper = milvusClientWrapper;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        try {
            var stats = milvusClientWrapper.getStats();
            long count = (long) stats.get("totalChunks");
            if (count > 0) {
                log.info("📚 知识库已有 {} 条数据，跳过种子初始化", count);
                return;
            }

            log.info("📚 知识库为空，写入种子数据...");
            List<SeedEntry> seeds = buildSeedData();
            List<String> texts = seeds.stream().map(s -> s.text).toList();

            List<List<Float>> embeddings = embeddingService.embedBatch(texts);

            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (int i = 0; i < seeds.size(); i++) {
                SeedEntry entry = seeds.get(i);
                chunks.add(new KnowledgeChunk(
                        entry.chunkId,
                        entry.text,
                        embeddings.get(i),
                        entry.metadataJson
                ));
            }

            milvusClientWrapper.insertChunks(chunks);
            log.info("✅ 种子数据初始化完成，共写入 {} 条", seeds.size());
        } catch (Exception e) {
            log.warn("⚠️ 种子数据初始化失败（Milvus 未就绪？）: {}", e.getMessage());
        }
    }

    private List<SeedEntry> buildSeedData() {
        List<SeedEntry> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String ts = today.toString();

        // ===== 商品数据 (category=product) =====
        list.add(seed("prod_001", "漫步者 G2 无线蓝牙耳机 299元 防汗佩戴稳固 适合运动跑步 续航20小时 好评5000+", """
                {"sourceFile":"seed","category":"product","tags":"耳机,蓝牙,运动","price":299,"brand":"漫步者","status":"上架","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("prod_002", "小米 Buds 5 主动降噪 499元 音质出色 续航30小时 支持无线充电 好评3200+", """
                {"sourceFile":"seed","category":"product","tags":"耳机,蓝牙,降噪","price":499,"brand":"小米","status":"上架","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("prod_003", "华为 FreeBuds 6i 入耳式 359元 主动降噪 舒适佩戴 好评2800+", """
                {"sourceFile":"seed","category":"product","tags":"耳机,蓝牙,降噪","price":359,"brand":"华为","status":"上架","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("prod_004", "iPhone 16 Pro Max 9999元 A18芯片 钛金属 512GB 超视网膜显示屏", """
                {"sourceFile":"seed","category":"product","tags":"手机,iPhone","price":9999,"brand":"Apple","status":"上架","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("prod_005", "华为 Mate 70 Pro 7999元 鸿蒙系统 昆仑玻璃 5000万超聚光影像", """
                {"sourceFile":"seed","category":"product","tags":"手机,华为","price":7999,"brand":"华为","status":"上架","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("prod_006", "小米 15 Ultra 6499元 骁龙8至尊版 徕卡影像 6000mAh大电池", """
                {"sourceFile":"seed","category":"product","tags":"手机,小米","price":6499,"brand":"小米","status":"上架","uploadedAt":"%s"}""".formatted(ts)));

        // ===== 政策数据 (category=policy) =====
        list.add(seed("policy_001", "七天无理由退货：商品签收后7天内可申请退货，需保证商品完好不影响二次销售。电子产品适用此政策。", """
                {"sourceFile":"seed","category":"policy","tags":"退货,电子产品","policyType":"退货","applicableScope":"全部商品","effectiveFrom":"2026-01-01","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("policy_002", "退款规则：确认退款后金额原路返回，预计1-3个工作日到账。优惠券抵扣部分不退现金。", """
                {"sourceFile":"seed","category":"policy","tags":"退款","policyType":"退款","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("policy_003", "退换货条件：商品需完好不影响二次销售，包装配件齐全。退回运费由买家承担（商品质量问题除外）。", """
                {"sourceFile":"seed","category":"policy","tags":"退换货","policyType":"退换货","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("policy_004", "物流政策：满99元包邮，不满99元运费8元。默认发中通快递，加急可发顺丰到付。", """
                {"sourceFile":"seed","category":"policy","tags":"物流","policyType":"物流","uploadedAt":"%s"}""".formatted(ts)));

        // ===== FAQ数据 (category=faq) =====
        list.add(seed("faq_001", "如何查询订单：提供订单号即可查询订单状态、商品信息和物流进度。订单号格式为 ORD 开头加数字。", """
                {"sourceFile":"seed","category":"faq","tags":"订单","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("faq_002", "如何申请退款：联系在线客服提供订单号和退款原因，客服会引导您完成退款流程。退款金额1-3个工作日原路返回。", """
                {"sourceFile":"seed","category":"faq","tags":"退款","uploadedAt":"%s"}""".formatted(ts)));
        list.add(seed("faq_003", "如何投诉：通过在线客服提交投诉内容，我们会记录并提交相关部门处理，承诺24小时内回复。紧急情况可升级人工客服加急处理。", """
                {"sourceFile":"seed","category":"faq","tags":"投诉","uploadedAt":"%s"}""".formatted(ts)));

        return list;
    }

    private static SeedEntry seed(String chunkId, String text, String metadataJson) {
        return new SeedEntry(chunkId, text, metadataJson);
    }

    private record SeedEntry(String chunkId, String text, String metadataJson) {}
}
```

---

## Phase 2: RAG 知识检索 + Advisor 溯源

### Task 2.1: Advisor 模型类

**Files:**
- Create: `AdvisedContext.java`, `CitedReply.java`, `Citation.java`, `RetrievalSpec.java`

**Interfaces:**
- Consumes: 无
- Produces: KnowledgeAdvisor 使用的模型类

- [ ] **Step 1: 创建 AdvisedContext.java**

```java
package com.cs.agent.vector.advisor;

import java.util.ArrayList;
import java.util.List;

/**
 * Advisor 检索结果 —— 包含文档和拼接后的上下文
 */
public class AdvisedContext {
    private final List<RetrievedDoc> documents;
    private final String combinedContext;

    public AdvisedContext(List<RetrievedDoc> documents, String combinedContext) {
        this.documents = documents != null ? documents : List.of();
        this.combinedContext = combinedContext != null ? combinedContext : "";
    }

    public List<RetrievedDoc> getDocuments() { return documents; }
    public String getCombinedContext() { return combinedContext; }
    public boolean isEmpty() { return documents.isEmpty(); }

    /** 单个检索到的文档 */
    public static class RetrievedDoc {
        private final String chunkId;
        private final String content;
        private final String metadataJson;
        private final double score;

        public RetrievedDoc(String chunkId, String content, String metadataJson, double score) {
            this.chunkId = chunkId;
            this.content = content;
            this.metadataJson = metadataJson;
            this.score = score;
        }

        public String getChunkId() { return chunkId; }
        public String getContent() { return content; }
        public String getMetadataJson() { return metadataJson; }
        public double getScore() { return score; }
    }
}
```

- [ ] **Step 2: 创建 Citation.java**

```java
package com.cs.agent.vector.advisor;

import java.util.Map;

/**
 * 单个引用 —— 标识回复中某段内容的来源
 */
public class Citation {
    private final String docId;
    private final String collection;
    private final double score;
    private final String snippet;
    private final Map<String, Object> metadata;

    public Citation(String docId, String collection, double score, String snippet, Map<String, Object> metadata) {
        this.docId = docId;
        this.collection = collection;
        this.score = score;
        this.snippet = snippet;
        this.metadata = metadata;
    }

    public String getDocId() { return docId; }
    public String getCollection() { return collection; }
    public double getScore() { return score; }
    public String getSnippet() { return snippet; }
    public Map<String, Object> getMetadata() { return metadata; }
}
```

- [ ] **Step 3: 创建 CitedReply.java**

```java
package com.cs.agent.vector.advisor;

import java.util.List;
import java.util.UUID;

/**
 * 溯源回复 —— 回复文本 + 引用列表
 */
public class CitedReply {
    private final String replyId;
    private final String reply;
    private final List<Citation> citations;

    public CitedReply(String reply, List<Citation> citations) {
        this.replyId = "r_" + UUID.randomUUID().toString().substring(0, 8);
        this.reply = reply;
        this.citations = citations != null ? citations : List.of();
    }

    public String getReplyId() { return replyId; }
    public String getReply() { return reply; }
    public List<Citation> getCitations() { return citations; }
    public boolean hasCitations() { return !citations.isEmpty(); }
}
```

- [ ] **Step 4: 创建 RetrievalSpec.java**

```java
package com.cs.agent.vector.advisor;

import java.util.*;

/**
 * 检索规格 —— 控制检索的目标集合、过滤条件、数量等
 */
public class RetrievalSpec {
    private final int topK;
    private final double minScore;
    private final String expr;  // Milvus 标量过滤表达式

    public RetrievalSpec(int topK, double minScore, String expr) {
        this.topK = topK;
        this.minScore = minScore;
        this.expr = expr;
    }

    public int getTopK() { return topK; }
    public double getMinScore() { return minScore; }
    public String getExpr() { return expr; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int topK = 3;
        private double minScore = 0.6;
        private String expr = "";

        public Builder topK(int topK) { this.topK = topK; return this; }
        public Builder minScore(double minScore) { this.minScore = minScore; return this; }
        /** 设置 Milvus 过滤表达式，如 metadata['category'] == 'product' */
        public Builder expr(String expr) { this.expr = expr; return this; }
        public RetrievalSpec build() { return new RetrievalSpec(topK, minScore, expr); }
    }
}
```

---

### Task 2.2: Metadata 管理

**Files:**
- Create: `DocMetadata.java`, `CollectionSchema.java`, `MetadataFilter.java`, `ProductMeta.java`, `PolicyMeta.java`, `FaqMeta.java`

**Interfaces:**
- Consumes: 无
- Produces: KnowledgeAdvisor 使用的元数据过滤

- [ ] **Step 1: 创建 DocMetadata.java**

```java
package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

/**
 * 知识库文档元数据接口
 */
public interface DocMetadata {
    String getCategory();
    String getTags();
    LocalDateTime getUpdatedAt();
}
```

- [ ] **Step 2: 创建 CollectionSchema.java**

```java
package com.cs.agent.vector.advisor.metadata;

/**
 * 知识库集合 Schema 定义
 *
 * 每个集合对应一种知识类型，metadata 中有特定的分类字段。
 */
public enum CollectionSchema {

    PRODUCT("cs_knowledge", "product",
            "商品知识 — 名称、分类、价格、品牌"),
    POLICY("cs_knowledge", "policy",
            "政策知识 — 退换货/退款/物流政策"),
    FAQ("cs_knowledge", "faq",
            "常见问题 — 订单/退款/投诉指南");

    /** Milvus 集合名 */
    private final String collectionName;
    /** 分类值（metadata.category） */
    private final String category;
    private final String description;

    CollectionSchema(String collectionName, String category, String description) {
        this.collectionName = collectionName;
        this.category = category;
        this.description = description;
    }

    public String getCollectionName() { return collectionName; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }

    /** 获取该 schema 的 Milvus 过滤表达式：metadata['category'] == 'xxx' */
    public String categoryExpr() {
        return "metadata['category'] == '" + category + "'";
    }
}
```

- [ ] **Step 3: 创建 MetadataFilter.java**

```java
package com.cs.agent.vector.advisor.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 标量过滤表达式构建器
 *
 * 构建 JSON 字段过滤表达式，如：
 *   metadata['category'] == 'product' && metadata['price'] < 500
 */
public class MetadataFilter {
    private final List<String> conditions = new ArrayList<>();

    private MetadataFilter() {}

    public static MetadataFilter create() { return new MetadataFilter(); }

    /** 等于 */
    public MetadataFilter eq(String field, Object value) {
        if (value instanceof String) {
            conditions.add("metadata['" + field + "'] == '" + value + "'");
        } else {
            conditions.add("metadata['" + field + "'] == " + value);
        }
        return this;
    }

    /** 不等于 */
    public MetadataFilter neq(String field, Object value) {
        if (value instanceof String) {
            conditions.add("metadata['" + field + "'] != '" + value + "'");
        } else {
            conditions.add("metadata['" + field + "'] != " + value);
        }
        return this;
    }

    /** 小于 */
    public MetadataFilter lt(String field, Number value) {
        conditions.add("metadata['" + field + "'] < " + value);
        return this;
    }

    /** 大于 */
    public MetadataFilter gt(String field, Number value) {
        conditions.add("metadata['" + field + "'] > " + value);
        return this;
    }

    /** 小于等于 */
    public MetadataFilter lte(String field, Number value) {
        conditions.add("metadata['" + field + "'] <= " + value);
        return this;
    }

    /** 包含标签 */
    public MetadataFilter hasTag(String tag) {
        conditions.add("json_contains(metadata['tags'], '" + tag + "')");
        return this;
    }

    /** 分类 */
    public MetadataFilter category(String category) {
        return eq("category", category);
    }

    public String build() {
        if (conditions.isEmpty()) return "";
        return String.join(" && ", conditions);
    }

    public boolean isEmpty() { return conditions.isEmpty(); }
}
```

- [ ] **Step 4: 创建 ProductMeta.java**

```java
package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

/** 商品文档元数据 */
public class ProductMeta implements DocMetadata {
    private String category = "product";
    private String tags;
    private double price;
    private String brand;
    private String status = "上架";
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Override public String getCategory() { return category; }
    @Override public String getTags() { return tags; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }

    public double getPrice() { return price; }
    public String getBrand() { return brand; }
    public String getStatus() { return status; }
}
```

- [ ] **Step 5: 创建 PolicyMeta.java**

```java
package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

/** 政策文档元数据 */
public class PolicyMeta implements DocMetadata {
    private String category = "policy";
    private String tags;
    private String policyType;
    private String applicableScope = "全部商品";
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Override public String getCategory() { return category; }
    @Override public String getTags() { return tags; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getPolicyType() { return policyType; }
    public String getApplicableScope() { return applicableScope; }
}
```

- [ ] **Step 6: 创建 FaqMeta.java**

```java
package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

/** FAQ 文档元数据 */
public class FaqMeta implements DocMetadata {
    private String category = "faq";
    private String tags;
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Override public String getCategory() { return category; }
    @Override public String getTags() { return tags; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

---

### Task 2.3: KnowledgeBaseStore

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\vector\store\KnowledgeBaseStore.java`

**Interfaces:**
- Consumes: `MilvusClientWrapper`, `EmbeddingService`
- Produces: `searchKnowledge(query, spec)`, `insertDocument(chunks)`, `deleteDocument(docId)`, `listDocuments()`

- [ ] **Step 1: 创建 KnowledgeBaseStore.java**

```java
package com.cs.agent.vector.store;

import com.cs.agent.vector.EmbeddingService;
import com.cs.agent.vector.MilvusClientWrapper;
import com.cs.agent.vector.MilvusClientWrapper.KnowledgeChunk;
import com.cs.agent.vector.MilvusClientWrapper.ScoredDoc;
import com.cs.agent.vector.advisor.AdvisedContext;
import com.cs.agent.vector.advisor.AdvisedContext.RetrievedDoc;
import com.cs.agent.vector.advisor.RetrievalSpec;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库向量存储
 *
 * 封装知识库的检索和写入操作。
 */
@Repository
public class KnowledgeBaseStore {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeBaseStore.class);

    private final MilvusClientWrapper milvusClientWrapper;
    private final EmbeddingService embeddingService;

    public KnowledgeBaseStore(MilvusClientWrapper milvusClientWrapper, EmbeddingService embeddingService) {
        this.milvusClientWrapper = milvusClientWrapper;
        this.embeddingService = embeddingService;
    }

    /**
     * 检索知识库 —— 向量相似度搜索 + 可选标量过滤
     */
    public AdvisedContext searchKnowledge(String query, RetrievalSpec spec) {
        List<Float> queryVector = embeddingService.embed(query);
        var result = milvusClientWrapper.searchSimilar(queryVector, spec.getTopK(), spec.getExpr());

        if (result.isEmpty()) {
            log.info("🔍 知识库检索无结果: query={}", query);
            return new AdvisedContext(List.of(), "");
        }

        List<RetrievedDoc> docs = result.docs.stream()
                .filter(d -> d.score >= spec.getMinScore())
                .map(d -> new RetrievedDoc(d.chunkId, d.content, d.metadataJson, d.score))
                .collect(Collectors.toList());

        String combined = docs.stream()
                .map(d -> "【来源:" + d.getChunkId() + "】" + d.getContent())
                .collect(Collectors.joining("\n\n"));

        log.info("🔍 知识库检索: query={}, 命中={}条, topScore={}", query, docs.size(),
                docs.isEmpty() ? 0 : docs.get(0).getScore());

        return new AdvisedContext(docs, combined);
    }

    /** 插入文档切片 */
    public void insertChunks(List<KnowledgeChunk> chunks) {
        milvusClientWrapper.insertChunks(chunks);
    }

    /** 删除文档（按 docId 前缀） */
    public void deleteDocument(String docIdPrefix) {
        milvusClientWrapper.deleteDocument(docIdPrefix);
    }

    /** 列出文档 */
    public List<String> listDocuments() {
        return milvusClientWrapper.listDocuments();
    }

    /** 统计 */
    public Map<String, Object> getStats() {
        return milvusClientWrapper.getStats();
    }
}
```

---

### Task 2.4: SessionLogStore — 溯源日志存储

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\vector\store\SessionLogStore.java`

**Interfaces:**
- Consumes: `MilvusClientV2`
- Produces: `logCitation(...)` 写入溯源记录

- [ ] **Step 1: 创建 SessionLogStore.java**

```java
package com.cs.agent.vector.store;

import com.cs.agent.vector.advisor.Citation;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 溯源日志存储
 *
 * 记录每次回复引用了哪些知识库文档。
 */
@Repository
public class SessionLogStore {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionLogStore.class);

    private final MilvusClientV2 milvusClient;
    private final String collectionName;

    public SessionLogStore(MilvusClientV2 milvusClient,
                           @Value("${milvus.collections.session-log:cs_session_log}") String collectionName) {
        this.milvusClient = milvusClient;
        this.collectionName = collectionName;
    }

    public static class CitationLog {
        public final String replyId;
        public final String sessionId;
        public final String workerName;
        public final List<Map<String, Object>> citedDocs;
        public final String replyContent;
        public final String createdAt;

        public CitationLog(String replyId, String sessionId, String workerName,
                           List<Citation> citations, String replyContent) {
            this.replyId = replyId;
            this.sessionId = sessionId;
            this.workerName = workerName;
            this.citedDocs = citations.stream().map(c -> Map.<String, Object>of(
                    "docId", c.getDocId(),
                    "score", c.getScore(),
                    "snippet", c.getSnippet(),
                    "metadata", c.getMetadata()
            )).toList();
            this.replyContent = replyContent.length() > 8000 ? replyContent.substring(0, 8000) : replyContent;
            this.createdAt = LocalDateTime.now().toString();
        }
    }

    /** 记录溯源信息 */
    public void logCitation(CitationLog logEntry) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply_id", logEntry.replyId);
            data.put("session_id", logEntry.sessionId);
            data.put("worker_name", logEntry.workerName);
            data.put("cited_docs", logEntry.citedDocs);
            data.put("reply_content", logEntry.replyContent);
            data.put("created_at", logEntry.createdAt);

            InsertReq req = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(data))
                    .build();
            milvusClient.insert(req);
        } catch (Exception e) {
            log.warn("溯源日志写入失败: {}", e.getMessage());
        }
    }
}
```

---

### Task 2.5: KnowledgeAdvisor — 检索+溯源核心

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\vector\advisor\KnowledgeAdvisor.java`

**Interfaces:**
- Consumes: `KnowledgeBaseStore`, `SessionLogStore`
- Produces: `retrieve(query, spec)`, `cite(reply, sources, sessionId, workerName)`, `trackConsumption(...)`

- [ ] **Step 1: 创建 KnowledgeAdvisor.java**

```java
package com.cs.agent.vector.advisor;

import com.cs.agent.vector.advisor.AdvisedContext.RetrievedDoc;
import com.cs.agent.vector.advisor.metadata.CollectionSchema;
import com.cs.agent.vector.store.KnowledgeBaseStore;
import com.cs.agent.vector.store.SessionLogStore;
import com.cs.agent.vector.store.SessionLogStore.CitationLog;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识顾问 —— Worker 的知识检索入口
 *
 * 职责：
 * 1. 根据用户意图检索最相关的知识库文档
 * 2. 将回复与源文档绑定，生成带引用的回复
 * 3. 记录溯源信息到审计日志
 */
@Service
public class KnowledgeAdvisor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeAdvisor.class);

    private final KnowledgeBaseStore knowledgeBaseStore;
    private final SessionLogStore sessionLogStore;

    public KnowledgeAdvisor(KnowledgeBaseStore knowledgeBaseStore, SessionLogStore sessionLogStore) {
        this.knowledgeBaseStore = knowledgeBaseStore;
        this.sessionLogStore = sessionLogStore;
    }

    /**
     * 检索知识库
     *
     * @param query 用户问题
     * @param spec  检索规格（topK、minScore、过滤条件）
     */
    public AdvisedContext retrieve(String query, RetrievalSpec spec) {
        return knowledgeBaseStore.searchKnowledge(query, spec);
    }

    /**
     * 按 Schema 检索（简化版——自动设置 category 过滤）
     */
    public AdvisedContext retrieveBySchema(String query, CollectionSchema schema) {
        RetrievalSpec spec = RetrievalSpec.builder()
                .topK(3)
                .minScore(0.6)
                .expr(schema.categoryExpr())
                .build();
        return retrieve(query, spec);
    }

    /**
     * 将回复与源文档绑定，生成带引用的溯源回复
     */
    public CitedReply cite(String reply, List<RetrievedDoc> sources) {
        List<Citation> citations = sources.stream()
                .map(doc -> {
                    Map<String, Object> meta = parseMetadata(doc.getMetadataJson());
                    return new Citation(
                            doc.getChunkId(),
                            "cs_knowledge",
                            doc.getScore(),
                            doc.getContent().length() > 100
                                    ? doc.getContent().substring(0, 100) + "..."
                                    : doc.getContent(),
                            meta
                    );
                })
                .collect(Collectors.toList());

        return new CitedReply(reply, citations);
    }

    /**
     * 记录溯源信息
     */
    public void trackConsumption(String sessionId, String workerName, CitedReply citedReply) {
        if (citedReply == null || !citedReply.hasCitations()) return;

        CitationLog logEntry = new CitationLog(
                citedReply.getReplyId(),
                sessionId,
                workerName,
                citedReply.getCitations(),
                citedReply.getReply()
        );
        sessionLogStore.logCitation(logEntry);
        log.info("📝 记录溯源: sessionId={}, worker={}, citations={}",
                sessionId, workerName, citedReply.getCitations().size());
    }

    /** 解析 metadata JSON 为 Map */
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            // 简单解析 JSON 键值对
            String clean = json.replaceAll("[{}\"]", "");
            for (String pair : clean.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    result.put(kv[0].trim(), kv[1].trim());
                }
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
```

---

### Task 2.6: 修改 CsAgentState — 新增 citations 字段

**Files:**
- Modify: `C:\multi-agent-cs\src\main\java\com\cs\agent\state\CsAgentState.java`

- [ ] **Step 1: 在 SCHEMA 中添加 citations 通道**

找到 `finalReply` 行，在其后添加：
```java
"citations", base((old, newVal) -> newVal),
```

- [ ] **Step 2: 添加 citations 访问方法**

在 `finalReply()` 方法后添加：
```java
/** 获取溯源引用列表 */
public List<Map<String, Object>> citations() {
    return (List<Map<String, Object>>) value("citations").orElse(List.of());
}
```

---

### Task 2.7: 修改 Worker 节点 — 注入 Advisor

**Files:**
- Modify: `OrderAgentNode.java`, `ProductAgentNode.java`, `ReturnAgentNode.java`, `ComplaintAgentNode.java`

每个 Worker 注入 `KnowledgeAdvisor`，在调用 LLM 前先检索知识库，将检索结果注入 context。

- [ ] **Step 1: 修改 ProductAgentNode — 注入 Advisor 替换 Mock**

```java
// 新增字段和构造器参数
private final KnowledgeAdvisor knowledgeAdvisor;

public ProductAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                        ProductTools productTools,
                        KnowledgeAdvisor knowledgeAdvisor) {
    this.chatModel = chatModel;
    this.productTools = productTools;
    this.knowledgeAdvisor = knowledgeAdvisor;
}

// 在 apply() 方法中，调用 LLM 前先检索知识库
@Override
public Map<String, Object> apply(CsAgentState state) {
    String userMessage = state.lastUserMessage();
    log.info("🛍️ [ProductWorker] 处理: {}", userMessage);

    // ★ 检索知识库
    AdvisedContext ctx = knowledgeAdvisor.retrieveBySchema(userMessage, CollectionSchema.PRODUCT);
    String knowledgeContext = ctx.isEmpty() ? "" : "【参考知识】\n" + ctx.getCombinedContext();

    // ... 原有 LLM 调用逻辑，systemPrompt 中追加 knowledgeContext
    String enhancedPrompt = SYSTEM_PROMPT + "\n\n" + knowledgeContext;
    // ... 调用 LLM

    // ★ 生成回复后绑定引用
    CitedReply citedReply = knowledgeAdvisor.cite(reply, ctx.getDocuments());
    knowledgeAdvisor.trackConsumption(state.sessionId(), "product_agent", citedReply);

    return Map.of(
            "messages", Map.of("role", "assistant", "content", citedReply.getReply()),
            "finalReply", citedReply.getReply(),
            "citations", citedReply.getCitations().stream().map(c -> Map.of(
                    "docId", c.getDocId(),
                    "score", c.getScore(),
                    "snippet", c.getSnippet(),
                    "metadata", c.getMetadata()
            )).toList(),
            "workerResults", Map.of(...)
    );
}
```

完整代码改动（以 ProductAgentNode 为例）：

```java
package com.cs.agent.node.agent;

import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.ProductTools;
import com.cs.agent.vector.advisor.AdvisedContext;
import com.cs.agent.vector.advisor.CitedReply;
import com.cs.agent.vector.advisor.KnowledgeAdvisor;
import com.cs.agent.vector.advisor.metadata.CollectionSchema;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;

@Component("productAgentNode")
public class ProductAgentNode implements NodeAction<CsAgentState> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductAgentNode.class);

    private final ChatLanguageModel chatModel;
    private final ProductTools productTools;
    private final KnowledgeAdvisor knowledgeAdvisor;

    private static final String SYSTEM_PROMPT = """
            你是电商客服系统的商品专员。
            根据参考知识和用户问题推荐合适的商品。
            规则：
            - 回答简洁友好，用中文
            - 优先参考提供的知识内容
            """;

    public ProductAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                            ProductTools productTools,
                            KnowledgeAdvisor knowledgeAdvisor) {
        this.chatModel = chatModel;
        this.productTools = productTools;
        this.knowledgeAdvisor = knowledgeAdvisor;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("🛍️ [ProductWorker] 处理: {}", userMessage);

        // ★ 检索知识库
        AdvisedContext ctx = knowledgeAdvisor.retrieveBySchema(userMessage, CollectionSchema.PRODUCT);
        String knowledgeContext = ctx.isEmpty() ? "" : "【参考知识】\n" + ctx.getCombinedContext();

        List<ToolSpecification> tool = List.of(
                ToolSpecification.builder()
                        .name("searchProduct")
                        .description("根据关键词搜索商品")
                        .addParameter("keyword", STRING)
                        .build()
        );

        String enhancedPrompt = SYSTEM_PROMPT;
        if (!knowledgeContext.isEmpty()) {
            enhancedPrompt += "\n\n" + knowledgeContext;
        }

        var response = chatModel.generate(
                List.of(SystemMessage.from(enhancedPrompt), UserMessage.from(userMessage)),
                tool
        );

        String reply;
        if (response.content().hasToolExecutionRequests()) {
            var requests = response.content().toolExecutionRequests();
            List<ChatMessage> msgs = new ArrayList<>();
            msgs.add(SystemMessage.from(enhancedPrompt));
            msgs.add(UserMessage.from(userMessage));
            msgs.add(response.content());

            for (var req : requests) {
                String keyword = extractArg(req.arguments(), "keyword");
                String result = productTools.searchProduct(keyword);
                msgs.add(ToolExecutionResultMessage.from(req, result));
            }
            reply = chatModel.generate(msgs).content().text();
        } else {
            reply = response.content().text();
        }

        // ★ 生成溯源引用
        CitedReply citedReply = knowledgeAdvisor.cite(reply, ctx.getDocuments());
        knowledgeAdvisor.trackConsumption(state.sessionId(), "product_agent", citedReply);

        log.info("  → 回复: {}", citedReply.getReply());

        return Map.of(
                "messages", Map.of("role", "assistant", "content", citedReply.getReply()),
                "finalReply", citedReply.getReply(),
                "citations", citedReply.getCitations().stream().map(c -> Map.of(
                        "docId", c.getDocId(),
                        "score", c.getScore(),
                        "snippet", c.getSnippet(),
                        "metadata", c.getMetadata()
                )).toList(),
                "workerResults", Map.of(
                        "workerName", "product_agent",
                        "result", citedReply.getReply(),
                        "success", true
                )
        );
    }

    private String extractArg(String jsonArgs, String key) {
        if (jsonArgs == null) return "";
        String search = "\"" + key + "\":\"";
        int start = jsonArgs.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = jsonArgs.indexOf("\"", start);
        return end < 0 ? jsonArgs.substring(start) : jsonArgs.substring(start, end);
    }
}
```

同样改动应用到 `ReturnAgentNode.java`、`ComplaintAgentNode.java`、`OrderAgentNode.java`，关键差异：
- `OrderAgentNode` → `CollectionSchema` 不用（不从知识库检索订单数据，订单从 MySQL 查）
- `ReturnAgentNode` → `CollectionSchema.POLICY`（检索退换货政策）
- `ComplaintAgentNode` → `CollectionSchema.FAQ`（检索 FAQ + 投诉处理指南）

---

### Task 2.8: 修改 ChatOrchestrator — 传递 citations

**Files:**
- Modify: `C:\multi-agent-cs\src\main\java\com\cs\agent\orchestrator\ChatOrchestrator.java`

- [ ] **Step 1: 在 processMessage 返回中添加 citations 字段**

```java
// 在 extractReply 后添加 citations 提取
@SuppressWarnings("unchecked")
private List<Map<String, Object>> extractCitations(CsAgentState state) {
    Object val = state.value("citations").orElse(null);
    if (val instanceof List) {
        return (List<Map<String, Object>>) val;
    }
    return List.of();
}
```

同时修改 processMessage 方法，在返回 Map 中包含 citations：
```java
public Map<String, Object> processMessageWithCitations(String sessionId, String userMessage) {
    // ... 现有逻辑 ...
    if (result.isPresent()) {
        CsAgentState finalState = result.get();
        String reply = finalState.finalReply();
        if (reply.isEmpty()) reply = finalState.lastAiReply();
        
        List<Map<String, Object>> citations = extractCitations(finalState);
        
        sessionStore.addMessage(sessionId, "assistant", reply);
        sessionStates.put(sessionId, finalState);
        
        return Map.of(
            "sessionId", sessionId,
            "reply", reply,
            "citations", citations
        );
    }
    return Map.of("sessionId", sessionId, "reply", "好的，已为您处理。", "citations", List.of());
}
```

---

## Phase 3: 文件上传

### Task 3.1: DocumentParser — 解析器接口 + PDF/TXT/MD 实现

**Files:**
- Create: `DocumentParser.java`, `PdfParser.java`, `TextParser.java`, `MarkdownParser.java`

- [ ] **Step 1: 创建 DocumentParser.java**

```java
package com.cs.agent.vector.file.parser;

import java.util.List;

/**
 * 文档解析器接口
 */
public interface DocumentParser {
    /** 解析文件内容为纯文本 */
    String parse(byte[] fileContent, String fileName);

    /** 支持的文件扩展名 */
    List<String> supportedExtensions();
}
```

- [ ] **Step 2: 创建 PdfParser.java**

```java
package com.cs.agent.vector.file.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PdfParser implements DocumentParser {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PdfParser.class);

    @Override
    public String parse(byte[] fileContent, String fileName) {
        try (PDDocument doc = Loader.loadPDF(fileContent)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (Exception e) {
            log.error("PDF 解析失败: {}", fileName, e);
            return "【PDF 解析失败: " + e.getMessage() + "】";
        }
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of("pdf");
    }
}
```

- [ ] **Step 3: 创建 TextParser.java**

```java
package com.cs.agent.vector.file.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TextParser implements DocumentParser {
    @Override
    public String parse(byte[] fileContent, String fileName) {
        return new String(fileContent, StandardCharsets.UTF_8);
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of("txt");
    }
}
```

- [ ] **Step 4: 创建 MarkdownParser.java**

```java
package com.cs.agent.vector.file.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 解析器
 *
 * 提取纯文本内容，保留标题层级作为结构信息。
 */
@Component
public class MarkdownParser implements DocumentParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\([^)]+\\)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern IMG_PATTERN = Pattern.compile("!\\[[^]]*]\\([^)]+\\)");

    @Override
    public String parse(byte[] fileContent, String fileName) {
        String content = new String(fileContent, StandardCharsets.UTF_8);

        // 去除代码块
        content = CODE_BLOCK_PATTERN.matcher(content).replaceAll("");

        // 标题加标记
        Matcher m = HEADING_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int level = m.group(1).length();
            m.appendReplacement(sb, "【" + "h" + level + "】" + m.group(2));
        }
        m.appendTail(sb);

        // 链接替换为文本
        content = LINK_PATTERN.matcher(sb.toString()).replaceAll("$1");
        // 去除图片
        content = IMG_PATTERN.matcher(content).replaceAll("");
        // 去除 markdown 符号
        content = content.replaceAll("[*_~`>|]", "");

        return content.trim();
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of("md", "markdown");
    }
}
```

---

### Task 3.2: DocumentChunker — 文本切片

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\vector\file\chunker\DocumentChunker.java`

- [ ] **Step 1: 创建 DocumentChunker.java**

```java
package com.cs.agent.vector.file.chunker;

import com.cs.agent.vector.file.dto.DocumentChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切片器
 *
 * 策略：按段落分割，每个 chunk 不超过指定 token 数。
 * token 估算：中文约 1.5 字/token，按字符数近似估算。
 */
@Component
public class DocumentChunker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentChunker.class);

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentChunker(@Value("${knowledge.chunk.size:500}") int chunkSize,
                           @Value("${knowledge.chunk.overlap:50}") int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 将文本切分为 chunk 列表
     */
    public List<DocumentChunk> chunk(String text, String docId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        // 按段落分割（多个换行符）
        String[] paragraphs = text.split("\\n\\s*\\n");

        StringBuilder current = new StringBuilder();
        int chunkIndex = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 如果当前段落实体太长，按句子切分
            if (trimmed.length() > chunkSize * 2 && trimmed.contains("。")) {
                // 先 flush 当前累积
                if (current.length() > 0) {
                    chunks.add(createChunk(docId, chunkIndex++, current.toString().trim()));
                    current.setLength(0);
                }
                // 按句子切分长段落
                String[] sentences = trimmed.split("(?<=。|！|？)");
                for (String sentence : sentences) {
                    String s = sentence.trim();
                    if (s.isEmpty()) continue;
                    if (current.length() + s.length() > chunkSize && current.length() > 0) {
                        chunks.add(createChunk(docId, chunkIndex++, current.toString().trim()));
                        current.setLength(0);
                    }
                    current.append(s);
                }
            } else {
                // 正常段落，累积到超过 chunkSize 时切分
                if (current.length() > 0 && current.length() + trimmed.length() > chunkSize) {
                    chunks.add(createChunk(docId, chunkIndex++, current.toString().trim()));
                    // 保留 overlap
                    String overlap = current.length() > chunkOverlap
                            ? current.substring(current.length() - chunkOverlap)
                            : current.toString();
                    current.setLength(0);
                    current.append(overlap);
                }
                current.append("\n").append(trimmed);
            }
        }

        // 最后一个 chunk
        if (current.length() > 0) {
            chunks.add(createChunk(docId, chunkIndex, current.toString().trim()));
        }

        log.info("📄 切片完成: docId={}, chunks={}", docId, chunks.size());
        return chunks;
    }

    private DocumentChunk createChunk(String docId, int index, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(docId + "_" + index);
        chunk.setDocId(docId);
        chunk.setContent(content);
        chunk.setChunkIndex(index);
        return chunk;
    }
}
```

---

### Task 3.3: DTO 类

**Files:**
- Create: `UploadResult.java`, `KnowledgeDoc.java`, `DocumentChunk.java`

- [ ] **Step 1: 创建 UploadResult.java**

```java
package com.cs.agent.vector.file.dto;

import java.time.LocalDateTime;

/** 文件上传结果 */
public class UploadResult {
    private String docId;
    private String fileName;
    private int chunks;
    private String status;
    private String collection;
    private LocalDateTime uploadedAt;

    // getters/setters
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getChunks() { return chunks; }
    public void setChunks(int chunks) { this.chunks = chunks; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
```

- [ ] **Step 2: 创建 KnowledgeDoc.java**

```java
package com.cs.agent.vector.file.dto;

/** 知识库文档摘要 */
public class KnowledgeDoc {
    private String docId;
    private String fileName;
    private String category;
    private int chunkCount;
    private String uploadedAt;

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
}
```

- [ ] **Step 3: 创建 DocumentChunk.java**

```java
package com.cs.agent.vector.file.dto;

import java.util.List;

/** 文档切片 */
public class DocumentChunk {
    private String chunkId;
    private String docId;
    private String content;
    private int chunkIndex;
    private List<Float> embedding;
    private String metadataJson;

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public List<Float> getEmbedding() { return embedding; }
    public void setEmbedding(List<Float> embedding) { this.embedding = embedding; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
```

---

### Task 3.4: FileUploadService — 上传处理总入口

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\vector\file\FileUploadService.java`

- [ ] **Step 1: 创建 FileUploadService**

```java
package com.cs.agent.vector.file;

import com.cs.agent.vector.EmbeddingService;
import com.cs.agent.vector.MilvusClientWrapper;
import com.cs.agent.vector.MilvusClientWrapper.KnowledgeChunk;
import com.cs.agent.vector.file.chunker.DocumentChunker;
import com.cs.agent.vector.file.dto.DocumentChunk;
import com.cs.agent.vector.file.dto.UploadResult;
import com.cs.agent.vector.file.parser.DocumentParser;
import com.cs.agent.vector.file.parser.MarkdownParser;
import com.cs.agent.vector.file.parser.PdfParser;
import com.cs.agent.vector.file.parser.TextParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文件上传服务 —— 上传 → 解析 → 切片 → 向量化 → 写入 Milvus
 */
@Service
public class FileUploadService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileUploadService.class);

    private final PdfParser pdfParser;
    private final TextParser textParser;
    private final MarkdownParser markdownParser;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final MilvusClientWrapper milvusClientWrapper;

    @Value("${knowledge.upload.max-file-size:10485760}")
    private long maxFileSize;

    @Value("${knowledge.upload.storage-path:./knowledge/files}")
    private String storagePath;

    public FileUploadService(PdfParser pdfParser, TextParser textParser, MarkdownParser markdownParser,
                             DocumentChunker chunker, EmbeddingService embeddingService,
                             MilvusClientWrapper milvusClientWrapper) {
        this.pdfParser = pdfParser;
        this.textParser = textParser;
        this.markdownParser = markdownParser;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.milvusClientWrapper = milvusClientWrapper;
    }

    public UploadResult upload(MultipartFile file, String category, String tags) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 1. 校验文件大小
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件大小超过限制 (" + maxFileSize / 1024 / 1024 + "MB)");
        }

        // 2. 获取解析器
        String ext = getExtension(fileName);
        DocumentParser parser = getParser(ext);
        if (parser == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + ext + "（支持: pdf/txt/md）");
        }

        // 3. 解析文本内容
        String text;
        try {
            text = parser.parse(file.getBytes(), fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + fileName, e);
        }

        // 4. 生成 docId
        String docId = "kb_" + UUID.randomUUID().toString().substring(0, 8);

        // 5. 保存原始文件
        saveOriginalFile(file, docId, ext);

        // 6. 切片
        List<DocumentChunk> chunks = chunker.chunk(text, docId);

        // 7. 批量向量化
        List<String> chunkTexts = chunks.stream().map(DocumentChunk::getContent).toList();
        List<List<Float>> embeddings = embeddingService.embedBatch(chunkTexts);

        // 8. 构建 metadata
        String metadataJson = buildMetadataJson(fileName, category, tags, chunks.size());

        // 9. 写入 Milvus
        List<KnowledgeChunk> knowledgeChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            knowledgeChunks.add(new KnowledgeChunk(
                    chunk.getChunkId(),
                    chunk.getContent(),
                    embeddings.get(i),
                    metadataJson
            ));
        }
        milvusClientWrapper.insertChunks(knowledgeChunks);

        // 10. 返回结果
        UploadResult result = new UploadResult();
        result.setDocId(docId);
        result.setFileName(fileName);
        result.setChunks(chunks.size());
        result.setStatus("indexed");
        result.setCollection("cs_knowledge");
        result.setUploadedAt(LocalDateTime.now());

        log.info("📤 文件上传完成: {} → docId={}, chunks={}", fileName, docId, chunks.size());
        return result;
    }

    private DocumentParser getParser(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf" -> pdfParser;
            case "txt" -> textParser;
            case "md", "markdown" -> markdownParser;
            default -> null;
        };
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1);
    }

    private void saveOriginalFile(MultipartFile file, String docId, String ext) {
        try {
            Path dir = Paths.get(storagePath, ext);
            Files.createDirectories(dir);
            Path target = dir.resolve(docId + "." + ext);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("原始文件保存失败: {}", e.getMessage());
        }
    }

    private String buildMetadataJson(String fileName, String category, String tags, int totalChunks) {
        String safeTags = tags != null && !tags.isBlank() ? tags : "";
        String safeCategory = category != null && !category.isBlank() ? category : "other";
        return String.format(
                "{\"sourceFile\":\"%s\",\"fileName\":\"%s\",\"category\":\"%s\",\"tags\":\"%s\",\"totalChunks\":%d,\"uploadedAt\":\"%s\"}",
                fileName, fileName, safeCategory, safeTags, totalChunks, LocalDateTime.now().toString()
        );
    }
}
```

---

### Task 3.5: KnowledgeController — 知识库管理 API

**Files:**
- Create: `C:\multi-agent-cs\src\main\java\com\cs\agent\controller\KnowledgeController.java`

- [ ] **Step 1: 创建 KnowledgeController.java**

```java
package com.cs.agent.controller;

import com.cs.agent.vector.file.FileUploadService;
import com.cs.agent.vector.file.dto.UploadResult;
import com.cs.agent.vector.store.KnowledgeBaseStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理 API
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeController.class);

    private final FileUploadService fileUploadService;
    private final KnowledgeBaseStore knowledgeBaseStore;

    public KnowledgeController(FileUploadService fileUploadService, KnowledgeBaseStore knowledgeBaseStore) {
        this.fileUploadService = fileUploadService;
        this.knowledgeBaseStore = knowledgeBaseStore;
    }

    @PostMapping("/upload")
    public UploadResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "other") String category,
            @RequestParam(value = "tags", defaultValue = "") String tags) {
        log.info("📤 上传文件: name={}, size={}, category={}", file.getOriginalFilename(), file.getSize(), category);
        return fileUploadService.upload(file, category, tags);
    }

    @GetMapping("/documents")
    public List<String> listDocuments() {
        return knowledgeBaseStore.listDocuments();
    }

    @DeleteMapping("/documents/{docId}")
    public Map<String, Object> deleteDocument(@PathVariable String docId) {
        knowledgeBaseStore.deleteDocument(docId);
        return Map.of("docId", docId, "status", "deleted");
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.6") double minScore) {
        var spec = com.cs.agent.vector.advisor.RetrievalSpec.builder()
                .topK(topK).minScore(minScore).build();
        var ctx = knowledgeBaseStore.searchKnowledge(q, spec);
        return Map.of(
                "query", q,
                "results", ctx.getDocuments().stream().map(d -> Map.of(
                        "chunkId", d.getChunkId(),
                        "content", d.getContent().length() > 200 ? d.getContent().substring(0, 200) : d.getContent(),
                        "score", d.getScore(),
                        "metadata", d.getMetadataJson()
                )).toList()
        );
    }

    @GetMapping("/collections")
    public Map<String, Object> getStats() {
        return knowledgeBaseStore.getStats();
    }
}
```

---

## Phase 4: 前端溯源展示

### Task 4.1: 修改 ChatController — SSE 推送 citations 事件

**Files:**
- Modify: `C:\multi-agent-cs\src\main\java\com\cs\agent\controller\ChatController.java`

- [ ] **Step 1: 在 SSE 流中加入 citations 事件推送**

修改 SSE 的 send 方法，在处理完消息后先发 citations 再发 message：

```java
// 在 send() 方法中，获取回复后
String reply = orchestrator.processMessage(sessionId, request.getMessage());
List<Map<String, Object>> citations = orchestrator.getLastCitations(sessionId);  // 新增方法

// 先推 citations 事件
if (citations != null && !citations.isEmpty()) {
    emitter.send(SseEmitter.event()
            .name("citations")
            .data(new ObjectMapper().writeValueAsString(citations)));
}

// 再推 message（原有逻辑）
// ...
```

由于 ChatOrchestrator 需要新增 getLastCitations 方法，或者在 processMessage 中返回包含了 citations 的 map。

更好的做法：修改 ChatOrchestrator 的 processMessage 返回 Map（含 reply + citations），或加一个 `lastCitations` 字段。

简化方案：在 ChatOrchestrator 中添加一个字段存储最后一次的 citations。

---

### Task 4.2: 修改前端 SSE 处理 + 引用展示

**Files:**
- Modify: `C:\multi-agent-cs\web\src\store\chat.js`
- Modify: `C:\multi-agent-cs\web\src\views\chat\index.vue`

- [ ] **Step 1: 在 chat store 中添加 citations 存储和 SSE 事件处理**

在 `state` 中添加：
```javascript
citations: [],      // 当前消息的引用列表
```

在 SSE 事件处理中增加 `citations` 事件：

```javascript
// 在 sendStream 的 while 循环中
} else if (lastEvent === 'citations') {
  try {
    this.citations = JSON.parse(data)
  } catch (e) {
    console.warn('citations parse error:', e)
  }
}
```

- [ ] **Step 2: 在 index.vue 的消息气泡下方添加引用标签**

在 message-bubble 内部，消息内容下方添加：

```html
<div v-if="msg.citations && msg.citations.length" class="citation-tags">
  <el-popover
    v-for="(cite, ci) in msg.citations"
    :key="ci"
    placement="top"
    :width="300"
    trigger="hover"
    :content="cite.snippet"
  >
    <template #reference>
      <el-tag size="small" :type="getCitationType(cite)" class="citation-tag">
        [{{ ci + 1 }}] {{ cite.metadata?.fileName || cite.metadata?.sourceFile || '来源' }}
        <span class="citation-score">{{ (cite.score * 100).toFixed(0) }}%</span>
      </el-tag>
    </template>
  </el-popover>
</div>
```

添加方法：
```javascript
function getCitationType(cite) {
  const cat = cite.metadata?.category
  if (cat === 'product') return 'success'
  if (cat === 'policy') return 'warning'
  if (cat === 'faq') return 'info'
  return ''
}
```

样式：
```css
.citation-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.citation-tag {
  cursor: pointer;
}
.citation-score {
  font-size: 11px;
  opacity: 0.7;
  margin-left: 2px;
}
```

---

## 验证方案

### Phase 1 验证
```bash
# 1. 启动 Milvus
docker compose up -d
# 2. 编译
mvn compile -q
# 3. 启动后端，观察日志
mvn spring-boot:run
# 期望日志:
# ✅ Milvus 连接成功: localhost:19530
# ✅ 创建集合 [cs_knowledge] (dim=1536)
# ✅ Milvus 集合初始化完成
# 📚 知识库为空，写入种子数据...
# ✅ 种子数据初始化完成，共写入 13 条
```

### Phase 2 验证
```bash
# 测试知识检索
curl -s -X POST http://localhost:8080/api/chat/send-sync \
  -H "Content-Type: application/json" \
  -d '{"message":"推荐耳机"}' | python3 -m json.tool
# 期望: 回复基于知识库中的商品数据，不再硬编码
```

### Phase 3 验证
```bash
# 上传文件
curl -s -X POST http://localhost:8080/api/knowledge/upload \
  -F "file=@test.pdf" \
  -F "category=product" \
  -F "tags=测试" | python3 -m json.tool
# 期望: {"docId":"kb_xxx","chunks":N,"status":"indexed"}

# 查询知识库
curl -s http://localhost:8080/api/knowledge/search?q=测试内容 | python3 -m json.tool
```
