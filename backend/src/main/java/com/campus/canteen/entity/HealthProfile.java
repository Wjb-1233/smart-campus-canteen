package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_health_profile")
public class HealthProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String allergyJson;
    private String dietTabooJson;
    private Integer targetCalorie;
    private Integer targetProtein;
    private String religionTag;
}
