/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.dto;

import lombok.Data;

import java.util.List;

/**
 * 健康档案更新请求参数。
 *
 * @since 2026-09-15
 */
@Data
public class HealthUpdateRequest {
    private List<String> allergies;
    private List<String> dietTaboo;
    private Integer targetCalorie;
    private Integer targetProtein;
    private String religionTag;
}
