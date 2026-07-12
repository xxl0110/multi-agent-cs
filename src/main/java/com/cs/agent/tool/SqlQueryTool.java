package com.cs.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.agent.entity.CsOrder;
import com.cs.agent.mapper.OrderMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Text-to-SQL 工具 —— LLM 生成条件查数据库
 *
 * 通过 ToolSpecification 暴露给 LLM，LLM 决定查询参数。
 * 仅限 SELECT 查询，杜绝写操作。
 */
@Component
public class SqlQueryTool {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SqlQueryTool.class);

    private final OrderMapper orderMapper;

    public static final String TABLE_SCHEMA = """
            表: cs_order (订单表)
            字段: order_id(订单号), user_id(用户ID), product_name(商品名),
                  amount(金额), status(状态:0待发货1已发货2已完成3已取消),
                  logistics_company(快递公司), logistics_no(快递单号),
                  create_time(下单时间)
            """;

    public static final String QUERY_TOOL_NAME = "queryOrders";
    public static final String QUERY_TOOL_DESC = "按条件查询订单，返回格式化文本。支持按订单号/商品名/金额/状态过滤";

    public SqlQueryTool(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /** 执行查询（由 Worker 节点在 Tool 执行阶段调用） */
    public String queryOrders(String orderId, String productKeyword, String status, Double minAmount,
                              Double maxAmount, String orderBy, String orderDir, int limit) {
        if (limit < 1) limit = 1;
        if (limit > 20) limit = 20;

        log.info("📊 Text-to-SQL: orderId={}, product={}, status={}, amount=[{},{}], order={} {}, limit={}",
                orderId, productKeyword, status, minAmount, maxAmount, orderBy, orderDir, limit);

        try {
            QueryWrapper<CsOrder> wrapper = new QueryWrapper<>();

            if (notBlank(orderId)) wrapper.eq("order_id", orderId.trim());
            if (notBlank(productKeyword)) wrapper.like("product_name", productKeyword.trim());
            if (notBlank(status)) wrapper.eq("status", parseStatus(status));
            if (minAmount != null) wrapper.ge("amount", minAmount);
            if (maxAmount != null) wrapper.le("amount", maxAmount);

            if (notBlank(orderBy) && isSafeColumn(orderBy)) {
                wrapper.orderBy(true, "asc".equalsIgnoreCase(orderDir), orderBy);
            } else {
                wrapper.orderByDesc("create_time");
            }
            wrapper.last("LIMIT " + limit);

            List<CsOrder> orders = orderMapper.selectList(wrapper);
            if (orders == null || orders.isEmpty()) return "没有找到匹配的订单。";

            return orders.stream().map(this::formatOrder).collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.error("Text-to-SQL 查询失败", e);
            return "查询出错: " + e.getMessage();
        }
    }

    private String formatOrder(CsOrder order) {
        return String.format("""
                订单 %s
                - 商品: %s  |  金额: %.2f 元
                - 状态: %s
                - 物流: %s %s
                - 下单: %s""",
                order.getOrderId(), order.getProductName(), order.getAmount(),
                order.getStatusText(),
                order.getLogisticsCompany() != null ? order.getLogisticsCompany() : "-",
                order.getLogisticsNo() != null ? "(" + order.getLogisticsNo() + ")" : "",
                order.getCreateTime());
    }

    private int parseStatus(String s) {
        return switch (s.trim()) {
            case "待发货", "0" -> 0;
            case "已发货", "1" -> 1;
            case "已完成", "2" -> 2;
            case "已取消", "3" -> 3;
            default -> Integer.parseInt(s);
        };
    }

    private boolean isSafeColumn(String col) {
        return List.of("order_id", "user_id", "product_name", "amount", "status", "create_time").contains(col);
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
