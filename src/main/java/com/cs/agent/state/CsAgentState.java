package com.cs.agent.state;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.state.Channels.*;

/**
 * 客服系统的 Agent 状态定义
 *
 * 消息以 List<Map<String, String>> 格式存储（可序列化），
 * 通过 messages() 方法转为 List<ChatMessage> 供 LLM 使用。
 */
public class CsAgentState extends AgentState {

    @SuppressWarnings("unchecked")
    public static final Map<String, Channel<?>> SCHEMA = new HashMap<>(Map.of(
            "messages", appender(ArrayList::new),
            "workerResults", appender(ArrayList::new),
            "next", base((old, newVal) -> newVal),
            "roundCount", base(
                    (a, b) -> (Integer) a + (Integer) b,
                    () -> 0
            ),
            "userIntent", base((old, newVal) -> newVal),
            "finalReply", base((old, newVal) -> newVal),
            "citations", base((old, newVal) -> newVal)
    ));

    public CsAgentState(Map<String, Object> initData) {
        super(initData != null ? initData : new HashMap<>());
    }

    /** 获取全部消息列表（从可序列化格式转换为 ChatMessage） */
    @SuppressWarnings("unchecked")
    public List<ChatMessage> messages() {
        var raw = (List<Object>) value("messages").orElse(List.of());
        if (raw.isEmpty()) return new ArrayList<>();

        // 如果是 ChatMessage 对象（兼容旧数据），直接返回
        if (raw.get(0) instanceof ChatMessage) {
            @SuppressWarnings("unchecked")
            List<ChatMessage> chatMessages = (List<ChatMessage>) (List<?>) raw;
            return chatMessages;
        }

        // 如果是 Map (序列化格式)，转换为 ChatMessage
        return raw.stream()
                .map(obj -> {
                    Map<String, String> map = (Map<String, String>) obj;
                    String role = map.get("role");
                    String content = map.get("content");
                    if ("user".equals(role)) return UserMessage.from(content);
                    if ("assistant".equals(role)) return AiMessage.from(content);
                    if ("system".equals(role)) return SystemMessage.from(content);
                    return UserMessage.from(content);
                })
                .collect(Collectors.toList());
    }

    /** 添加用户消息（以序列化格式存储） */
    public void addUserMessage(String text) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", text);
        addToMessages(msg);
    }

    /** 添加 AI 回复（以序列化格式存储） */
    public void addAiMessage(String text) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", text);
        addToMessages(msg);
    }

    @SuppressWarnings("unchecked")
    private void addToMessages(Map<String, String> msg) {
        var raw = (List<Object>) value("messages").orElseGet(ArrayList::new);
        raw.add(msg);
        // 更新 data 中的 messages 引用
        data().put("messages", raw);
    }

    /** 获取 Worker 执行结果列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> workerResults() {
        return (List<Map<String, Object>>) value("workerResults").orElse(List.of());
    }

    /** 获取 Supervisor 决策的下一节点 */
    public String next() {
        return value("next").map(String.class::cast).orElse("FINISH");
    }

    /** 获取当前轮次 */
    public Integer roundCount() {
        return value("roundCount").map(Integer.class::cast).orElse(0);
    }

    /** 获取会话 ID */
    public String sessionId() {
        return value("sessionId").map(String.class::cast).orElse("");
    }

    /** 获取 Worker 最后回复（直接通道，不受消息列表干扰） */
    public String finalReply() {
        return value("finalReply").map(String.class::cast).orElse("");
    }

    /** 获取溯源引用列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> citations() {
        return (List<Map<String, Object>>) value("citations").orElse(List.of());
    }

    /** 获取最后一条 AI 回复 */
    public String lastAiReply() {
        for (ChatMessage msg : messages()) {
            if (msg instanceof AiMessage aiMsg) {
                return aiMsg.text();
            }
        }
        return "";
    }

    /** 获取最后一条用户消息 */
    public String lastUserMessage() {
        var msgs = messages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i) instanceof UserMessage userMsg) {
                return userMsg.singleText();
            }
        }
        return "";
    }
}
