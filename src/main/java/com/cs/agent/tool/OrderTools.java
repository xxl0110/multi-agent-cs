package com.cs.agent.tool;

import com.cs.agent.entity.CsOrder;
import com.cs.agent.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * 订单查询工具 —— 从 MySQL 查询真实订单数据
 */
@Component
public class OrderTools {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderTools.class);

    private final OrderService orderService;

    public OrderTools(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 查询订单
     * 优先按订单号查，没有订单号则查最近订单
     */
    public String queryOrder(String orderId) {
        log.info("🔍 查询订单: {}", orderId);

        CsOrder order;
        if (orderId != null && !orderId.isBlank()) {
            order = orderService.getByOrderId(orderId);
        } else {
            order = orderService.getLatestByUserId("user_001");
        }

        if (order == null) {
            return "未找到订单，请检查订单号是否正确。";
        }

        return orderService.formatOrder(order);
    }

    /**
     * 查询物流
     */
    public String queryLogistics(String orderId) {
        log.info("🚚 查询物流: {}", orderId);

        CsOrder order = null;
        if (orderId != null && !orderId.isBlank()) {
            order = orderService.getByOrderId(orderId);
        } else {
            order = orderService.getLatestByUserId("user_001");
        }

        if (order == null) {
            return "未找到订单，请提供正确的订单号。";
        }

        if (order.getLogisticsCompany() == null || order.getLogisticsCompany().isEmpty()) {
            return "订单 " + order.getOrderId() + " 尚未发货，暂无物流信息。";
        }

        return String.format(
                "订单 %s 的物流信息：\n- 快递公司：%s\n- 快递单号：%s\n- 配送状态：%s\n- 预计送达：最近2-3天",
                order.getOrderId(), order.getLogisticsCompany(),
                order.getLogisticsNo(), "配送中"
        );
    }
}
