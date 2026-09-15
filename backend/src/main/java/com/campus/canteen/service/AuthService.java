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

    public Map<String, Object> login(LoginRequest req) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getStudentNo(), req.getPassword()));
        LoginUser user = (LoginUser) authentication.getPrincipal();
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

    /** 教务系统 OAuth2 简化模拟：用学号换取登录态 */
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

    public Map<String, Object> oauthAuthorizeUrl(String redirectUri) {
        String state = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authorizeUrl", "/api/auth/oauth/edu/callback?code=EDU_2021001001&state=" + state
                + (redirectUri == null ? "" : "&redirectUri=" + redirectUri));
        result.put("state", state);
        result.put("hint", "模拟教务授权：回调 code=EDU_{学号}");
        return result;
    }

    public UserProfileVO profile() {
        Long uid = SecurityUtils.currentUserId();
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, uid)
                .ge(Order::getCreatedAt, weekStart)
                .notIn(Order::getStatus, "CANCELLED", "CREATED"));
        BigDecimal weekSpend = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NutritionDaily today = nutritionDailyMapper.selectOne(new LambdaQueryWrapper<NutritionDaily>()
                .eq(NutritionDaily::getUserId, uid)
                .eq(NutritionDaily::getStatDate, LocalDate.now()));

        HealthProfile profile = healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, uid));

        List<String> allergies = List.of();
        Map<String, Object> health = new HashMap<>();
        if (profile != null) {
            allergies = readJsonList(profile.getAllergyJson());
            health.put("targetCalorie", profile.getTargetCalorie());
            health.put("targetProtein", profile.getTargetProtein());
            health.put("religionTag", profile.getReligionTag());
            health.put("dietTaboo", readJsonList(profile.getDietTabooJson()));
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

    @Transactional
    public UserProfileVO updateHealth(HealthUpdateRequest req) {
        Long uid = SecurityUtils.currentUserId();
        HealthProfile profile = healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, uid));
        boolean insert = false;
        if (profile == null) {
            profile = new HealthProfile();
            profile.setUserId(uid);
            insert = true;
        }
        try {
            if (req.getAllergies() != null) {
                profile.setAllergyJson(objectMapper.writeValueAsString(req.getAllergies()));
            }
            if (req.getDietTaboo() != null) {
                profile.setDietTabooJson(objectMapper.writeValueAsString(req.getDietTaboo()));
            }
        } catch (Exception e) {
            throw new BizException("健康档案序列化失败");
        }
        if (req.getTargetCalorie() != null) profile.setTargetCalorie(req.getTargetCalorie());
        if (req.getTargetProtein() != null) profile.setTargetProtein(req.getTargetProtein());
        if (req.getReligionTag() != null) profile.setReligionTag(req.getReligionTag());
        if (insert) {
            healthProfileMapper.insert(profile);
        } else {
            healthProfileMapper.updateById(profile);
        }
        return profile();
    }

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
        } catch (Exception e) {
            return List.of();
        }
    }
}
