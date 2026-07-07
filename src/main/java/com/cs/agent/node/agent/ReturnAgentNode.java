package com.cs.agent.node.agent;

import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.ReturnTools;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;

/**
 * 退换货 Worker 节点
 *
 * 处理退换货申请、退款计算、退换货进度查询。
 */

@Component("returnAgentNode")
public class ReturnAgentNode implements NodeAction<CsAgentState> {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReturnAgentNode.class);

    private final ChatLanguageModel chatModel;
    private final ReturnTools returnTools;

    private static final String SYSTEM_PROMPT = """
            你是电商客服系统的退换货专员。

            你的职责：
            - 查询退换货资格和规则
            - 计算退款金额
            - 查询退换货进度

            规则：
            - 用户需要提供订单号才能查询
            - 回答简洁友好，用中文
            """;

    public ReturnAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                           ReturnTools returnTools) {
        this.chatModel = chatModel;
        this.returnTools = returnTools;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("🔄 [ReturnWorker] 处理: {}", userMessage);

        List<ToolSpecification> tools = List.of(
                ToolSpecification.builder()
                        .name("checkReturnEligibility")
                        .description("查询订单的退换货资格和规则")
                        .addParameter("orderId", STRING)
                        .build(),
                ToolSpecification.builder()
                        .name("calculateRefund")
                        .description("计算退款金额")
                        .addParameter("orderId", STRING)
                        .build()
        );

        var response = chatModel.generate(
                List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userMessage)),
                tools
        );

        String reply;
        if (response.content().hasToolExecutionRequests()) {
            var requests = response.content().toolExecutionRequests();
            List<ChatMessage> msgs = new ArrayList<>();
            msgs.add(SystemMessage.from(SYSTEM_PROMPT));
            msgs.add(UserMessage.from(userMessage));
            msgs.add(response.content());

            for (var req : requests) {
                String result = switch (req.name()) {
                    case "checkReturnEligibility" ->
                            returnTools.checkReturnEligibility(extractArg(req.arguments(), "orderId"));
                    case "calculateRefund" ->
                            returnTools.calculateRefund(extractArg(req.arguments(), "orderId"), "");
                    default -> "未知工具";
                };
                msgs.add(ToolExecutionResultMessage.from(req, result));
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
                        "workerName", "return_agent",
                        "result", reply,
                        "success", true
                )
        );
    }

    private String extractArg(String jsonArgs, String key) {
        if (jsonArgs == null) return "";
        String search = "\"" + key + "\":\"";
        int start = jsonArgs.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = jsonArgs.indexOf("\"", start);
        return end < 0 ? jsonArgs.substring(start) : jsonArgs.substring(start, end);
    }
}
