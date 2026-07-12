package com.cs.agent.vector.file.dto;

import java.time.LocalDateTime;

public class UploadResult {
    private String docId;
    private String fileName;
    private int chunks;
    private String status;
    private String collection;
    private LocalDateTime uploadedAt;

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
