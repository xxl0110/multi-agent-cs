package com.cs.agent.node.agent;

import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.ProductTools;
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
 * 商品 Worker 节点
 *
 * 处理商品搜索、推荐、详情查询等问题。
 */

@Component("productAgentNode")
public class ProductAgentNode implements NodeAction<CsAgentState> {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductAgentNode.class);

    private final ChatLanguageModel chatModel;
    private final ProductTools productTools;

    private static final String SYSTEM_PROMPT = """
            你是电商客服系统的商品专员。

            你的职责：
            - 根据关键词搜索商品
            - 推荐合适的商品给用户
            - 回答商品相关问题

            规则：
            - 主动了解用户需求后再推荐
            - 回答简洁友好，用中文
            """;

    public ProductAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                            ProductTools productTools) {
        this.chatModel = chatModel;
        this.productTools = productTools;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("🛍️  [ProductWorker] 处理: {}", userMessage);

        List<ToolSpecification> tool = List.of(
                ToolSpecification.builder()
                        .name("searchProduct")
                        .description("根据关键词搜索商品")
                        .addParameter("keyword", STRING)
                        .build()
        );

        var response = chatModel.generate(
                List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userMessage)),
                tool
        );

        String reply;
        if (response.content().hasToolExecutionRequests()) {
            var requests = response.content().toolExecutionRequests();
            List<ChatMessage> msgs = new ArrayList<>();
            msgs.add(SystemMessage.from(SYSTEM_PROMPT));
            msgs.add(UserMessage.from(userMessage));
            msgs.add(response.content());

            for (var req : requests) {
                String keyword = extractArg(req.arguments(), "keyword");
                String result = productTools.searchProduct(keyword);
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
                        "workerName", "product_agent",
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
