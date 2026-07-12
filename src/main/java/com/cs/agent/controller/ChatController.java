package com.cs.agent.controller;

import com.cs.agent.orchestrator.ChatOrchestrator;
import com.cs.agent.service.ApprovalStore;
import com.cs.agent.service.ApprovalStore.ApprovalRequest;
import com.cs.agent.state.CsAgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);

    private final ChatOrchestrator orchestrator;
    private final ApprovalStore approvalStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(ChatOrchestrator orchestrator, ApprovalStore approvalStore) {
        this.orchestrator = orchestrator;
        this.approvalStore = approvalStore;
    }

    /** ★ 流式聊天 — Graph 级流式，每个 Node 输出实时推送 */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                // 先发 sessionId
                emitter.send(SseEmitter.event().name("sessionId").data(sessionId));

                // 流式执行 Graph
                var generator = orchestrator.processStreaming(sessionId, request.getMessage());
                var it = generator.iterator();

                while (it.hasNext()) {
                    var nodeOutput = it.next();
                    String nodeName = nodeOutput.node();
                    CsAgentState state = (CsAgentState) nodeOutput.state();

                    if (nodeOutput.isEND()) break;

                    // 推送 Node 执行事件
                    emitter.send(SseEmitter.event()
                            .name("node")
                            .data(nodeName));

                    // 如果这个 Node 产生了 finalReply，推送 message 事件
                    String reply = state.finalReply();
                    if (!reply.isEmpty()) {
                        // 推送引用
                        List<Map<String, Object>> citations = extractCitations(state);
                        if (!citations.isEmpty()) {
                            emitter.send(SseEmitter.event()
                                    .name("citations")
                                    .data(objectMapper.writeValueAsString(citations)));
                        }
                        emitter.send(SseEmitter.event().name("message").data(reply));
                    }
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 流式发送失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("抱歉，系统暂时无法处理您的请求。"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** ★ 同步聊天（保持兼容） */
    @PostMapping("/send-sync")
    public Map<String, Object> sendSync(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        String reply = orchestrator.processMessage(sessionId, request.getMessage());
        List<Map<String, Object>> citations = orchestrator.getLastCitations(sessionId);

        if (citations == null) citations = List.of();
        return Map.of("sessionId", sessionId, "reply", reply, "citations", citations);
    }

    @PostMapping("/session")
    public Map<String, String> createSession() {
        String sessionId = orchestrator.createSession();
        return Map.of("sessionId", sessionId);
    }

    @GetMapping("/session/{sessionId}/history")
    public Map<String, Object> getHistory(@PathVariable String sessionId) {
        List<Map<String, String>> history = orchestrator.getHistory(sessionId);
        return Map.of("sessionId", sessionId, "messages", history);
    }

    // ===== 审批接口 =====

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
        return Map.of("sessionId", sessionId, "result", result,
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
        return Map.of("sessionId", sessionId, "result", result,
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
        return Map.of("exists", true, "sessionId", req.getSessionId(),
                "amount", req.getAmount(), "status", req.getStatus(),
                "createdAt", req.getCreatedAt().toString());
    }

    // ===== 内部方法 =====

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractCitations(CsAgentState state) {
        Object val = state.value("citations").orElse(null);
        if (val instanceof List) return (List<Map<String, Object>>) val;
        return List.of();
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
