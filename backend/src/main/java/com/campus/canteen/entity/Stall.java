/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 档口实体，对应 t_stall。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_stall")
public class Stall {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long canteenId;
    private String name;
    private String type;
    private Integer queueCount;
    private Integer status;
}
