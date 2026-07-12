package com.cs.agent.vector.store;

import com.cs.agent.vector.EmbeddingService;
import com.cs.agent.vector.MilvusClientWrapper;
import com.cs.agent.vector.MilvusClientWrapper.KnowledgeChunk;
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

    /** 检索知识库 */
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

        log.info("🔍 知识库检索: query={}, 命中={}条", query, docs.size());
        return new AdvisedContext(docs, combined);
    }

    public void insertChunks(List<KnowledgeChunk> chunks) {
        milvusClientWrapper.insertChunks(chunks);
    }

    public void deleteDocument(String docIdPrefix) {
        milvusClientWrapper.deleteDocument(docIdPrefix);
    }

    public List<String> listDocuments() {
        return milvusClientWrapper.listDocuments();
    }

    public Map<String, Object> getStats() {
        return milvusClientWrapper.getStats();
    }
}
