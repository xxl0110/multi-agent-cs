package com.cs.agent.tool;


import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 投诉工具 —— 模拟数据版
 */

@Component
public class ComplaintTools {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ComplaintTools.class);

    /**
     * 提交投诉
     */
    public String submitComplaint(String content, String contactPhone) {
        log.info("📋 提交投诉: content={}, phone={}", content, contactPhone);

        String ticketId = "TK" + System.currentTimeMillis();
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return String.format("""
                ✅ 投诉已成功提交！
                - 投诉编号：%s
                - 提交时间：%s
                - 联系电话：%s

                我们已记录您的投诉内容，客服团队将在 30 分钟内联系您处理。
                请保持电话畅通。如有紧急情况，请拨打人工客服热线：400-888-8888。
                """, ticketId, time,
                contactPhone != null && !contactPhone.isBlank() ? contactPhone : "未提供");
    }

    /**
     * 升级人工客服
     */
    public String escalateToHuman(String reason) {
        log.info("⬆️ 升级人工客服: reason={}", reason);

        return """
                ⬆️ 已为您转接人工客服...

                当前排队人数：2 人
                预计等待时间：3-5 分钟

                转接过程中您的对话记录将同步给人工客服，无需重复描述问题。
                感谢您的耐心等待 🙏
                """;
    }
}
