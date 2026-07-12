package com.cs.agent.vector.advisor.metadata;

import java.time.LocalDateTime;

public class ProductMeta implements DocMetadata {
    private String category = "product";
    private String tags;
    private double price;
    private String brand;
    private String status = "上架";
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Override public String getCategory() { return category; }
    @Override public String getTags() { return tags; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    public double getPrice() { return price; }
    public String getBrand() { return brand; }
    public String getStatus() { return status; }
}
