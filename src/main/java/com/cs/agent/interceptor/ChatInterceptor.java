package com.cs.agent.interceptor;

import com.cs.agent.service.SessionStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 对话拦截器 —— 在进入 StateGraph 前执行鉴权 + 敏感词过滤
 */
@Component
public class ChatInterceptor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatInterceptor.class);

    private final SensitiveWordService sensitiveWordService;
    private final SessionStore sessionStore;

    public ChatInterceptor(SensitiveWordService sensitiveWordService, SessionStore sessionStore) {
        this.sensitiveWordService = sensitiveWordService;
        this.sessionStore = sessionStore;
    }

    /**
     * 拦截检查
     * @return null 表示放行；非空表示拦截，直接返回此回复
     */
    public String intercept(String sessionId, String message) {
        // 1. 鉴权：session 是否存在
        if (sessionId != null && !sessionId.isBlank() && !sessionStore.sessionExists(sessionId)) {
            log.warn("🔒 拦截: sessionId={} 不存在", sessionId);
            return "会话已过期，请刷新页面重新开始对话。";
        }

        // 2. 敏感词过滤
        List<String> hits = sensitiveWordService.check(message);
        if (!hits.isEmpty()) {
            log.warn("🔒 拦截敏感词: sessionId={}, hits={}", sessionId, hits);
            return "您的消息包含敏感内容（" + String.join(", ", hits) + "），请修改后重试。";
        }

        return null; // 放行
    }
}
