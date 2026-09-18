/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.security;

import com.campus.canteen.common.BizException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具，用于获取当前登录用户信息。
 *
 * @since 2026-09-15
 */
public final class SecurityUtils {
    private SecurityUtils() {}

    /**
     * 获取当前登录用户。
     *
     * @return 当前登录用户主体
     * @throws BizException 未登录时抛出 401 业务异常
     */
    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(401, "未登录");
        }
        return user;
    }

    /**
     * 获取当前登录用户的用户 ID。
     *
     * @return 当前登录用户 ID
     */
    public static Long currentUserId() {
        return currentUser().getId();
    }
}
