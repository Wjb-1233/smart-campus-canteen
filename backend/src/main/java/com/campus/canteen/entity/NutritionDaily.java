package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("t_nutrition_daily")
public class NutritionDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate statDate;
    private Integer calorie;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carb;
    private BigDecimal proteinRate;
}
