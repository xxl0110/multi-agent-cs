package com.cs.agent.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 会话存储接口 —— 支持内存/Redis 可切换
 */
public interface SessionStore {

    /** 初始化新会话 */
    void initSession(String sessionId);

    /** 追加消息 */
    void addMessage(String sessionId, String role, String content);

    /** 获取全部消息 */
    List<Map<String, String>> getMessages(String sessionId);

    /** 删除会话 */
    void deleteSession(String sessionId);

    /** 会话是否存在 */
    boolean sessionExists(String sessionId);

    /** 会话 TTL（仅 Redis 实现生效） */
    default Duration getTtl() { return Duration.ofHours(24); }
}
