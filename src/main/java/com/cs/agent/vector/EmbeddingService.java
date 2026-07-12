package com.cs.agent.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 向量化服务 —— 调用 DeepSeek Embedding API
 *
 * 兼容 OpenAI Embedding API 格式。
 * API 调用失败时使用零向量 fallback，保证系统不中断。
 */
@Service
public class EmbeddingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmbeddingService.class);

    private final OkHttpClient client;

    @Value("${ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.embedding.model:text-embedding-v2}")
    private String model;

    @Value("${ai.embedding.batch-size:16}")
    private int batchSize;

    private static final int DIM = 1536;
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    public EmbeddingService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("🧠 EmbeddingService 初始化，model={}, baseUrl={}", model, baseUrl);
    }

    /** 将单条文本转为向量 */
    public List<Float> embed(String text) {
        List<List<Float>> results = embedBatch(List.of(text));
        return results.isEmpty() ? zeroVector() : results.get(0);
    }

    /** 批量向量化 */
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> allResults = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            allResults.addAll(callEmbeddingAPI(texts.subList(i, end)));
        }
        return allResults;
    }

    private List<List<Float>> callEmbeddingAPI(List<String> texts) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);
            JsonArray input = new JsonArray();
            for (String t : texts) input.add(t);
            payload.add("input", input);

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Embedding API 错误: {} {}", response.code(),
                            response.body() != null ? response.body().string() : "");
                    return fallbackEmbeddings(texts.size());
                }
                String respBody = response.body() != null ? response.body().string() : "{}";
                return parseEmbeddingResponse(respBody);
            }
        } catch (IOException e) {
            log.error("Embedding API 调用失败", e);
            return fallbackEmbeddings(texts.size());
        }
    }

    private List<List<Float>> parseEmbeddingResponse(String json) {
        List<List<Float>> results = new ArrayList<>();
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");
            if (data == null) return fallbackEmbeddings(1);

            for (int i = 0; i < data.size(); i++) {
                JsonArray embedding = data.get(i).getAsJsonObject().getAsJsonArray("embedding");
                List<Float> vector = new ArrayList<>(DIM);
                for (int j = 0; j < embedding.size() && j < DIM; j++) {
                    vector.add(embedding.get(j).getAsFloat());
                }
                // 补齐维度
                while (vector.size() < DIM) vector.add(0.0f);
                results.add(vector);
            }
        } catch (Exception e) {
            log.error("解析 Embedding 响应失败", e);
            return fallbackEmbeddings(1);
        }
        return results;
    }

    private List<List<Float>> fallbackEmbeddings(int count) {
        log.warn("⚠️ 使用零向量 fallback (count={})", count);
        List<List<Float>> fallback = new ArrayList<>(count);
        for (int i = 0; i < count; i++) fallback.add(zeroVector());
        return fallback;
    }

    private List<Float> zeroVector() {
        List<Float> vec = new ArrayList<>(DIM);
        for (int i = 0; i < DIM; i++) vec.add(0.0f);
        return vec;
    }
}
