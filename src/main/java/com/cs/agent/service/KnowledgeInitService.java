package com.cs.agent.service;

import com.cs.agent.vector.EmbeddingService;
import com.cs.agent.vector.MilvusClientWrapper;
import com.cs.agent.vector.MilvusClientWrapper.KnowledgeChunk;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 启动时初始化种子知识库数据
 *
 * 如果 cs_knowledge 集合为空，自动写入预设的种子数据。
 * 种子数据与原项目的 Mock 数据兼容。
 */
@Service
@ConditionalOnProperty(name = "milvus.host")
public class KnowledgeInitService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeInitService.class);

    private final MilvusClientWrapper milvusClientWrapper;
    private final EmbeddingService embeddingService;

    public KnowledgeInitService(MilvusClientWrapper milvusClientWrapper, EmbeddingService embeddingService) {
        this.milvusClientWrapper = milvusClientWrapper;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        try {
            var stats = milvusClientWrapper.getStats();
            long count = (long) stats.get("totalChunks");
            if (count > 0) {
                log.info("📚 知识库已有 {} 条数据，跳过种子初始化", count);
                return;
            }

            log.info("📚 知识库为空，写入种子数据...");
            List<SeedEntry> seeds = buildSeedData();
            List<String> texts = seeds.stream().map(s -> s.text).toList();

            List<List<Float>> embeddings = embeddingService.embedBatch(texts);

            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (int i = 0; i < seeds.size(); i++) {
                SeedEntry entry = seeds.get(i);
                chunks.add(new KnowledgeChunk(
                        entry.chunkId,
                        entry.text,
                        embeddings.get(i),
                        entry.metadataJson
                ));
            }

            milvusClientWrapper.insertChunks(chunks);
            log.info("✅ 种子数据初始化完成，共写入 {} 条", seeds.size());
        } catch (Exception e) {
            log.warn("⚠️ 种子数据初始化失败（Milvus 未就绪？）: {}", e.getMessage());
        }
    }

    private List<SeedEntry> buildSeedData() {
        List<SeedEntry> list = new ArrayList<>();
        String ts = LocalDate.now().toString();

        list.add(seed("prod_001", "漫步者 G2 无线蓝牙耳机 299元 防汗佩戴稳固 适合运动跑步 续航20小时 好评5000+",
                jsonMeta(ts, "product", "耳机,蓝牙,运动")));
        list.add(seed("prod_002", "小米 Buds 5 主动降噪 499元 音质出色 续航30小时 支持无线充电 好评3200+",
                jsonMeta(ts, "product", "耳机,蓝牙,降噪")));
        list.add(seed("prod_003", "华为 FreeBuds 6i 入耳式 359元 主动降噪 舒适佩戴 好评2800+",
                jsonMeta(ts, "product", "耳机,蓝牙,降噪")));
        list.add(seed("prod_004", "iPhone 16 Pro Max 9999元 A18芯片 钛金属 512GB 超视网膜显示屏",
                jsonMeta(ts, "product", "手机,iPhone")));
        list.add(seed("prod_005", "华为 Mate 70 Pro 7999元 鸿蒙系统 昆仑玻璃 5000万超聚光影像",
                jsonMeta(ts, "product", "手机,华为")));
        list.add(seed("prod_006", "小米 15 Ultra 6499元 骁龙8至尊版 徕卡影像 6000mAh大电池",
                jsonMeta(ts, "product", "手机,小米")));

        list.add(seed("policy_001", "七天无理由退货：商品签收后7天内可申请退货，需保证商品完好不影响二次销售。电子产品适用此政策。",
                jsonMeta(ts, "policy", "退货,电子产品")));
        list.add(seed("policy_002", "退款规则：确认退款后金额原路返回，预计1-3个工作日到账。优惠券抵扣部分不退现金。",
                jsonMeta(ts, "policy", "退款")));
        list.add(seed("policy_003", "退换货条件：商品需完好不影响二次销售，包装配件齐全。退回运费由买家承担（商品质量问题除外）。",
                jsonMeta(ts, "policy", "退换货")));
        list.add(seed("policy_004", "物流政策：满99元包邮，不满99元运费8元。默认发中通快递，加急可发顺丰到付。",
                jsonMeta(ts, "policy", "物流")));

        list.add(seed("faq_001", "如何查询订单：提供订单号即可查询订单状态、商品信息和物流进度。订单号格式为 ORD 开头加数字。",
                jsonMeta(ts, "faq", "订单")));
        list.add(seed("faq_002", "如何申请退款：联系在线客服提供订单号和退款原因，客服会引导您完成退款流程。退款金额1-3个工作日原路返回。",
                jsonMeta(ts, "faq", "退款")));
        list.add(seed("faq_003", "如何投诉：通过在线客服提交投诉内容，我们会记录并提交相关部门处理，承诺24小时内回复。紧急情况可升级人工客服加急处理。",
                jsonMeta(ts, "faq", "投诉")));

        return list;
    }

    private static String jsonMeta(String ts, String category, String tags) {
        return "{\"sourceFile\":\"seed\",\"category\":\"" + category
                + "\",\"tags\":\"" + tags + "\",\"uploadedAt\":\"" + ts + "\"}";
    }

    private static SeedEntry seed(String chunkId, String text, String metadataJson) {
        return new SeedEntry(chunkId, text, metadataJson);
    }

    private record SeedEntry(String chunkId, String text, String metadataJson) {}
}
