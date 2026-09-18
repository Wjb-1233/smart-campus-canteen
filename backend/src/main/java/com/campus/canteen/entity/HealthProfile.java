/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户健康档案实体，对应 t_health_profile。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_health_profile")
public class HealthProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String allergyJson;
    private String dietTabooJson;
    private Integer targetCalorie;
    private Integer targetProtein;
    private String religionTag;
}
