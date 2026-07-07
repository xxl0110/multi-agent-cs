package com.cs.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.agent.entity.CsOrder;
import com.cs.agent.mapper.OrderMapper;
import org.springframework.stereotype.Service;

/**
 * 订单服务 —— 从 MySQL 查真实订单数据
 */
@Service
public class OrderService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderService.class);

    private final OrderMapper orderMapper;

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 根据订单号查订单
     */
    public CsOrder getByOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) return null;
        return orderMapper.selectOne(
                new LambdaQueryWrapper<CsOrder>()
                        .eq(CsOrder::getOrderId, orderId.trim())
        );
    }

    /**
     * 根据用户 ID 查最近订单
     */
    public CsOrder getLatestByUserId(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return orderMapper.selectOne(
                new LambdaQueryWrapper<CsOrder>()
                        .eq(CsOrder::getUserId, userId)
                        .orderByDesc(CsOrder::getCreateTime)
                        .last("LIMIT 1")
        );
    }

    /**
     * 格式化订单为可读文本
     */
    public String formatOrder(CsOrder order) {
        if (order == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("订单 ").append(order.getOrderId()).append(" 查询结果：\n");
        sb.append("- 商品：").append(order.getProductName()).append("\n");
        sb.append("- 金额：").append(order.getAmount()).append(" 元\n");
        sb.append("- 状态：").append(order.getStatusText()).append("\n");

        if (order.getLogisticsCompany() != null && !order.getLogisticsCompany().isEmpty()) {
            sb.append("- 物流：").append(order.getLogisticsCompany())
              .append("（").append(order.getLogisticsNo()).append("）\n");
        }

        sb.append("- 下单时间：").append(order.getCreateTime());
        return sb.toString();
    }
}
