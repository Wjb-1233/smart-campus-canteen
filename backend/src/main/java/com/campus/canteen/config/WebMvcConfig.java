package com.campus.canteen.config;

import com.campus.canteen.security.AuditInterceptor;
import com.campus.canteen.security.StallWriteScopeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final StallWriteScopeInterceptor readOnlyInterceptor;
    private final AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(readOnlyInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/oauth/**", "/ws/**", "/v3/api-docs/**", "/swagger-ui/**");
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/ws/**", "/v3/api-docs/**", "/swagger-ui/**", "/files/**");
    }
}
