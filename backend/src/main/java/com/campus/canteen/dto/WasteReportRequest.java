package com.campus.canteen.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

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
