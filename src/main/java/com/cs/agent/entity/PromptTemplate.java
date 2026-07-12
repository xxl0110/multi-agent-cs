package com.cs.agent.entity;

import java.time.LocalDateTime;

public class PromptTemplate {
    private String id;
    private String name;
    private String targetWorker;  // supervisor, order_agent, product_agent, return_agent, complaint_agent
    private String systemPrompt;
    private String description;
    private boolean enabled = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PromptTemplate() {}

    public PromptTemplate(String id, String name, String targetWorker, String systemPrompt, String description) {
        this.id = id;
        this.name = name;
        this.targetWorker = targetWorker;
        this.systemPrompt = systemPrompt;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetWorker() { return targetWorker; }
    public void setTargetWorker(String targetWorker) { this.targetWorker = targetWorker; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; this.updatedAt = LocalDateTime.now(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
