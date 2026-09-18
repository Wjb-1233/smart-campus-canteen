/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 营养/健康标签字典实体，对应 t_nutrition_tag。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_nutrition_tag")
public class NutritionTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String category;
    private String description;
}
