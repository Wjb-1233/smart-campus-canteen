/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

/**
 * JWT 令牌生成与解析工具。
 *
 * @since 2026-09-15
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    /**
     * 构造 JWT 工具，按配置的密钥与有效期初始化签名密钥。
     *
     * @param secret      JWT 签名密钥
     * @param expireHours 令牌有效小时数
     */
    public JwtUtil(@Value("${canteen.jwt.secret}") String secret,
                   @Value("${canteen.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    /**
     * 生成访问令牌。
     *
     * @param userId    用户 ID
     * @param studentNo 学号/工号
     * @param role      角色标识
     * @return 签名后的 JWT 字符串
     */
    public String generate(Long userId, String studentNo, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(Map.of("uid", userId, "sno", studentNo, "role", role))
                .subject(studentNo)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析令牌。
     *
     * @param token JWT 字符串
     * @return 令牌载荷
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
