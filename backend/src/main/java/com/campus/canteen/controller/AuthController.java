/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.dto.HealthUpdateRequest;
import com.campus.canteen.dto.LoginRequest;
import com.campus.canteen.dto.RechargeRequest;
import com.campus.canteen.entity.BalanceLedger;
import com.campus.canteen.service.AuthService;
import com.campus.canteen.vo.UserProfileVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证接口：登录、教务 OAuth2 模拟授权、个人资料与账户充值。
 *
 * @since 2026-09-15
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * 账号密码登录，成功后返回令牌与角色。
     *
     * @param request 登录请求
     * @return 含 token、role、realName 的登录结果
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 查询当前登录用户的个人资料。
     *
     * @return 用户资料视图对象
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        return Result.ok(authService.profile());
    }

    /**
     * 更新当前用户的健康档案（过敏原、饮食禁忌、营养目标）。
     *
     * @param request 健康档案更新请求
     * @return 更新后的用户资料
     */
    @PutMapping("/health")
    public Result<UserProfileVO> updateHealth(@RequestBody HealthUpdateRequest request) {
        return Result.ok(authService.updateHealth(request));
    }

    /**
     * 校园卡账户充值。
     *
     * @param request 充值请求
     * @return 充值结果与最新余额
     */
    @PostMapping("/recharge")
    public Result<Map<String, Object>> recharge(@Valid @RequestBody RechargeRequest request) {
        return Result.ok(authService.recharge(request));
    }

    /**
     * 查询当前用户的账户流水。
     *
     * @param limit 返回条数上限
     * @return 账户流水列表
     */
    @GetMapping("/ledger")
    public Result<List<BalanceLedger>> ledger(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return Result.ok(authService.ledger(limit));
    }

    /**
     * 获取教务 OAuth2 授权地址（模拟）。
     *
     * @param redirectUri 授权后跳回地址，可为空
     * @return 含 authorizeUrl 与 state 的授权信息
     */
    @GetMapping("/oauth/edu/authorize")
    public Result<Map<String, Object>> eduAuthorize(
            @RequestParam(name = "redirectUri", required = false) String redirectUri) {
        return Result.ok(authService.oauthAuthorizeUrl(redirectUri));
    }

    /**
     * 教务 OAuth2 回调（模拟）：用授权码换取系统登录态。
     *
     * @param code  授权码，约定为 EDU_{学号}
     * @param state 授权校验串，可为空
     * @return 登录结果
     */
    @GetMapping("/oauth/edu/callback")
    public Result<Map<String, Object>> eduCallback(@RequestParam(name = "code") String code,
                                                   @RequestParam(name = "state", required = false) String state) {
        return Result.ok(authService.oauthEduCallback(code, state));
    }
}
