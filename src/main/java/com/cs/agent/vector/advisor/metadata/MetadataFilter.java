package com.cs.agent.vector.advisor.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 标量过滤表达式构建器
 */
public class MetadataFilter {
    private final List<String> conditions = new ArrayList<>();

    private MetadataFilter() {}

    public static MetadataFilter create() { return new MetadataFilter(); }

    public MetadataFilter eq(String field, Object value) {
        if (value instanceof String s) {
            conditions.add("metadata['" + field + "'] == '" + s + "'");
        } else {
            conditions.add("metadata['" + field + "'] == " + value);
        }
        return this;
    }

    public MetadataFilter lt(String field, Number value) {
        conditions.add("metadata['" + field + "'] < " + value);
        return this;
    }

    public MetadataFilter gt(String field, Number value) {
        conditions.add("metadata['" + field + "'] > " + value);
        return this;
    }

    public MetadataFilter hasTag(String tag) {
        conditions.add("json_contains(metadata['tags'], '" + tag + "')");
        return this;
    }

    public MetadataFilter category(String category) {
        return eq("category", category);
    }

    public String build() {
        if (conditions.isEmpty()) return "";
        return String.join(" && ", conditions);
    }

    public boolean isEmpty() { return conditions.isEmpty(); }
}
