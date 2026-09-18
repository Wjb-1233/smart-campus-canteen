/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.OrderItem;

import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细数据访问层。
 *
 * @since 2026-09-15
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
