package com.cs.agent.vector.advisor;

import com.cs.agent.vector.advisor.metadata.CollectionSchema;
import com.cs.agent.vector.store.KnowledgeBaseStore;
import com.cs.agent.vector.store.SessionLogStore;
import com.cs.agent.vector.store.SessionLogStore.CitationLog;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识顾问 —— Worker 的知识检索入口
 *
 * 职责：
 * 1. 根据用户意图检索最相关的知识库文档
 * 2. 将回复与源文档绑定，生成带引用的回复
 * 3. 记录溯源信息到审计日志
 */
@Service
public class KnowledgeAdvisor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeAdvisor.class);

    private final KnowledgeBaseStore knowledgeBaseStore;
    private final SessionLogStore sessionLogStore;

    public KnowledgeAdvisor(KnowledgeBaseStore knowledgeBaseStore, SessionLogStore sessionLogStore) {
        this.knowledgeBaseStore = knowledgeBaseStore;
        this.sessionLogStore = sessionLogStore;
    }

    /** 检索知识库 */
    public AdvisedContext retrieve(String query, RetrievalSpec spec) {
        return knowledgeBaseStore.searchKnowledge(query, spec);
    }

    /** 按 Schema 检索（自动设置 category 过滤） */
    public AdvisedContext retrieveBySchema(String query, CollectionSchema schema) {
        RetrievalSpec spec = RetrievalSpec.builder()
                .topK(3)
                .minScore(0.6)
                .expr(schema.categoryExpr())
                .build();
        return retrieve(query, spec);
    }

    /** 将回复与源文档绑定，生成带引用的溯源回复 */
    public CitedReply cite(String reply, List<AdvisedContext.RetrievedDoc> sources) {
        List<Citation> citations = sources.stream()
                .map(doc -> new Citation(
                        doc.getChunkId(),
                        "cs_knowledge",
                        doc.getScore(),
                        doc.getContent().length() > 100
                                ? doc.getContent().substring(0, 100) + "..."
                                : doc.getContent(),
                        parseMetadata(doc.getMetadataJson())
                ))
                .collect(Collectors.toList());
        return new CitedReply(reply, citations);
    }

    /** 记录溯源信息 */
    public void trackConsumption(String sessionId, String workerName, CitedReply citedReply) {
        if (citedReply == null || !citedReply.hasCitations()) return;
        CitationLog logEntry = new CitationLog(
                citedReply.getReplyId(), sessionId, workerName,
                citedReply.getCitations(), citedReply.getReply()
        );
        sessionLogStore.logCitation(logEntry);
        log.info("📝 记录溯源: sessionId={}, worker={}, citations={}",
                sessionId, workerName, citedReply.getCitations().size());
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            String clean = json.replaceAll("[{}\"]", "");
            for (String pair : clean.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) result.put(kv[0].trim(), kv[1].trim());
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
