package com.campus.canteen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeRequest {
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    private String channel = "CAMPUS_CARD";
}
