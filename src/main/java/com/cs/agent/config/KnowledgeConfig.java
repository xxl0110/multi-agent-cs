package com.cs.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识检索配置 —— 每个 Worker 的 topK 限制
 */
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeConfig {

    /** 全局最大 topK */
    private int maxTopK = 10;

    /** 各 Worker 默认 topK (未配置的 Worker 使用全局默认) */
    private int defaultTopK = 3;

    /** 按 Worker 定制的 topK */
    private Map<String, Integer> workerTopK = Map.of(
            "product_agent", 5,
            "return_agent", 4,
            "complaint_agent", 3
    );

    public int getMaxTopK() { return maxTopK; }
    public void setMaxTopK(int maxTopK) { this.maxTopK = maxTopK; }

    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int defaultTopK) { this.defaultTopK = defaultTopK; }

    public Map<String, Integer> getWorkerTopK() { return workerTopK; }
    public void setWorkerTopK(Map<String, Integer> workerTopK) { this.workerTopK = workerTopK; }

    /** 获取某个 Worker 的 topK，受全局上限约束 */
    public int getTopKForWorker(String workerName) {
        int topK = workerTopK.getOrDefault(workerName, defaultTopK);
        return Math.min(topK, maxTopK);
    }
}
