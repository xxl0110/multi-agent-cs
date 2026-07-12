package com.cs.agent.orchestrator;

import com.cs.agent.graph.WorkflowBuilder;
import com.cs.agent.interceptor.ChatInterceptor;
import com.cs.agent.service.ApprovalStore;
import com.cs.agent.service.SessionStore;
import com.cs.agent.state.CsAgentState;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatOrchestrator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatOrchestrator.class);

    private final WorkflowBuilder workflowBuilder;
    private final ApprovalStore approvalStore;
    private final SessionStore sessionStore;
    private final ChatInterceptor chatInterceptor;

    private final Map<String, CsAgentState> sessionStates = new ConcurrentHashMap<>();

    public ChatOrchestrator(WorkflowBuilder workflowBuilder,
                            ApprovalStore approvalStore,
                            SessionStore sessionStore,
                            ChatInterceptor chatInterceptor) {
        this.workflowBuilder = workflowBuilder;
        this.approvalStore = approvalStore;
        this.sessionStore = sessionStore;
        this.chatInterceptor = chatInterceptor;
    }

    /** 同步处理（带拦截器） */
    public String processMessage(String sessionId, String userMessage) {
        // ★ 拦截器
        String blocked = chatInterceptor.intercept(sessionId, userMessage);
        if (blocked != null) {
            sessionStore.addMessage(sessionId, "assistant", blocked);
            return blocked;
        }
        return doProcessMessage(sessionId, userMessage);
    }

    /** 流式处理（Graph 级流式 + 拦截器） */
    public AsyncGenerator<NodeOutput<CsAgentState>> processStreaming(String sessionId, String userMessage) {
        // ★ 拦截器 — 同步检查
        String blocked = chatInterceptor.intercept(sessionId, userMessage);
        if (blocked != null) {
            sessionStore.addMessage(sessionId, "assistant", blocked);
            return AsyncGenerator.from(List.<NodeOutput<CsAgentState>>of(
                    NodeOutput.of("interceptor", createSimpleState(sessionId, blocked))
            ).iterator());
        }
        return doProcessStreaming(sessionId, userMessage);
    }

    /** 同步处理核心逻辑 */
    private String doProcessMessage(String sessionId, String userMessage) {
        sessionStore.initSession(sessionId);
        sessionStore.addMessage(sessionId, "user", userMessage);
        log.info("💬 [{}] 用户: {}", sessionId, userMessage);

        // 审批快捷查询
        String approvalReply = checkApprovalQuery(sessionId, userMessage);
        if (approvalReply != null) return approvalReply;

        try {
            Map<String, Object> inputData = buildInput(sessionId);
            var compiled = workflowBuilder.getCompiledGraph();
            Optional<CsAgentState> result = compiled.invoke(inputData);

            if (result.isPresent()) {
                return handleResult(sessionId, result.get());
            }
            return "好的，已为您处理。";
        } catch (Exception e) {
            log.error("❌ [{}] Agent 执行出错", sessionId, e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }

    /** 流式处理核心 */
    private AsyncGenerator<NodeOutput<CsAgentState>> doProcessStreaming(String sessionId, String userMessage) {
        sessionStore.initSession(sessionId);
        sessionStore.addMessage(sessionId, "user", userMessage);
        log.info("💬 [{}] 用户: {} (流式)", sessionId, userMessage);

        String approvalReply = checkApprovalQuery(sessionId, userMessage);
        if (approvalReply != null) {
            return AsyncGenerator.from(List.<NodeOutput<CsAgentState>>of(
                    NodeOutput.of("approval", createSimpleState(sessionId, approvalReply))
            ).iterator());
        }

        Map<String, Object> inputData = buildInput(sessionId);
        var compiled = workflowBuilder.getCompiledGraph();
        AsyncGenerator<NodeOutput<CsAgentState>> stream = compiled.stream(inputData);

        // 包装：在流结束后保存结果
        return AsyncGenerator.from(new Iterator<>() {
            private final Iterator<NodeOutput<CsAgentState>> it = stream.iterator();
            private NodeOutput<CsAgentState> lastOutput;

            @Override
            public boolean hasNext() {
                boolean has = it.hasNext();
                if (!has && lastOutput != null) {
                    handleResult(sessionId, lastOutput.state());
                }
                return has;
            }

            @Override
            public NodeOutput<CsAgentState> next() {
                lastOutput = it.next();
                return lastOutput;
            }
        });
    }

    /** 构建 Graph 输入 */
    private Map<String, Object> buildInput(String sessionId) {
        List<Map<String, String>> messages = sessionStore.getMessages(sessionId);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("sessionId", sessionId);
        inputData.put("messages", new ArrayList<>(messages));
        inputData.put("roundCount", 0);
        return inputData;
    }

    /** 处理 Graph 执行结果 */
    private String handleResult(String sessionId, CsAgentState finalState) {
        String reply = finalState.finalReply();
        if (reply.isEmpty()) reply = finalState.lastAiReply();
        log.info("💬 [{}] 云小护: {}", sessionId, reply);

        if (reply.contains("需要主管审批") || reply.contains("超过500元")) {
            approvalStore.create(sessionId, 800.0, "退款金额超过500元", "ORD20240630");
        }
        sessionStore.addMessage(sessionId, "assistant", reply);
        sessionStates.put(sessionId, finalState);
        return reply;
    }

    /** 审批快捷查询 */
    private String checkApprovalQuery(String sessionId, String userMessage) {
        String lower = userMessage.toLowerCase();
        if (lower.contains("审批") || lower.contains("批准") || lower.contains("approv")
                || (lower.contains("status") && lower.contains("refund"))) {
            var req = approvalStore.getStatus(sessionId);
            if (req != null) {
                return switch (req.getStatus()) {
                    case "APPROVED" -> "✅ 您的退款审批已通过，退款将在1-3个工作日内原路返回。";
                    case "REJECTED" -> "❌ 您的退款审批未通过，请联系客服了解详情。";
                    default -> "⏳ 您的退款申请（¥" + req.getAmount() + "）正在审批中，请耐心等待。";
                };
            }
        }
        return null;
    }

    /** 创建简单状态（用于拦截器/审批快捷回复） */
    private CsAgentState createSimpleState(String sessionId, String reply) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("messages", new ArrayList<>());
        data.put("finalReply", reply);
        data.put("citations", List.of());
        return new CsAgentState(data);
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
