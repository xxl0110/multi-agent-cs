package com.cs.agent.config;

import com.cs.agent.vector.MilvusClientWrapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq.CollectionSchema;
import io.milvus.v2.service.collection.request.CreateCollectionReq.FieldSchema;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class VectorConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VectorConfig.class);

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.database:default}")
    private String database;

    @Value("${milvus.collections.knowledge:cs_knowledge}")
    private String knowledgeCollection;

    @Value("${milvus.collections.knowledge-dim:1536}")
    private int knowledgeDim;

    @Value("${milvus.collections.session-log:cs_session_log}")
    private String sessionLogCollection;

    @Bean
    public MilvusClientV2 milvusClient() {
        ConnectConfig config = ConnectConfig.builder()
                .uri(host + ":" + port)
                .dbName(database)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        log.info("✅ Milvus 连接成功: {}:{}", host, port);
        return client;
    }

    @Bean
    public MilvusClientWrapper milvusClientWrapper(MilvusClientV2 milvusClient) {
        initCollections(milvusClient);
        return new MilvusClientWrapper(milvusClient, knowledgeCollection, knowledgeDim);
    }

    private void initCollections(MilvusClientV2 client) {
        initKnowledgeCollection(client);
        initSessionLogCollection(client);
        log.info("✅ Milvus 集合初始化完成");
    }

    private CollectionSchema buildKnowledgeSchema() {
        List<FieldSchema> fields = new ArrayList<>();
        fields.add(FieldSchema.builder().name("id").dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        fields.add(FieldSchema.builder().name("chunk_id").dataType(DataType.VarChar).maxLength(256).build());
        fields.add(FieldSchema.builder().name("content").dataType(DataType.VarChar).maxLength(8192).build());
        fields.add(FieldSchema.builder().name("embedding").dataType(DataType.FloatVector).dimension(knowledgeDim).build());
        fields.add(FieldSchema.builder().name("metadata").dataType(DataType.JSON).build());

        CollectionSchema schema = CollectionSchema.builder().build();
        schema.setFieldSchemaList(fields);
        return schema;
    }

    private void initKnowledgeCollection(MilvusClientV2 client) {
        boolean exists = client.hasCollection(HasCollectionReq.builder()
                .collectionName(knowledgeCollection).build());
        if (exists) {
            log.info("  集合 [{}] 已存在，跳过创建", knowledgeCollection);
            return;
        }

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(knowledgeCollection)
                .collectionSchema(buildKnowledgeSchema())
                .build());
        log.info("  ✅ 创建集合 [{}] (dim={})", knowledgeCollection, knowledgeDim);
    }

    private CollectionSchema buildSessionLogSchema() {
        List<FieldSchema> fields = new ArrayList<>();
        fields.add(FieldSchema.builder().name("id").dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        fields.add(FieldSchema.builder().name("reply_id").dataType(DataType.VarChar).maxLength(128).build());
        fields.add(FieldSchema.builder().name("session_id").dataType(DataType.VarChar).maxLength(128).build());
        fields.add(FieldSchema.builder().name("worker_name").dataType(DataType.VarChar).maxLength(64).build());
        fields.add(FieldSchema.builder().name("cited_docs").dataType(DataType.JSON).build());
        fields.add(FieldSchema.builder().name("reply_content").dataType(DataType.VarChar).maxLength(8192).build());
        fields.add(FieldSchema.builder().name("created_at").dataType(DataType.VarChar).maxLength(32).build());

        CollectionSchema schema = CollectionSchema.builder().build();
        schema.setFieldSchemaList(fields);
        return schema;
    }

    private void initSessionLogCollection(MilvusClientV2 client) {
        boolean exists = client.hasCollection(HasCollectionReq.builder()
                .collectionName(sessionLogCollection).build());
        if (exists) return;

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(sessionLogCollection)
                .collectionSchema(buildSessionLogSchema())
                .build());
        log.info("  ✅ 创建集合 [{}]", sessionLogCollection);
    }
}
