package com.cs.agent.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批存储 —— 管理待审批的退款请求
 *
 * 生产环境应替换为数据库持久化。
 */
@Service
public class ApprovalStore {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApprovalStore.class);

    private final Map<String, ApprovalRequest> store = new ConcurrentHashMap<>();

    public static class ApprovalRequest {
        private final String sessionId;
        private final double amount;
        private final String reason;
        private final String orderId;
        private final LocalDateTime createdAt;
        private String status; // PENDING, APPROVED, REJECTED
        private String processedBy;
        private LocalDateTime processedAt;

        public ApprovalRequest(String sessionId, double amount, String reason, String orderId) {
            this.sessionId = sessionId;
            this.amount = amount;
            this.reason = reason;
            this.orderId = orderId;
            this.createdAt = LocalDateTime.now();
            this.status = "PENDING";
        }

        public String getSessionId() { return sessionId; }
        public double getAmount() { return amount; }
        public String getReason() { return reason; }
        public String getOrderId() { return orderId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getProcessedBy() { return processedBy; }
        public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
        public LocalDateTime getProcessedAt() { return processedAt; }
        public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    }

    /** 创建待审批请求 */
    public void create(String sessionId, double amount, String reason, String orderId) {
        store.put(sessionId, new ApprovalRequest(sessionId, amount, reason, orderId));
        log.info("📋 创建审批请求: sessionId={}, amount={}, reason={}", sessionId, amount, reason);
    }

    /** 审批通过 */
    public String approve(String sessionId, String operator) {
        ApprovalRequest req = store.get(sessionId);
        if (req == null) return "NOT_FOUND";
        if (!"PENDING".equals(req.status)) return "ALREADY_" + req.status;
        req.setStatus("APPROVED");
        req.setProcessedBy(operator);
        req.setProcessedAt(LocalDateTime.now());
        log.info("✅ 审批通过: sessionId={}, operator={}", sessionId, operator);
        return "APPROVED";
    }

    /** 审批拒绝 */
    public String reject(String sessionId, String operator) {
        ApprovalRequest req = store.get(sessionId);
        if (req == null) return "NOT_FOUND";
        if (!"PENDING".equals(req.status)) return "ALREADY_" + req.status;
        req.setStatus("REJECTED");
        req.setProcessedBy(operator);
        req.setProcessedAt(LocalDateTime.now());
        log.info("❌ 审批拒绝: sessionId={}, operator={}", sessionId, operator);
        return "REJECTED";
    }

    /** 查询审批状态 */
    public ApprovalRequest getStatus(String sessionId) {
        return store.get(sessionId);
    }

    /** 获取所有待审批请求 */
    public List<ApprovalRequest> getPendingList() {
        List<ApprovalRequest> list = new ArrayList<>();
        for (ApprovalRequest req : store.values()) {
            if ("PENDING".equals(req.status)) list.add(req);
        }
        return list;
    }
}
