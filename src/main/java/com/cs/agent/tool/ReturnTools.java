package com.cs.agent.tool;


import org.springframework.stereotype.Component;

/**
 * 退换货工具 —— 模拟数据版
 */

@Component
public class ReturnTools {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReturnTools.class);

    /**
     * 查询退换货资格
     */
    public String checkReturnEligibility(String orderId) {
        log.info("🔄 查询退换货资格: {}", orderId);
        if (orderId == null || orderId.isBlank()) {
            return "请提供订单号以查询退换货资格。";
        }

        return String.format("""
                订单 %s 退换货资格：
                - 是否在退换期内（7天）：✅ 是
                - 商品是否完好：✅ 是（不影响二次销售）
                - 是否属于特殊商品：✅ 否（可退换）
                - 可操作：✅ 支持「七天无理由退货」和「换货」
                - 退换截止日期：2026-07-10
                """, orderId);
    }

    /**
     * 计算退款金额
     */
    public String calculateRefund(String orderId, String reason) {
        log.info("💰 计算退款: orderId={}, reason={}", orderId, reason);
        if (orderId == null || orderId.isBlank()) {
            return "请提供订单号。";
        }

        return String.format("""
                订单 %s 退款核算：
                - 商品金额：39.90 元
                - 已使用优惠券：-5.00 元
                - 运费：0.00 元（包邮）
                - 退款金额：34.90 元
                - 退款方式：原路返回
                - 预计到账：1-3 个工作日

                如果确认退款，请回复「确认退款」，我将为您提交申请。
                """, orderId);
    }
}
