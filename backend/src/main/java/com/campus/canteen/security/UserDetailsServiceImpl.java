/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.entity.User;
import com.campus.canteen.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 用户加载服务，按学号/工号查询用户并校验状态。
 *
 * @since 2026-09-15
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentNo, username));
        if (user == null || user.getStatus() != null && user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户不存在或已禁用");
        }
        return new LoginUser(user.getId(), user.getStudentNo(), user.getPassword(), user.getRole(), user.getRealName());
    }
}
