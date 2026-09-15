package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
