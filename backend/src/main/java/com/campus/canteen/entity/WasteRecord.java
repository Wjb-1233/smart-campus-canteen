package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
