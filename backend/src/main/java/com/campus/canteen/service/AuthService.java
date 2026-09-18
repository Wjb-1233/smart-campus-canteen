/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.dto.HealthUpdateRequest;
import com.campus.canteen.dto.LoginRequest;
import com.campus.canteen.dto.RechargeRequest;
import com.campus.canteen.entity.BalanceLedger;
import com.campus.canteen.entity.HealthProfile;
import com.campus.canteen.entity.NutritionDaily;
import com.campus.canteen.entity.Order;
import com.campus.canteen.entity.User;
import com.campus.canteen.mapper.BalanceLedgerMapper;
import com.campus.canteen.mapper.HealthProfileMapper;
import com.campus.canteen.mapper.NutritionDailyMapper;
import com.campus.canteen.mapper.OrderMapper;
import com.campus.canteen.mapper.UserMapper;
import com.campus.canteen.security.JwtUtil;
import com.campus.canteen.security.LoginUser;
import com.campus.canteen.security.SecurityUtils;
import com.campus.canteen.util.MaskUtil;
import com.campus.canteen.vo.UserProfileVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认证与账户业务：登录、个人资料、健康档案、充值与流水查询。
 *
 * @since 2026-09-15
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final HealthProfileMapper healthProfileMapper;
    private final OrderMapper orderMapper;
    private final NutritionDailyMapper nutritionDailyMapper;
    private final BalanceLedgerMapper balanceLedgerMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 账号密码登录，成功后签发 JWT 并写入在线状态。
     *
     * @param req 登录请求
     * @return 含 token、role、realName、studentNo、userId 的登录结果
     */
    public Map<String, Object> login(LoginRequest req) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getStudentNo(), req.getPassword()));
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof LoginUser user)) {
            throw new BizException(401, "登录失败：用户信息异常");
        }
        String token = jwtUtil.generate(user.getId(), user.getStudentNo(), user.getRole());
        redisTemplate.opsForValue().set("auth:role:" + user.getId(), user.getRole(), Duration.ofHours(24));
        redisTemplate.opsForValue().set("online:user:" + user.getId(), "1", Duration.ofHours(2));
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("role", user.getRole());
        result.put("realName", user.getRealName());
        result.put("studentNo", user.getStudentNo());
        result.put("userId", user.getId());
        return result;
    }

    /**
     * 教务系统 OAuth2 简化模拟：用授权码（EDU_{学号}）换取登录态。
     *
     * @param code  授权码
     * @param state 授权校验串
     * @return 含 token、role 与授权信息的登录结果
     * @throws BizException 授权码为空或学号未建档时抛出
     */
    public Map<String, Object> oauthEduCallback(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new BizException("教务授权码无效");
        }
        String studentNo = code.startsWith("EDU_") ? code.substring(4) : code;
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentNo, studentNo));
        if (user == null) {
            throw new BizException("教务同步失败：学号不存在，请先完成本地建档");
        }
        String token = jwtUtil.generate(user.getId(), user.getStudentNo(), user.getRole());
        redisTemplate.opsForValue().set("auth:role:" + user.getId(), user.getRole(), Duration.ofHours(24));
        redisTemplate.opsForValue().set("online:user:" + user.getId(), "1", Duration.ofHours(2));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("role", user.getRole());
        result.put("realName", user.getRealName());
        result.put("studentNo", user.getStudentNo());
        result.put("userId", user.getId());
        result.put("oauthProvider", "CAMPUS_EDU_MOCK");
        result.put("state", state);
        result.put("syncedAt", LocalDateTime.now().toString());
        return result;
    }

    /**
     * 生成教务授权地址与随机 state（模拟）。
     *
     * @param redirectUri 授权后跳回地址，可为空
     * @return 含 authorizeUrl、state 与提示信息的授权信息
     */
    public Map<String, Object> oauthAuthorizeUrl(String redirectUri) {
        String state = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authorizeUrl", "/api/auth/oauth/edu/callback?code=EDU_2021001001&state=" + state
                + (redirectUri == null ? "" : "&redirectUri=" + redirectUri));
        result.put("state", state);
        result.put("hint", "模拟教务授权：回调 code=EDU_{学号}");
        return result;
    }

    /**
     * 查询当前登录用户的资料，聚合本周消费与今日营养达标率。
     *
     * @return 用户资料视图对象
     * @throws BizException 用户不存在时抛出
     */
    public UserProfileVO profile() {
        Long uid = SecurityUtils.currentUserId();
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<Order> weekOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, uid)
                .ge(Order::getCreatedAt, weekStart)
                .notIn(Order::getStatus, "CANCELLED", "CREATED"));
        BigDecimal weekSpend = weekOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NutritionDaily today = nutritionDailyMapper.selectOne(new LambdaQueryWrapper<NutritionDaily>()
                .eq(NutritionDaily::getUserId, uid)
                .eq(NutritionDaily::getStatDate, LocalDate.now()));

        HealthProfile healthProfile = healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, uid));

        List<String> allergies = List.of();
        Map<String, Object> health = new HashMap<>();
        if (healthProfile != null) {
            allergies = readJsonList(healthProfile.getAllergyJson());
            health.put("targetCalorie", healthProfile.getTargetCalorie());
            health.put("targetProtein", healthProfile.getTargetProtein());
            health.put("religionTag", healthProfile.getReligionTag());
            health.put("dietTaboo", readJsonList(healthProfile.getDietTabooJson()));
        }

        return UserProfileVO.builder()
                .id(user.getId())
                .studentNo(user.getStudentNo())
                .realName(user.getRealName())
                .phoneMasked(MaskUtil.maskPhone(user.getPhone()))
                .role(user.getRole())
                .department(user.getDepartment())
                .grade(user.getGrade())
                .balance(user.getBalance())
                .weekSpend(weekSpend)
                .nutritionRate(today == null ? BigDecimal.ZERO : today.getProteinRate())
                .allergies(allergies)
                .health(health)
                .build();
    }

    /**
     * 更新当前用户的健康档案，未建档时自动创建。
     *
     * @param req 健康档案更新请求
     * @return 更新后的用户资料
     * @throws BizException 健康档案序列化失败时抛出
     */
    @Transactional
    public UserProfileVO updateHealth(HealthUpdateRequest req) {
        Long uid = SecurityUtils.currentUserId();
        HealthProfile healthProfile = healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, uid));
        boolean isInsert = false;
        if (healthProfile == null) {
            healthProfile = new HealthProfile();
            healthProfile.setUserId(uid);
            isInsert = true;
        }
        try {
            if (req.getAllergies() != null) {
                healthProfile.setAllergyJson(objectMapper.writeValueAsString(req.getAllergies()));
            }
            if (req.getDietTaboo() != null) {
                healthProfile.setDietTabooJson(objectMapper.writeValueAsString(req.getDietTaboo()));
            }
        } catch (JsonProcessingException e) {
            throw new BizException("健康档案序列化失败");
        }
        if (req.getTargetCalorie() != null) {
            healthProfile.setTargetCalorie(req.getTargetCalorie());
        }
        if (req.getTargetProtein() != null) {
            healthProfile.setTargetProtein(req.getTargetProtein());
        }
        if (req.getReligionTag() != null) {
            healthProfile.setReligionTag(req.getReligionTag());
        }
        if (isInsert) {
            healthProfileMapper.insert(healthProfile);
        } else {
            healthProfileMapper.updateById(healthProfile);
        }
        return profile();
    }

    /**
     * 校园卡账户充值，并写入账户流水。
     *
     * @param req 充值请求
     * @return 含最新余额、充值金额与流水号的充值结果
     * @throws BizException 用户不存在时抛出
     */
    @Transactional
    public Map<String, Object> recharge(RechargeRequest req) {
        Long uid = SecurityUtils.currentUserId();
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setBalance(user.getBalance().add(req.getAmount()));
        userMapper.updateById(user);
        BalanceLedger ledger = new BalanceLedger();
        ledger.setUserId(uid);
        ledger.setChangeAmt(req.getAmount());
        ledger.setBalanceAfter(user.getBalance());
        ledger.setBizType("RECHARGE");
        ledger.setBizNo("R" + System.currentTimeMillis());
        ledger.setRemark("充值-" + req.getChannel());
        ledger.setCreatedAt(LocalDateTime.now());
        balanceLedgerMapper.insert(ledger);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", user.getBalance());
        result.put("changeAmt", req.getAmount());
        result.put("bizNo", ledger.getBizNo());
        return result;
    }

    /**
     * 查询当前用户的账户流水。
     *
     * @param limit 返回条数上限，实际限制在 1~100 之间
     * @return 按时间倒序的账户流水列表
     */
    public List<BalanceLedger> ledger(int limit) {
        Long uid = SecurityUtils.currentUserId();
        return balanceLedgerMapper.selectList(new LambdaQueryWrapper<BalanceLedger>()
                .eq(BalanceLedger::getUserId, uid)
                .orderByDesc(BalanceLedger::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
    }

    private List<String> readJsonList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
