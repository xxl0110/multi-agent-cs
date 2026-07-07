package com.cs.agent.tool;


import org.springframework.stereotype.Component;

/**
 * 商品工具 —— 模拟数据版
 */

@Component
public class ProductTools {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductTools.class);

    /**
     * 搜索商品
     */
    public String searchProduct(String keyword) {
        log.info("🔎 搜索商品: {}", keyword);
        if (keyword == null || keyword.isBlank()) {
            return "请告诉我您想搜索什么商品。";
        }

        if (keyword.contains("耳机") || keyword.contains("蓝牙") || keyword.contains("耳")) {
            return """
                    为您推荐以下蓝牙耳机：
                    \t1.【漫步者 G2】无线蓝牙耳机 299元  ★ 好评 5000+
                    \t2.【小米 Buds 5】主动降噪蓝牙耳机 499元  ★ 好评 3200+
                    \t3.【华为 FreeBuds 6i】入耳式降噪耳机 359元  ★ 好评 2800+
                    \t4.【QCY Crossky GTR】挂耳式运动耳机 129元  ★ 好评 1800+

                    如果您是运动跑步用，推荐 QCY Crossky GTR 或 漫步者 G2，佩戴稳固防汗。
                    如果需要降噪，推荐小米 Buds 5 或华为 FreeBuds 6i。
                    """;
        }

        if (keyword.contains("手机") || keyword.contains("phone")) {
            return """
                    热门手机推荐：
                    \t1.【iPhone 16 Pro Max】256G 9999元  ★ 旗舰之选
                    \t2.【华为 Mate 70 Pro】512G 7999元  ★ 国产标杆
                    \t3.【小米 15 Ultra】512G 6499元  ★ 影像旗舰
                    \t4.【OPPO Find X8 Pro】256G 5999元  ★ 颜值在线

                    您对预算有什么要求？我可以进一步推荐。
                    """;
        }

        if (keyword.contains("电脑") || keyword.contains("笔记本")) {
            return """
                    热门电脑推荐：
                    \t1.【MacBook Air M4】13.6寸 8999元  ★ 轻薄长续航
                    \t2.【联想 ThinkPad X1 Carbon】14寸 10999元  ★ 商务首选
                    \t3.【华硕 ROG 幻14】14寸 9999元  ★ 性能游戏本

                    您主要用于办公还是游戏？我可以进一步推荐。
                    """;
        }

        return String.format("找到「%s」相关商品 %d 件。请告诉我更多需求，比如预算或品牌偏好，我可以精确推荐。",
                keyword, 12 + (int) (Math.random() * 20));
    }
}
