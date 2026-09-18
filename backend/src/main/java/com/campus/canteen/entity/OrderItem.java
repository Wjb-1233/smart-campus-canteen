/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细实体，对应 t_order_item，保存下单时的价格快照。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_order_item")
public class OrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long dishId;
    private String dishName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private Integer calorie;
    private BigDecimal protein;
    private Long replacedFrom;
}
