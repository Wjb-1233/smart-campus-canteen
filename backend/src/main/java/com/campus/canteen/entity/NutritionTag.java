package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
