package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

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
