package com.cs.agent.node.agent;

import com.cs.agent.state.CsAgentState;
import com.cs.agent.tool.ProductTools;
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
 * 商品 Worker 节点
 *
 * 处理商品搜索、推荐、详情查询等问题。
 */

@Component("productAgentNode")
public class ProductAgentNode implements NodeAction<CsAgentState> {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductAgentNode.class);

    private final ChatLanguageModel chatModel;
    private final ProductTools productTools;
    private final KnowledgeAdvisor knowledgeAdvisor;
    private final PromptService promptService;

    public ProductAgentNode(@Qualifier("workerChatModel") ChatLanguageModel chatModel,
                            ProductTools productTools,
                            KnowledgeAdvisor knowledgeAdvisor,
                            PromptService promptService) {
        this.chatModel = chatModel;
        this.productTools = productTools;
        this.knowledgeAdvisor = knowledgeAdvisor;
        this.promptService = promptService;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        String userMessage = state.lastUserMessage();
        log.info("🛍️  [ProductWorker] 处理: {}", userMessage);

        // ★ RAG: 检索知识库
        AdvisedContext ctx = knowledgeAdvisor.retrieveBySchema(userMessage, CollectionSchema.PRODUCT, "product_agent");
        String knowledgeContext = ctx.isEmpty() ? "" : "\n\n【参考知识】\n" + ctx.getCombinedContext();
        String enhancedPrompt = promptService.getPrompt("product_agent") + knowledgeContext;

        List<ToolSpecification> tool = List.of(
                ToolSpecification.builder()
                        .name("searchProduct")
                        .description("根据关键词搜索商品")
                        .addParameter("keyword", STRING)
                        .build()
        );

        var response = chatModel.generate(
                List.of(SystemMessage.from(enhancedPrompt), UserMessage.from(userMessage)),
                tool
        );

        String reply;
        if (response.content().hasToolExecutionRequests()) {
            var requests = response.content().toolExecutionRequests();
            List<ChatMessage> msgs = new ArrayList<>();
            msgs.add(SystemMessage.from(enhancedPrompt));
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

        // ★ 溯源绑定
        CitedReply citedReply = knowledgeAdvisor.cite(reply, ctx.getDocuments());
        knowledgeAdvisor.trackConsumption(state.sessionId(), "product_agent", citedReply);

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
                        "workerName", "product_agent",
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
