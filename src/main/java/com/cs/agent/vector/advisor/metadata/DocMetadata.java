package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

public interface DocMetadata {
    String getCategory();
    String getTags();
    LocalDateTime getUpdatedAt();
}
