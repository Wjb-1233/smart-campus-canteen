/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 营养师修正菜品营养数据请求体（单位：kcal / g）。
 *
 * @since 2026-09-15
 */
@Data
public class DishNutritionUpdateRequest {
    @NotNull
    @Min(0)
    @Max(5000)
    private Integer calorie;

    @NotNull
    @Min(0)
    @Max(500)
    private Integer protein;

    @NotNull
    @Min(0)
    @Max(500)
    private Integer fat;

    @NotNull
    @Min(0)
    @Max(1000)
    private Integer carb;

    /** 修正说明，便于审计追溯 */
    private String remark;
}
