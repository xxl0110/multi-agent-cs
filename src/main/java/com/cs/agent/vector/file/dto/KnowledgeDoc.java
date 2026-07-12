package com.cs.agent.vector.file.dto;

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
