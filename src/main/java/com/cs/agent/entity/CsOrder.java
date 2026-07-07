package com.cs.agent.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类 —— 对应 MySQL cs_order 表
 */
public class CsOrder {

    private Long id;
    private String orderId;
    private String userId;
    private String productName;
    private BigDecimal amount;
    private Integer status;      // 0=待发货 1=已发货 2=已完成 3=已取消
    private String logisticsCompany;
    private String logisticsNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getStatusText() {
        return switch (status) {
            case 0 -> "待发货";
            case 1 -> "已发货";
            case 2 -> "已完成";
            case 3 -> "已取消";
            default -> "未知";
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getLogisticsCompany() { return logisticsCompany; }
    public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
    public String getLogisticsNo() { return logisticsNo; }
    public void setLogisticsNo(String logisticsNo) { this.logisticsNo = logisticsNo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
