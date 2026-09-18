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
 * 消费账户流水实体，对应 t_balance_ledger。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_balance_ledger")
public class BalanceLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal changeAmt;
    private BigDecimal balanceAfter;
    private String bizType;
    private String bizNo;
    private String remark;
    private LocalDateTime createdAt;
}
