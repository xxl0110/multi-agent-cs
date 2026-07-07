package com.cs.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM 配置类
 *
 * 支持两种模式：
 * 1. mock-mode=true  → 使用 MockChatModel（无需 API Key）
 * 2. mock-mode=false → 使用 OpenAiChatModel（调真实 API）
 */
@Configuration
public class AgentConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentConfig.class);

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model-name:deepseek-chat}")
    private String modelName;

    @Value("${ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.temperature:0.3}")
    private double temperature;

    @Value("${ai.mock-mode:false}")
    private boolean mockMode;

    /**
     * Supervisor 专用模型
     */
    @Bean("supervisorChatModel")
    @Primary
    public ChatLanguageModel supervisorChatModel() {
        if (mockMode) {
            log.info("🧪 Supervisor 使用 Mock 模式");
            return new MockChatModel();
        }
        log.info("🤖 Supervisor 使用真实模型: {} @ {}", modelName, baseUrl);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(Math.min(temperature, 0.3))
                .build();
    }

    /**
     * Worker 专用模型
     */
    @Bean("workerChatModel")
    public ChatLanguageModel workerChatModel() {
        if (mockMode) {
            log.info("🧪 Worker 使用 Mock 模式");
            return new MockChatModel();
        }
        log.info("🤖 Worker 使用真实模型: {} @ {}", modelName, baseUrl);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .build();
    }
}
