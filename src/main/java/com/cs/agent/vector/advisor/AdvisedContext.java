package com.cs.agent.vector.advisor;

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
