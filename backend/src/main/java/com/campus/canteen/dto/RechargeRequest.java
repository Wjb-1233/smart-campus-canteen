/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户充值请求参数。
 *
 * @since 2026-09-15
 */
@Data
public class RechargeRequest {
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    private String channel = "CAMPUS_CARD";
}
