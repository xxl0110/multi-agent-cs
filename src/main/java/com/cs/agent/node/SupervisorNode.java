package com.cs.agent.node;

import com.cs.agent.state.CsAgentState;
import dev.langchain4j.model.chat.ChatLanguageModel;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Supervisor 节点 —— Agent 群的"大脑"
 *
 * 职责：
 * 1. 分析用户输入的意图
 * 2. 决定下一个由哪个 Worker 处理
 * 3. 判断是否应该结束
 *
 * 路由逻辑完全由 LLM 驱动（NL2Routing），
 * 不需要硬编码 if-else 规则。
 */

@Component
public class SupervisorNode implements NodeAction<CsAgentState> {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SupervisorNode.class);

    private final ChatLanguageModel chatModel;

    private static final String SUPERVISOR_PROMPT = """
            你是一个电商客服系统的智能调度员（Supervisor）。

            你的职责：
            1. 分析用户的输入，判断应该由哪个专用客服来处理
            2. **只有第一轮才需要路由**，后续轮次都输出 FINISH
            3. 注意：如果用户输入的是订单号（ORD开头的数字），这通常是上一步被问到的补充信息

            可用 Worker（只能从以下选择）：
            - order_agent: 订单查询、物流查询、订单修改
            - product_agent: 商品搜索、商品推荐、商品详情查询
            - return_agent: 退换货申请、退款计算、退换货进度查询
            - complaint_agent: 投诉处理、升级人工客服

            输出格式（严格 JSON，不要多余内容）：
            {"next": "order_agent"}
            {"next": "product_agent"}
            {"next": "return_agent"}
            {"next": "complaint_agent"}
            {"next": "FINISH"}
            """;

    public SupervisorNode(@Qualifier("supervisorChatModel") ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        log.info("🤖 [Supervisor] 执行中... 当前轮次: {}", state.roundCount());

        // ===== 安全阀：超过 6 轮强制结束 =====
        if (state.roundCount() >= 6) {
            log.warn("⚠️ [Supervisor] 超过最大轮次限制，强制结束");
            return Map.of(
                    "next", "FINISH",
                    "roundCount", 1
            );
        }

        // ===== 订单号检测（优先级最高，无视轮次和历史） =====
        String lastMsg = state.lastUserMessage();
        if (lastMsg != null && lastMsg.matches(".*ORD\\d{5,}.*")) {
            log.info("🎯 [Supervisor] 检测到订单号，路由到 order_agent");
            return Map.of("next", "order_agent", "roundCount", 1);
        }

        // ===== 已有 Worker 回复过 → 直接结束，不走 LLM =====
        if (!state.workerResults().isEmpty()) {
            log.info("🏁 [Supervisor] 已有 Worker 回复，直接结束");
            return Map.of("next", "FINISH", "roundCount", 1);
        }

        // ===== 第一轮：走 LLM 决策 =====
        StringBuilder prompt = new StringBuilder(SUPERVISOR_PROMPT);
        prompt.append("\n\n【当前对话历史】\n");
        for (var msg : state.messages()) {
            if (msg instanceof dev.langchain4j.data.message.UserMessage userMsg) {
                prompt.append("用户: ").append(userMsg.singleText()).append("\n");
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
                prompt.append("AI: ").append(aiMsg.text()).append("\n");
            }
        }

        String response = chatModel.generate(prompt.toString());
        String next = parseNext(response);

        log.info("🎯 [Supervisor] 决策: next={}", next);

        return Map.of(
                "next", next,
                "roundCount", 1
        );
    }

    /**
     * 解析 LLM 输出，提取 next 字段
     */
    private String parseNext(String response) {
        if (response == null || response.isBlank()) return "FINISH";

        String trimmed = response.trim();

        // 尝试 JSON 解析
        if (trimmed.contains("\"next\"")) {
            if (trimmed.contains("order_agent")) return "order_agent";
            if (trimmed.contains("product_agent")) return "product_agent";
            if (trimmed.contains("return_agent")) return "return_agent";
            if (trimmed.contains("complaint_agent")) return "complaint_agent";
            if (trimmed.contains("FINISH")) return "FINISH";
        }

        // 兜底：直接关键词匹配（含纯订单号 ORDxxx 的情况）
        String lower = trimmed.toLowerCase();
        if (lower.contains("order") || lower.contains("订单") || lower.matches(".*ord\\d{5,}.*")) return "order_agent";
        if (lower.contains("product") || lower.contains("商品") || lower.contains("推荐")) return "product_agent";
        if (lower.contains("return") || lower.contains("退款") || lower.contains("退换")) return "return_agent";
        if (lower.contains("complaint") || lower.contains("投诉") || lower.contains("人工")) return "complaint_agent";

        return "FINISH";
    }
}
