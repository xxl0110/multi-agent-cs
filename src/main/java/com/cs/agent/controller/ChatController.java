package com.cs.agent.controller;

import com.cs.agent.orchestrator.ChatOrchestrator;
import com.cs.agent.service.ApprovalStore;
import com.cs.agent.service.ApprovalStore.ApprovalRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 聊天接口控制器
 *
 * 提供聊天接口 + 会话管理 + 审批管理。
 */

@RestController
@RequestMapping("/api/chat")
public class ChatController {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);

    private final ChatOrchestrator orchestrator;
    private final ApprovalStore approvalStore;

    public ChatController(ChatOrchestrator orchestrator, ApprovalStore approvalStore) {
        this.orchestrator = orchestrator;
        this.approvalStore = approvalStore;
    }

    /**
     * 发送消息 — SSE 流式返回
     *
     * 回复会分块逐字推送，前端可展示打字机效果。
     * 生产环境应用 StreamingChatLanguageModel 直接流式输出。
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                // 先发 sessionId
                emitter.send(SseEmitter.event()
                        .name("sessionId")
                        .data(sessionId));

                // 处理消息拿到完整回复
                String reply = orchestrator.processMessage(sessionId, request.getMessage());

                // ★ 推送溯源引用
                List<Map<String, Object>> citations = orchestrator.getLastCitations(sessionId);
                if (citations != null && !citations.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("citations")
                            .data(new ObjectMapper().writeValueAsString(citations)));
                }

                if (reply == null || reply.isEmpty()) {
                    emitter.send(SseEmitter.event().name("message").data(""));
                    emitter.complete();
                    return;
                }

                // 分块推送模拟流式效果（自然停顿 + 换行分割）
                // 生产环境应使用 StreamingChatLanguageModel 逐 token 推送
                String[] parts = reply.split("(?<=[。！？\n！？])|(?<=\\n)");

                StringBuilder sent = new StringBuilder();
                for (String part : parts) {
                    if (part.trim().isEmpty()) continue;
                    sent.append(part);
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(sent.toString()));
                    // 模拟 LLM 生成延迟（每块 30-80ms）
                    Thread.sleep(30 + (long)(Math.random() * 50));
                }

                // 如有遗漏的剩余字符
                if (!reply.equals(sent.toString()) && reply.length() > sent.length()) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(reply));
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 发送失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("抱歉，系统暂时无法处理您的请求。"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/send-sync")
    public Map<String, Object> sendSync(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        String reply = orchestrator.processMessage(sessionId, request.getMessage());

        return Map.of(
                "sessionId", sessionId,
                "reply", reply
        );
    }

    @PostMapping("/session")
    public Map<String, String> createSession() {
        String sessionId = orchestrator.createSession();
        return Map.of("sessionId", sessionId);
    }

    @GetMapping("/session/{sessionId}/history")
    public Map<String, Object> getHistory(@PathVariable String sessionId) {
        List<Map<String, String>> history = orchestrator.getHistory(sessionId);
        return Map.of(
                "sessionId", sessionId,
                "messages", history
        );
    }

    // ==================== 审批接口 ====================

    @GetMapping("/approve/pending")
    public List<Map<String, Object>> getPendingApprovals() {
        return approvalStore.getPendingList().stream().map(req -> Map.<String, Object>of(
                "sessionId", req.getSessionId(),
                "amount", req.getAmount(),
                "reason", req.getReason(),
                "orderId", req.getOrderId(),
                "createdAt", req.getCreatedAt().toString()
        )).collect(Collectors.toList());
    }

    @PostMapping("/approve/{sessionId}")
    public Map<String, Object> approve(@PathVariable String sessionId) {
        String result = approvalStore.approve(sessionId, "admin");
        return Map.of(
                "sessionId", sessionId,
                "result", result,
                "message", switch (result) {
                    case "APPROVED" -> "✅ 退款审批通过";
                    case "NOT_FOUND" -> "未找到该审批请求";
                    case "ALREADY_APPROVED" -> "已被审批通过";
                    case "ALREADY_REJECTED" -> "已被拒绝";
                    default -> "未知状态";
                }
        );
    }

    @PostMapping("/approve/{sessionId}/reject")
    public Map<String, Object> reject(@PathVariable String sessionId) {
        String result = approvalStore.reject(sessionId, "admin");
        return Map.of(
                "sessionId", sessionId,
                "result", result,
                "message", switch (result) {
                    case "REJECTED" -> "❌ 退款审批已拒绝";
                    case "NOT_FOUND" -> "未找到该审批请求";
                    case "ALREADY_APPROVED" -> "已被审批通过，无法拒绝";
                    case "ALREADY_REJECTED" -> "已被拒绝";
                    default -> "未知状态";
                }
        );
    }

    @GetMapping("/approve/{sessionId}/status")
    public Map<String, Object> getApprovalStatus(@PathVariable String sessionId) {
        ApprovalRequest req = approvalStore.getStatus(sessionId);
        if (req == null) return Map.of("exists", false);
        return Map.of(
                "exists", true,
                "sessionId", req.getSessionId(),
                "amount", req.getAmount(),
                "status", req.getStatus(),
                "createdAt", req.getCreatedAt().toString()
        );
    }

    public static class ChatRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
