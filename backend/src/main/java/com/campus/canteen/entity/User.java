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
 * 用户实体，对应 t_user，学生/老师、食堂、管理员三端共用。
 *
 * @since 2026-09-15
 */
@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String studentNo;
    private String password;
    private String realName;
    private String phone;
    private String role;
    private String department;
    private String grade;
    private BigDecimal balance;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
