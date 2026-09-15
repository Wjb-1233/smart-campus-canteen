package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_cart_item")
public class CartItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long dishId;
    private Integer quantity;
    private String mealPeriod;
    private LocalDateTime createdAt;
}
