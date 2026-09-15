package com.campus.canteen.security;

import com.campus.canteen.entity.AuditLog;
import com.campus.canteen.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 敏感操作审计：拦截非 GET/OPTIONS 写操作，写入 t_audit_log。
 * 记录用户、动作、详情（URI+方法+状态码）、IP。
 */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {
    private final AuditLogMapper auditLogMapper;
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            String method = request.getMethod();
            if (!WRITE_METHODS.contains(method)) {
                return;
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
                userId = user.getId();
            }
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setAction(method + " " + request.getRequestURI());
            log.setDetail("status=" + response.getStatus()
                    + " qs=" + (request.getQueryString() == null ? "" : request.getQueryString()));
            log.setIp(clientIp(request));
            log.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(log);
        } catch (Exception ignored) {
            // 审计失败不阻断业务
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
