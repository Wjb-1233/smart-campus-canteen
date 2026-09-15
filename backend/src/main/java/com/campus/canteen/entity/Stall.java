package com.campus.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_stall")
public class Stall {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long canteenId;
    private String name;
    private String type;
    private Integer queueCount;
    private Integer status;
}
