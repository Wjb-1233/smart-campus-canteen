/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 剩饭浪费登记请求参数。
 *
 * @since 2026-09-15
 */
@Data
public class WasteReportRequest {
    @NotNull
    private Long orderId;
    @NotNull
    private Long dishId;
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal wasteRatio;
    private String reason;
}
