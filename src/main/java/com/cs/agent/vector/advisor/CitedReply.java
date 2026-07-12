package com.cs.agent.vector.advisor;

import java.util.List;
import java.util.UUID;

/**
 * 溯源回复 —— 回复文本 + 引用列表
 */
public class CitedReply {
    private final String replyId;
    private final String reply;
    private final List<Citation> citations;

    public CitedReply(String reply, List<Citation> citations) {
        this.replyId = "r_" + UUID.randomUUID().toString().substring(0, 8);
        this.reply = reply;
        this.citations = citations != null ? citations : List.of();
    }

    public String getReplyId() { return replyId; }
    public String getReply() { return reply; }
    public List<Citation> getCitations() { return citations; }
    public boolean hasCitations() { return !citations.isEmpty(); }
}
