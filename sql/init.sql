-- ============================================================
-- 智能客服多Agent协同系统 - 数据库初始化脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS cs_agent DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cs_agent;

-- ============================================================
-- 订单表
-- ============================================================
DROP TABLE IF EXISTS cs_order;
CREATE TABLE cs_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  order_id VARCHAR(32) NOT NULL COMMENT '订单号',
  user_id VARCHAR(32) DEFAULT '' COMMENT '用户ID',
  product_name VARCHAR(100) NOT NULL COMMENT '商品名称',
  amount DECIMAL(10,2) NOT NULL COMMENT '金额',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0=待发货 1=已发货 2=已完成 3=已取消',
  logistics_company VARCHAR(50) DEFAULT '' COMMENT '快递公司',
  logistics_no VARCHAR(50) DEFAULT '' COMMENT '快递单号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_order_id (order_id),
  KEY idx_user_id (user_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================================
-- 初始化订单数据
-- ============================================================
INSERT INTO cs_order (order_id, user_id, product_name, amount, status, logistics_company, logistics_no, create_time) VALUES
('ORD20240630', 'user_001', 'iPhone 16 Pro 手机壳', 39.90, 1, '顺丰速运', 'SF1234567890', '2026-06-30 14:22:33'),
('ORD20240701', 'user_001', '漫步者 G2 无线蓝牙耳机', 299.00, 0, '', '', '2026-07-01 10:15:00'),
('ORD20240702', 'user_002', '小米 Buds 5 主动降噪耳机', 499.00, 1, '中通快递', 'ZT9876543210', '2026-07-02 16:30:00'),
('ORD20240703', 'user_001', '华为 FreeBuds 6i', 359.00, 2, '京东物流', 'JD5555555555', '2026-07-03 09:00:00');

-- ============================================================
-- 验证
-- ============================================================
SELECT '✅ 数据库初始化完成' AS status;
SELECT id, order_id, product_name, amount,
  CASE status
    WHEN 0 THEN '待发货'
    WHEN 1 THEN '已发货'
    WHEN 2 THEN '已完成'
    WHEN 3 THEN '已取消'
  END AS status_text,
  logistics_company, logistics_no, create_time
FROM cs_order;
