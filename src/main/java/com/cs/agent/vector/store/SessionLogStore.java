package com.cs.agent.vector.store;

import com.alibaba.fastjson.JSONObject;
import com.cs.agent.vector.advisor.Citation;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class SessionLogStore {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionLogStore.class);

    private final MilvusClientV2 milvusClient;
    private final String collectionName;

    public SessionLogStore(MilvusClientV2 milvusClient,
                           @Value("${milvus.collections.session-log:cs_session_log}") String collectionName) {
        this.milvusClient = milvusClient;
        this.collectionName = collectionName;
    }

    public static class CitationLog {
        public final String replyId;
        public final String sessionId;
        public final String workerName;
        public final List<Map<String, Object>> citedDocs;
        public final String replyContent;
        public final String createdAt;

        public CitationLog(String replyId, String sessionId, String workerName,
                           List<Citation> citations, String replyContent) {
            this.replyId = replyId;
            this.sessionId = sessionId;
            this.workerName = workerName;
            this.citedDocs = citations.stream().map(c -> Map.<String, Object>of(
                    "docId", c.getDocId(),
                    "score", c.getScore(),
                    "snippet", c.getSnippet(),
                    "metadata", c.getMetadata()
            )).toList();
            this.replyContent = replyContent.length() > 8000 ? replyContent.substring(0, 8000) : replyContent;
            this.createdAt = LocalDateTime.now().toString();
        }
    }

    public void logCitation(CitationLog logEntry) {
        try {
            JSONObject row = new JSONObject();
            row.put("reply_id", logEntry.replyId);
            row.put("session_id", logEntry.sessionId);
            row.put("worker_name", logEntry.workerName);
            row.put("cited_docs", logEntry.citedDocs);
            row.put("reply_content", logEntry.replyContent);
            row.put("created_at", logEntry.createdAt);

            milvusClient.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(row))
                    .build());
        } catch (Exception e) {
            log.warn("溯源日志写入失败: {}", e.getMessage());
        }
    }
}
