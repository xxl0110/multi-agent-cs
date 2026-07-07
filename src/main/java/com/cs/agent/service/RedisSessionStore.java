package com.cs.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 会话存储 —— 支持多实例、重启不丢数据
 *
 * 启用方式：在 application.yml 中添加 cs.redis-session: true
 * 数据格式：
 *   key: cs:session:{sessionId}
 *   value: JSON [{"role":"user","content":"..."}, ...]
 *   ttl: 24 小时
 */

@Component
@ConditionalOnProperty(name = "cs.redis-session", havingValue = "true")
public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "cs:session:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void initSession(String sessionId) {
        String key = key(sessionId);
        redis.opsForValue().set(key, "[]", TTL);
    }

    @Override
    public void addMessage(String sessionId, String role, String content) {
        String key = key(sessionId);
        String json = redis.opsForValue().get(key);
        try {
            List<Map<String, String>> messages;
            if (json == null) {
                messages = new ArrayList<>();
            } else {
                messages = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            }
            messages.add(Map.of("role", role, "content", content));
            redis.opsForValue().set(key, objectMapper.writeValueAsString(messages), TTL);
        } catch (Exception e) {
            throw new RuntimeException("Redis 序列化失败", e);
        }
    }

    @Override
    public List<Map<String, String>> getMessages(String sessionId) {
        String key = key(sessionId);
        String json = redis.opsForValue().get(key);
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        redis.delete(key(sessionId));
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
    }

    private static String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
