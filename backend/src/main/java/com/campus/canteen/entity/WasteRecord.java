/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 剩饭浪费溯源实体，对应 t_waste_record。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_waste_record")
public class WasteRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long dishId;
    private BigDecimal wasteRatio;
    private String reason;
    private LocalDateTime createdAt;
}
