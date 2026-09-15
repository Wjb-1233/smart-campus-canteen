package com.campus.canteen.security;

import com.campus.canteen.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 食堂端（STALL）写操作收敛：仅放行「档口接单 / 出餐 / 核销 / 菜品维护 / 图片上传」等本职工作，
 * 其余写接口（下单、支付、充值、购物车、营养核验等）一律拒绝。
 * <p>说明：管理员端（ADMIN）拥有全量权限，不受此限制。</p>
 */
@Component
@RequiredArgsConstructor
public class StallWriteScopeInterceptor implements HandlerInterceptor {

    /** 档口订单操作： /order/{id}/status | /order/{id}/urge | /order/{id}/cancel */
    private static final Pattern ORDER_OPERATE = Pattern.compile("^/order/\\d+/(status|urge|cancel)$");
    /** 菜品维护： /dish/{id} */
    private static final Pattern DISH_ITEM = Pattern.compile("^/dish/\\d+$");

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            return true;
        }
        if (!"STALL".equals(user.getRole())) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        String uri = request.getRequestURI().replaceFirst("^/api", "");
        if (isAllowedWrite(uri)) {
            return true;
        }
        response.setStatus(403);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(403, "食堂端权限受限：禁止调用「" + uri + "」写接口")));
        return false;
    }

    private boolean isAllowedWrite(String uri) {
        if (ORDER_OPERATE.matcher(uri).matches()) {
            return true;
        }
        if (uri.startsWith("/order/scan")) {
            return true;
        }
        if (uri.startsWith("/order/statistics")) {
            return true;
        }
        if (uri.startsWith("/waste")) {
            return true;
        }
        if ("/dish".equals(uri) || DISH_ITEM.matcher(uri).matches()) {
            return true;
        }
        if (uri.startsWith("/files/upload")) {
            return true;
        }
        return uri.startsWith("/admin-readonly/");
    }
}
