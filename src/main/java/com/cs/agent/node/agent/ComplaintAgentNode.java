package com.cs.agent.node.agent;

import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.ComplaintTools;
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
 * 投诉 Worker 节点
 *
 * 处理投诉和升级人工客服。
 */

@Component("complaintAgentNode")
public class ComplaintAgentNode implements NodeAction<CsAgentState> {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ComplaintAgentNode.class);

    private final ChatLanguageModel chatModel;
    private final ComplaintTools complaintTools;

    private static final String SYSTEM_PROMPT = """
            你是电商客服系统的投诉专员。

            你的职责：
            - 受理用户投诉
            - 记录投诉内容并提交
            - 在必要时升级给人工客服

            规则：
            - 先安抚用户情绪，再记录问题
            - 承诺会尽快处理并反馈
            - 回答有礼貌，用中文
            """;

    public ComplaintAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                              ComplaintTools complaintTools) {
        this.chatModel = chatModel;
        this.complaintTools = complaintTools;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("📢 [ComplaintWorker] 处理: {}", userMessage);

        List<ToolSpecification> tools = List.of(
                ToolSpecification.builder()
                        .name("submitComplaint")
                        .description("提交投诉内容，升级给人工客服处理")
                        .addParameter("content", STRING)
                        .addParameter("contactPhone", STRING)
                        .build(),
                ToolSpecification.builder()
                        .name("escalateToHuman")
                        .description("将问题升级给人工客服")
                        .addParameter("reason", STRING)
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
                    case "submitComplaint" ->
                            complaintTools.submitComplaint(
                                    extractArg(req.arguments(), "content"),
                                    extractArg(req.arguments(), "contactPhone")
                            );
                    case "escalateToHuman" ->
                            complaintTools.escalateToHuman(extractArg(req.arguments(), "reason"));
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
                        "workerName", "complaint_agent",
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
