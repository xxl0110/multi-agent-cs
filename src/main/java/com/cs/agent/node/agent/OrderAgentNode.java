package com.cs.agent.node.agent;

import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.OrderTools;
import com.cs.agent.service.PromptService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 订单 Worker 节点
 *
 * 策略：先尝试从用户消息中提取订单号并查询数据库，
 * 再用 LLM 生成自然语言回复。
 * 这样不依赖 LLM 的 Tool Calling 能力。
 */
@Component("orderAgentNode")
public class OrderAgentNode implements NodeAction<CsAgentState> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderAgentNode.class);

    private final ChatLanguageModel chatModel;
    private final OrderTools orderTools;
    private final PromptService promptService;

    /** 订单号格式：ORD 开头 + 数字，或纯数字 */
    private static final Pattern ORDER_ID_PATTERN =
            Pattern.compile("(ORD\\d{5,}|\\d{8,})");

    public OrderAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                          OrderTools orderTools,
                          PromptService promptService) {
        this.chatModel = chatModel;
        this.orderTools = orderTools;
        this.promptService = promptService;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("📦 [OrderWorker] 处理: {}", userMessage);

        // 1️⃣ 从用户消息提取订单号
        String orderId = extractOrderId(userMessage);

        // 2️⃣ 查询订单（如果有订单号）
        String orderData = "";
        if (orderId != null) {
            log.info("  → 提取到订单号: {}", orderId);
            orderData = orderTools.queryOrder(orderId);
            log.info("  → 查询结果: {}", orderData);
        }

        // 3️⃣ 用 LLM 生成自然语言回复
        String systemPrompt = promptService.getPrompt("order_agent");
        String reply;
        if (!orderData.isEmpty()) {
            reply = chatModel.generate(List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from("用户问题：" + userMessage
                            + "\n\n数据库查询到的数据：\n" + orderData
                            + "\n\n请根据以上数据回答用户，简洁友好。")
            )).content().text();
        } else if (userMessage.toLowerCase().contains("物流") || userMessage.contains("物流")) {
            String logisticsData = orderTools.queryLogistics(orderId);
            reply = chatModel.generate(List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from("用户问题：" + userMessage
                            + "\n\n查询到的物流信息：\n" + logisticsData)
            )).content().text();
        } else {
            // 无订单号，让 LLM 自然询问
            reply = chatModel.generate(List.of(
                    SystemMessage.from(systemPrompt
                            + "\n注意：用户还没有提供订单号，请礼貌地询问。"),
                    UserMessage.from(userMessage)
            )).content().text();
        }

        log.info("  → 回复: {}", reply);

        return Map.of(
                "messages", Map.of("role", "assistant", "content", reply),
                "finalReply", reply,
                "workerResults", Map.of(
                        "workerName", "order_agent",
                        "result", reply,
                        "success", true
                )
        );
    }

    /** 从文本中提取订单号 */
    private String extractOrderId(String text) {
        if (text == null) return null;
        Matcher m = ORDER_ID_PATTERN.matcher(text.toUpperCase());
        return m.find() ? m.group(1) : null;
    }
}
