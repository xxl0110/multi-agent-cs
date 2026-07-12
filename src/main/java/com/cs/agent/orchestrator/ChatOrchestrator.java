package com.cs.agent.orchestrator;

import com.cs.agent.graph.WorkflowBuilder;
import com.cs.agent.service.ApprovalStore;
import com.cs.agent.service.SessionStore;
import com.cs.agent.state.CsAgentState;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天编排服务 —— Agent 系统的总入口
 *
 * 会话消息通过 SessionStore 存储（内存/Redis 可切换）。
 */
@Service
public class ChatOrchestrator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatOrchestrator.class);

    private final WorkflowBuilder workflowBuilder;
    private final ApprovalStore approvalStore;
    private final SessionStore sessionStore;

    private final Map<String, CsAgentState> sessionStates = new ConcurrentHashMap<>();

    public ChatOrchestrator(WorkflowBuilder workflowBuilder,
                            ApprovalStore approvalStore,
                            SessionStore sessionStore) {
        this.workflowBuilder = workflowBuilder;
        this.approvalStore = approvalStore;
        this.sessionStore = sessionStore;
    }

    public String processMessage(String sessionId, String userMessage) {
        if (!sessionStore.sessionExists(sessionId)) {
            sessionStore.initSession(sessionId);
        }

        sessionStore.addMessage(sessionId, "user", userMessage);
        log.info("💬 [{}] 用户: {}", sessionId, userMessage);

        String lowerMsg = userMessage.toLowerCase();
        if (lowerMsg.contains("审批") || lowerMsg.contains("批准") || lowerMsg.contains("approv")
                || (lowerMsg.contains("status") && lowerMsg.contains("refund"))) {
            var req = approvalStore.getStatus(sessionId);
            if (req != null) {
                String reply = switch (req.getStatus()) {
                    case "APPROVED" -> "✅ 您的退款审批已通过，退款将在1-3个工作日内原路返回。";
                    case "REJECTED" -> "❌ 您的退款审批未通过，请联系客服了解详情。";
                    default -> "⏳ 您的退款申请（¥" + req.getAmount() + "）正在审批中，请耐心等待。";
                };
                sessionStore.addMessage(sessionId, "assistant", reply);
                return reply;
            }
        }

        try {
            List<Map<String, String>> messages = sessionStore.getMessages(sessionId);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("sessionId", sessionId);
            inputData.put("messages", new ArrayList<>(messages));
            inputData.put("roundCount", 0);

            var compiled = workflowBuilder.getCompiledGraph();
            Optional<CsAgentState> result = compiled.invoke(inputData);

            if (result.isPresent()) {
                CsAgentState finalState = result.get();
                String reply = finalState.finalReply();
                if (reply.isEmpty()) {
                    reply = finalState.lastAiReply();
                }
                log.info("💬 [{}] 云小护: {}", sessionId, reply);

                if (reply.contains("需要主管审批") || reply.contains("超过500元")) {
                    approvalStore.create(sessionId, 800.0, "退款金额超过500元", "ORD20240630");
                }

                sessionStore.addMessage(sessionId, "assistant", reply);
                sessionStates.put(sessionId, finalState);
                return reply;
            }

            return "好的，已为您处理。";

        } catch (Exception e) {
            log.error("❌ [{}] Agent 执行出错", sessionId, e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }

    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        sessionStore.initSession(sessionId);
        log.info("🆕 新会话: {}", sessionId);
        return sessionId;
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        return sessionStore.getMessages(sessionId);
    }

    /** 获取最新的溯源引用 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getLastCitations(String sessionId) {
        CsAgentState state = sessionStates.get(sessionId);
        if (state == null) return List.of();
        Object val = state.value("citations").orElse(null);
        if (val instanceof List) {
            return (List<Map<String, Object>>) val;
        }
        return List.of();
    }
}
