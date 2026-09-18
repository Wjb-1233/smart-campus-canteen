/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.dto;

import lombok.Data;

/**
 * 加入购物车请求参数。
 *
 * @since 2026-09-15
 */
@Data
public class CartAddRequest {
    private Long dishId;
    private Integer quantity = 1;
    private String mealPeriod;
}
