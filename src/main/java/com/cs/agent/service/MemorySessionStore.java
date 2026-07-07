package com.cs.agent.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储 —— 适用单机开发/测试
 *
 * 重启后数据丢失，生产环境请用 RedisSessionStore。
 */

@Component
@ConditionalOnMissingBean(RedisSessionStore.class)
public class MemorySessionStore implements SessionStore {

    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

    @Override
    public void initSession(String sessionId) {
        sessions.put(sessionId, new ArrayList<>());
    }

    @Override
    public void addMessage(String sessionId, String role, String content) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(Map.of("role", role, "content", content));
    }

    @Override
    public List<Map<String, String>> getMessages(String sessionId) {
        return sessions.getOrDefault(sessionId, List.of());
    }

    @Override
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
