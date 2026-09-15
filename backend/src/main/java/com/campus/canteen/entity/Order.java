package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long stallId;
    private String mealPeriod;
    private BigDecimal totalAmount;
    private Integer totalCalorie;
    private String status;
    private String payChannel;
    private String pickupCode;
    private LocalDateTime expectPickupAt;
    private String remark;
    private Integer abnormalFlag;
    private String abnormalReason;
    private Integer urgeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
