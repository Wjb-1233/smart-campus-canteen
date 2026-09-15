package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_dish")
public class Dish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stallId;
    private Long categoryId;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String imageUrl;
    private String nutritionJson;
    /** 营养数据是否已经营养师核验：1 已核验 0 待核验 */
    private Integer nutritionVerified;
    /** 核验人（营养师）用户 ID */
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private String mealPeriod;
    private Integer heatScore;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 非持久化：菜品标签码列表（用于管理端保存标签关联） */
    @TableField(exist = false)
    private List<String> tagCodes;
}
