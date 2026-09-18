/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单请求参数。
 *
 * @since 2026-09-15
 */
@Data
public class PlaceOrderRequest {
    @NotBlank(message = "用餐时段不能为空")
    private String mealPeriod;
    private String payChannel = "BALANCE";
    private String remark;
    @NotEmpty(message = "订单明细不能为空")
    private List<Item> items;

    /**
     * 订单明细项：菜品与数量。
     *
     * @since 2026-09-15
     */
    @Data
    public static class Item {
        @NotNull
        private Long dishId;
        @Min(1)
        private Integer quantity = 1;
    }
}
