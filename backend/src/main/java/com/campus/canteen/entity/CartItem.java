/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目实体，对应 t_cart_item。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_cart_item")
public class CartItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long dishId;
    private Integer quantity;
    private String mealPeriod;
    private LocalDateTime createdAt;
}
