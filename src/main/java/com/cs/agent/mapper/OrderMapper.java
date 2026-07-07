package com.cs.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.agent.entity.CsOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper —— MyBatis Plus 自动实现 CRUD
 */
@Mapper
public interface OrderMapper extends BaseMapper<CsOrder> {
}
