package com.cs.agent.node.agent;

import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.ComplaintTools;
import com.cs.agent.service.PromptService;
import com.cs.agent.vector.advisor.AdvisedContext;
import com.cs.agent.vector.advisor.CitedReply;
import com.cs.agent.vector.advisor.KnowledgeAdvisor;
import com.cs.agent.vector.advisor.metadata.CollectionSchema;
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
    private final KnowledgeAdvisor knowledgeAdvisor;
    private final PromptService promptService;

    public ComplaintAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                              ComplaintTools complaintTools,
                              KnowledgeAdvisor knowledgeAdvisor,
                              PromptService promptService) {
        this.chatModel = chatModel;
        this.complaintTools = complaintTools;
        this.knowledgeAdvisor = knowledgeAdvisor;
        this.promptService = promptService;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("📢 [ComplaintWorker] 处理: {}", userMessage);

        // ★ RAG: 检索 FAQ + 投诉处理指南
        AdvisedContext ctx = knowledgeAdvisor.retrieveBySchema(userMessage, CollectionSchema.FAQ, "complaint_agent");
        String knowledgeContext = ctx.isEmpty() ? "" : "\n\n【参考知识】\n" + ctx.getCombinedContext();
        String enhancedPrompt = promptService.getPrompt("complaint_agent") + knowledgeContext;

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
                List.of(SystemMessage.from(enhancedPrompt), UserMessage.from(userMessage)),
                tools
        );

        String reply;
        if (response.content().hasToolExecutionRequests()) {
            var requests = response.content().toolExecutionRequests();
            List<ChatMessage> msgs = new ArrayList<>();
            msgs.add(SystemMessage.from(enhancedPrompt));
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

        // ★ 溯源绑定
        CitedReply citedReply = knowledgeAdvisor.cite(reply, ctx.getDocuments());
        knowledgeAdvisor.trackConsumption(state.sessionId(), "complaint_agent", citedReply);

        log.info("  → 回复: {}", citedReply.getReply());

        return Map.of(
                "messages", Map.of("role", "assistant", "content", citedReply.getReply()),
                "finalReply", citedReply.getReply(),
                "citations", citedReply.getCitations().stream().map(c -> Map.of(
                        "docId", c.getDocId(),
                        "score", c.getScore(),
                        "snippet", c.getSnippet(),
                        "metadata", c.getMetadata()
                )).toList(),
                "workerResults", Map.of(
                        "workerName", "complaint_agent",
                        "result", citedReply.getReply(),
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
