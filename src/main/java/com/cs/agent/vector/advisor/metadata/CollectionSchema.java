package com.cs.agent.vector.advisor.metadata;

public enum CollectionSchema {
    PRODUCT("cs_knowledge", "product", "商品知识 — 名称、分类、价格、品牌"),
    POLICY("cs_knowledge", "policy", "政策知识 — 退换货/退款/物流政策"),
    FAQ("cs_knowledge", "faq", "常见问题 — 订单/退款/投诉指南");

    private final String collectionName;
    private final String category;
    private final String description;

    CollectionSchema(String collectionName, String category, String description) {
        this.collectionName = collectionName;
        this.category = category;
        this.description = description;
    }

    public String getCollectionName() { return collectionName; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }

    /** Milvus 过滤表达式 */
    public String categoryExpr() {
        return "metadata['category'] == '" + category + "'";
    }
}
