package com.cs.agent.vector;

import com.alibaba.fastjson.JSONObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.*;

/**
 * Milvus 统一封装
 */
public class MilvusClientWrapper {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MilvusClientWrapper.class);

    private final MilvusClientV2 client;
    private final String collectionName;

    public MilvusClientWrapper(MilvusClientV2 client, String collectionName, int dimension) {
        this.client = client;
        this.collectionName = collectionName;
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

    /** 插入文档切片 */
    public void insertChunks(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) return;

        JSONObject row = new JSONObject();
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

        row.put("chunk_id", chunkIds);
        row.put("content", contents);
        row.put("embedding", embeddings);
        row.put("metadata", metadataList);

        client.insert(InsertReq.builder()
                .collectionName(collectionName)
                .data(List.of(row))
                .build());
        log.info("📥 插入 {} 条文档切片到 [{}]", chunks.size(), collectionName);
    }

    /** 向量相似度搜索 */
    public SearchResult searchSimilar(List<Float> queryVector, int topK, String filterExpr) {
        var builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField("embedding")
                .data(List.of(queryVector))
                .topK(topK)
                .outputFields(List.of("chunk_id", "content", "metadata"))
                .searchParams(Map.of("nprobe", 16));

        if (filterExpr != null && !filterExpr.isBlank()) {
            builder.filter(filterExpr);
        }

        SearchResp resp = client.search(builder.build());
        if (resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) {
            return new SearchResult(List.of());
        }

        List<ScoredDoc> docs = new ArrayList<>();
        for (var result : resp.getSearchResults().get(0)) {
            Map<String, Object> entity = result.getEntity();
            float distance = result.getDistance() != null ? result.getDistance() : 0f;
            docs.add(new ScoredDoc(
                    str(entity.get("chunk_id")),
                    str(entity.get("content")),
                    str(entity.get("metadata")),
                    distance
            ));
        }
        return new SearchResult(docs);
    }

    /** 按 docId 前缀删除文档 */
    public void deleteDocument(String docIdPrefix) {
        client.delete(DeleteReq.builder()
                .collectionName(collectionName)
                .filter("chunk_id like \"" + docIdPrefix + "%\"")
                .build());
        log.info("🗑️ 删除文档: {}*", docIdPrefix);
    }

    /** 列出文档 */
    public List<String> listDocuments() {
        QueryResp resp = client.query(QueryReq.builder()
                .collectionName(collectionName)
                .outputFields(List.of("chunk_id"))
                .limit(10000)
                .build());
        if (resp.getQueryResults() == null) return List.of();

        Set<String> docs = new LinkedHashSet<>();
        for (var row : resp.getQueryResults()) {
            String chunkId = str(row.getEntity().get("chunk_id"));
            if (chunkId != null && chunkId.contains("_")) {
                docs.add(chunkId.substring(0, chunkId.lastIndexOf('_')));
            }
        }
        return new ArrayList<>(docs);
    }

    /** 集合统计 */
    public Map<String, Object> getStats() {
        QueryResp resp = client.query(QueryReq.builder()
                .collectionName(collectionName)
                .outputFields(List.of("count(*)"))
                .limit(1)
                .build());
        long count = 0;
        if (resp.getQueryResults() != null && !resp.getQueryResults().isEmpty()) {
            Object val = resp.getQueryResults().get(0).getEntity().get("count(*)");
            count = val instanceof Number ? ((Number) val).longValue() : 0;
        }
        return Map.of("collection", collectionName, "totalChunks", count);
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

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
