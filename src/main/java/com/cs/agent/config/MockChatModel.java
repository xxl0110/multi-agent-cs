package com.cs.agent.config;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * 模拟聊天模型 —— 不依赖真实 API，返回预设回复
 */
public class MockChatModel implements ChatLanguageModel {

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String allText = extractAllText(messages);
        return Response.from(AiMessage.from(decideReply(allText)));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages);
    }

    private String extractAllText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage userMsg) {
                sb.append("用户: ").append(userMsg.singleText()).append("\n");
            } else if (msg instanceof SystemMessage sysMsg) {
                sb.append("系统: ").append(sysMsg.text()).append("\n");
            } else if (msg instanceof AiMessage aiMsg) {
                sb.append("AI: ").append(aiMsg.text()).append("\n");
            }
        }
        return sb.toString();
    }

    private String decideReply(String text) {
        // Supervisor
        if (text.contains("Supervisor") || text.contains("调度员")) {
            return decideRouting(text);
        }
        // Worker
        if (text.contains("订单专员")) {
            return "您的订单 ORD20240630 查询结果：\n" +
                   "- 商品：iPhone 16 Pro 手机壳\n" +
                   "- 金额：39.90 元\n" +
                   "- 状态：已发货\n" +
                   "- 物流：顺丰快递 SF1234567890，预计今天送达";
        }
        if (text.contains("商品专员")) {
            return "为您推荐以下蓝牙耳机：\n" +
                   "1.【漫步者 G2】无线蓝牙耳机 299元  ★ 好评5000+\n" +
                   "2.【小米 Buds 5】主动降噪 499元  ★ 好评3200+\n" +
                   "3.【华为 FreeBuds 6i】入耳式 359元  ★ 好评2800+\n\n" +
                   "如果您用于跑步，推荐漫步者 G2，佩戴稳固防汗。";
        }
        if (text.contains("退换货专员")) {
            return "订单 ORD20240630 退换货核算：\n" +
                   "- 商品：iPhone 16 Pro 手机壳\n" +
                   "- 原价：39.90 元\n" +
                   "- 退款金额：800.00 元（含赔偿金760.10元）\n" +
                   "- ⚠️ 退款金额超过500元，需要主管审批\n\n" +
                   "已为您提交审批申请，请等待主管审核。\n" +
                   "审批编号：APR-800";
        }
        if (text.contains("投诉专员")) {
            return "已记录您的投诉内容，投诉编号 TK202607071234。\n" +
                   "客服团队将在 30 分钟内联系您处理。\n" +
                   "紧急情况请拨打：400-888-8888";
        }
        return "您好！我是智能客服云小护，请问有什么可以帮您？";
    }

    private String decideRouting(String text) {
        String lastUserMsg = "";
        int lastUserIdx = text.lastIndexOf("用户:");
        if (lastUserIdx >= 0) {
            int nextLineIdx = text.indexOf("\n", lastUserIdx);
            lastUserMsg = (nextLineIdx > 0)
                ? text.substring(lastUserIdx + 3, nextLineIdx).trim()
                : text.substring(lastUserIdx + 3).trim();
        }

        if (text.contains("上一步 Worker 执行结果")) {
            return "{\"next\": \"FINISH\"}";
        }

        String lower = lastUserMsg.toLowerCase();
        if (lower.contains("订单") || lower.contains("order")) return "{\"next\": \"order_agent\"}";
        if (lower.contains("推荐") || lower.contains("recommend") || lower.contains("earphone")) return "{\"next\": \"product_agent\"}";
        if (lower.contains("退货") || lower.contains("return") || lower.contains("refund")) return "{\"next\": \"return_agent\"}";
        if (lower.contains("投诉") || lower.contains("complaint")) return "{\"next\": \"complaint_agent\"}";
        return "{\"next\": \"order_agent\"}";
    }
}
