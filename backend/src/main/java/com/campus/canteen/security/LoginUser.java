/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.security;

import lombok.Getter;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 登录用户主体，封装 id、学号/工号与角色。
 *
 * @since 2026-09-15
 */
@Getter
public class LoginUser implements UserDetails {
    private final Long id;
    private final String studentNo;
    private final String password;
    private final String role;
    private final String realName;

    public LoginUser(Long id, String studentNo, String password, String role, String realName) {
        this.id = id;
        this.studentNo = studentNo;
        this.password = password;
        this.role = role;
        this.realName = realName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return studentNo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
