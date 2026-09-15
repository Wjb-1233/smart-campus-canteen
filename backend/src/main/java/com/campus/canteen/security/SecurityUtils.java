package com.campus.canteen.security;

import com.campus.canteen.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(401, "未登录");
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }
}
