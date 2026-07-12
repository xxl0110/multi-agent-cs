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
