/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * @since 2026-09-15
 */
@Data
public class LoginRequest {
    @NotBlank(message = "学号不能为空")
    private String studentNo;
    @NotBlank(message = "密码不能为空")
    private String password;
}
