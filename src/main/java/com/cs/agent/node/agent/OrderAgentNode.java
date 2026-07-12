package com.cs.agent.node.agent;

import com.cs.agent.service.PromptService;
import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.SqlQueryTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;

/**
 * 订单 Worker 节点 —— Text-to-SQL 模式
 *
 * LLM 通过 Tool Calling 自主判断查询条件，
 * SqlQueryTool 将条件转为 SQL 查数据库。
 * 不再硬编码订单号提取。
 */
@Component("orderAgentNode")
public class OrderAgentNode implements NodeAction<CsAgentState> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderAgentNode.class);

    private final ChatLanguageModel chatModel;
    private final SqlQueryTool sqlQueryTool;
    private final PromptService promptService;

    private static final String SYSTEM_PROMPT = """
            你是电商客服系统的订单专员。

            你可以通过 queryOrders 工具查询订单数据库。
            表结构：
            %s

            查询技巧：
            - 用户说"查订单"但没有订单号 → 查最近订单 (limit=1, 按create_time DESC)
            - 用户提供了订单号 → 按 order_id 精确查询
            - 用户想查某商品的订单 → 用 product_keyword LIKE 模糊查询
            - 用户想查某个状态的订单 → 用 status 过滤 (0=待发货 1=已发货 2=已完成 3=已取消)
            - 用户想查金额范围 → 用 minAmount / maxAmount

            回答简洁友好，用中文。
            """;

    public OrderAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                          SqlQueryTool sqlQueryTool,
                          PromptService promptService) {
        this.chatModel = chatModel;
        this.sqlQueryTool = sqlQueryTool;
        this.promptService = promptService;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("📦 [OrderWorker] 处理: {}", userMessage);

        String prompt = String.format(promptService.getPrompt("order_agent") + "\n\n" + SYSTEM_PROMPT, SqlQueryTool.TABLE_SCHEMA);
        String reply;

        // 使用 Tool Calling 让 LLM 自主决定查询参数
        var tools = buildQueryTool();
        var response = chatModel.generate(
                List.of(SystemMessage.from(prompt), UserMessage.from(userMessage)),
                tools
        );

        if (response.content().hasToolExecutionRequests()) {
            var requests = response.content().toolExecutionRequests();
            List<ChatMessage> msgs = new ArrayList<>();
            msgs.add(SystemMessage.from(prompt));
            msgs.add(UserMessage.from(userMessage));
            msgs.add(response.content());

            for (var req : requests) {
                if (SqlQueryTool.QUERY_TOOL_NAME.equals(req.name())) {
                    String orderId = extractArg(req.arguments(), "orderId");
                    String productKeyword = extractArg(req.arguments(), "productKeyword");
                    String status = extractArg(req.arguments(), "status");
                    String minAmountStr = extractArg(req.arguments(), "minAmount");
                    String maxAmountStr = extractArg(req.arguments(), "maxAmount");
                    String orderBy = extractArg(req.arguments(), "orderBy");
                    String orderDir = extractArg(req.arguments(), "orderDir");
                    String limitStr = extractArg(req.arguments(), "limit");

                    Double minAmount = tryParseDouble(minAmountStr);
                    Double maxAmount = tryParseDouble(maxAmountStr);
                    int limit = tryParseInt(limitStr, 5);

                    String result = sqlQueryTool.queryOrders(
                            nullIfBlank(orderId), nullIfBlank(productKeyword),
                            nullIfBlank(status), minAmount, maxAmount,
                            nullIfBlank(orderBy), nullIfBlank(orderDir), limit);

                    msgs.add(ToolExecutionResultMessage.from(req, result));
                }
            }

            reply = chatModel.generate(msgs).content().text();
        } else {
            reply = response.content().text();
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

    private List<ToolSpecification> buildQueryTool() {
        return List.of(ToolSpecification.builder()
                .name(SqlQueryTool.QUERY_TOOL_NAME)
                .description(SqlQueryTool.QUERY_TOOL_DESC)
                .addParameter("orderId", STRING)
                .addParameter("productKeyword", STRING)
                .addParameter("status", STRING)
                .addParameter("minAmount", NUMBER)
                .addParameter("maxAmount", NUMBER)
                .addParameter("orderBy", STRING)
                .addParameter("orderDir", STRING)
                .addParameter("limit", INTEGER)
                .build());
    }

    private String extractArg(String jsonArgs, String key) {
        if (jsonArgs == null) return "";
        String search = "\"" + key + "\":";
        int start = jsonArgs.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        if (start >= jsonArgs.length()) return "";
        char c = jsonArgs.charAt(start);
        if (c == '"') {
            int end = jsonArgs.indexOf('"', start + 1);
            return end < 0 ? jsonArgs.substring(start + 1) : jsonArgs.substring(start + 1, end);
        }
        int end = jsonArgs.indexOf(',', start);
        if (end < 0) end = jsonArgs.indexOf('}', start);
        return end < 0 ? jsonArgs.substring(start) : jsonArgs.substring(start, end);
    }

    private String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s; }
    private Double tryParseDouble(String s) { try { return s == null || s.isBlank() ? null : Double.parseDouble(s); } catch (NumberFormatException e) { return null; } }
    private int tryParseInt(String s, int def) { try { return s == null || s.isBlank() ? def : Integer.parseInt(s); } catch (NumberFormatException e) { return def; } }
}
