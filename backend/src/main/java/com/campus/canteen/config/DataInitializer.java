/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.User;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.UserMapper;
import com.campus.canteen.service.DishService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 启动自愈：把历史数据收敛到「学生/老师 · 食堂 · 管理员」三端，并保证演示账号可用。
 * 老库（5 角色：STUDENT/TEACHER/CANTEEN_ADMIN/NUTRITIONIST/LOGISTICS）无需手工跑 SQL 也能直接跑起来。
 *
 * @since 2026-09-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_STALL = "STALL";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String DEFAULT_PASSWORD = "123456";

    /** 历史角色 → 三端角色：教师并入学生/老师端；营养师、后勤主管并入管理员端；食堂管理员转为食堂端 */
    private static final Map<String, String> LEGACY_ROLE_MAPPING = Map.of(
            "TEACHER", ROLE_STUDENT,
            "CANTEEN_ADMIN", ROLE_STALL,
            "NUTRITIONIST", ROLE_ADMIN,
            "LOGISTICS", ROLE_ADMIN
    );

    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final DishService dishService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            normalizeDemoAccounts();
            int migrated = normalizeLegacyRoles();
            if (migrated > 0) {
                log.info("Migrated {} legacy user roles to the three-end model", migrated);
            }
            int created = ensureDemoAccounts();
            if (created > 0) {
                log.info("Created {} missing demo accounts", created);
            }

            List<User> users = userMapper.selectList(null);
            for (User user : users) {
                user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
                userMapper.updateById(user);
            }
            log.info("Demo passwords reset to {} for {} users", DEFAULT_PASSWORD, users.size());

            List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>().eq(Dish::getStatus, 1));
            for (Dish dish : dishes) {
                dishService.syncStockToRedis(dish.getId(), dish.getStock());
            }
            log.info("Synced {} dish stocks to Redis", dishes.size());
        } catch (DataAccessException e) {
            // 数据库或 Redis 未就绪时跳过初始化，不影响应用启动
            log.warn("DataInitializer skipped (check MySQL/Redis): {}", e.getMessage());
        }
    }

    /**
     * 老库里出餐/管理员主账号是 A10001（食堂管理员）与 L10001（后勤主管），
     * 三端模型里约定为 S10001（食堂）与 A10001（管理员），这里做一次账号规整。
     */
    private void normalizeDemoAccounts() {
        User stall = findByStudentNo("A10001");
        if (stall != null && LEGACY_ROLE_MAPPING.containsKey(stall.getRole())
                && findByStudentNo("S10001") == null) {
            stall.setStudentNo("S10001");
            stall.setRealName("王师傅");
            stall.setDepartment("第一食堂");
            userMapper.updateById(stall);
            log.info("Renamed legacy canteen-admin account A10001 -> S10001");
        }

        User admin = findByStudentNo("L10001");
        if (admin != null && findByStudentNo("A10001") == null) {
            admin.setStudentNo("A10001");
            admin.setRealName("王管理员");
            admin.setDepartment("后勤处");
            userMapper.updateById(admin);
            log.info("Renamed legacy logistics account L10001 -> A10001");
        }
    }

    /** 把 TEACHER / CANTEEN_ADMIN / NUTRITIONIST / LOGISTICS 统一映射为三端角色 */
    private int normalizeLegacyRoles() {
        int migrated = 0;
        for (User user : userMapper.selectList(null)) {
            String mapped = LEGACY_ROLE_MAPPING.get(user.getRole());
            if (mapped != null) {
                user.setRole(mapped);
                userMapper.updateById(user);
                migrated++;
            }
        }
        return migrated;
    }

    /** 保证三端演示账号一定存在，避免「点某个端提示用户名或密码错误」 */
    private int ensureDemoAccounts() {
        int created = 0;
        created += ensureAccount("2021001001", ROLE_STUDENT, "张三", "计算机学院", "2021", new BigDecimal("200.00"));
        created += ensureAccount("S10001", ROLE_STALL, "王师傅", "第一食堂", null, BigDecimal.ZERO);
        created += ensureAccount("A10001", ROLE_ADMIN, "王管理员", "后勤处", null, BigDecimal.ZERO);
        return created;
    }

    private int ensureAccount(String studentNo, String role, String realName,
                              String department, String grade, BigDecimal balance) {
        if (findByStudentNo(studentNo) != null) {
            return 0;
        }
        User user = new User();
        user.setStudentNo(studentNo);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(realName);
        user.setRole(role);
        user.setDepartment(department);
        user.setGrade(grade);
        user.setBalance(balance);
        user.setStatus(1);
        userMapper.insert(user);
        return 1;
    }

    private User findByStudentNo(String studentNo) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentNo, studentNo));
    }
}
