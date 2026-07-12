package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

public class FaqMeta implements DocMetadata {
    private String category = "faq";
    private String tags;
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Override public String getCategory() { return category; }
    @Override public String getTags() { return tags; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
}
