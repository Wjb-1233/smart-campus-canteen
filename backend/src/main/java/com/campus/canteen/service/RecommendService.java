/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.HealthProfile;
import com.campus.canteen.entity.Order;
import com.campus.canteen.entity.OrderItem;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.HealthProfileMapper;
import com.campus.canteen.mapper.OrderItemMapper;
import com.campus.canteen.mapper.OrderMapper;
import com.campus.canteen.security.SecurityUtils;
import com.campus.canteen.vo.DishVO;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜品推荐业务：结合健康档案、时段与热度做混合推荐。
 *
 * @since 2026-09-15
 */
@Service
@RequiredArgsConstructor
public class RecommendService {
    /** 清真档口 ID，命中的菜品在清真饮食用户的推荐中加分。 */
    private static final long HALAL_STALL_ID = 6L;

    private final DishMapper dishMapper;
    private final HealthProfileMapper healthProfileMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishService dishService;
    private final DietGuardService dietGuardService;

    /**
     * 生成个性化推荐菜品列表：先按健康档案过滤，再按偏好与热度打分排序。
     *
     * @param mealPeriod 供餐时段，可为空表示不限
     * @param limit      返回条数上限
     * @return 带推荐理由的菜品列表
     */
    public List<?> recommend(String mealPeriod, int limit) {
        Long userId = SecurityUtils.currentUserId();
        HealthProfile profile = healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId));

        List<Dish> candidates = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, 1)
                .gt(Dish::getStock, 0)
                .eq(mealPeriod != null && !mealPeriod.isBlank(), Dish::getMealPeriod, mealPeriod))
                .stream()
                .filter(d -> dietGuardService.isDishAllowed(userId, d))
                .toList();

        Set<Long> preferredDishIds = preferredDishes(userId);
        Map<Long, Double> score = new HashMap<>();
        for (Dish d : candidates) {
            double s = d.getHeatScore() == null ? 0 : d.getHeatScore();
            if (preferredDishIds.contains(d.getId())) {
                s += 30;
            }
            if (isHalalStall(profile, d)) {
                s += 20;
            }
            score.put(d.getId(), s);
        }

        Comparator<Dish> byScoreDesc = (a, b) -> Double.compare(
                score.getOrDefault(b.getId(), 0.0), score.getOrDefault(a.getId(), 0.0));
        return candidates.stream()
                .sorted(byScoreDesc)
                .limit(limit)
                .map(d -> {
                    DishVO vo = dishService.detail(d.getId());
                    boolean isPreferred = preferredDishIds.contains(d.getId());
                    vo.setReason(buildReason(d, profile, isPreferred, score.getOrDefault(d.getId(), 0.0)));
                    return vo;
                })
                .toList();
    }

    /**
     * 生成可解释的推荐理由：命中历史偏好 / 宗教饮食规则 / 营养目标 / 热度四个维度。
     *
     * @param dish        菜品
     * @param profile     用户健康档案，可为 null
     * @param isPreferred 是否为用户最近点过的菜品
     * @param score       推荐得分
     * @return 以「·」连接的推荐理由，最多 3 条
     */
    private String buildReason(Dish dish, HealthProfile profile, boolean isPreferred, double score) {
        List<String> reasons = new ArrayList<>();
        if (isPreferred) {
            reasons.add("你最近点过");
        }
        if (isHalalStall(profile, dish)) {
            reasons.add("符合清真饮食");
        }
        int protein = nutritionInt(dish, "protein");
        String proteinReason = buildProteinReason(profile, protein);
        if (proteinReason != null) {
            reasons.add(proteinReason);
        }
        int calorie = nutritionInt(dish, "calorie");
        boolean isCalorieFit = profile != null && profile.getTargetCalorie() != null
                && calorie > 0 && calorie <= profile.getTargetCalorie() / 3;
        if (isCalorieFit) {
            reasons.add("热量适配你的日目标");
        }
        if (reasons.isEmpty()) {
            reasons.add(score >= 60 ? "档口热销" : "营养均衡");
        }
        return String.join(" · ", reasons.subList(0, Math.min(reasons.size(), 3)));
    }

    private String buildProteinReason(HealthProfile profile, int protein) {
        if (profile != null && profile.getTargetProtein() != null && protein >= profile.getTargetProtein() / 3) {
            return "补足今日蛋白质目标";
        }
        if (protein >= 25) {
            return "高蛋白";
        }
        return null;
    }

    private boolean isHalalStall(HealthProfile profile, Dish dish) {
        boolean isHalalProfile = profile != null && "HALAL".equalsIgnoreCase(profile.getReligionTag());
        return isHalalProfile && Objects.equals(dish.getStallId(), HALAL_STALL_ID);
    }

    private int nutritionInt(Dish dish, String key) {
        Object v = dishService.nutritionOf(dish).get(key);
        if (v == null) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(v)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Set<Long> preferredDishes(Long userId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .notIn(Order::getStatus, "CANCELLED", "CREATED")
                .last("LIMIT 50"));
        if (orders.isEmpty()) {
            return Set.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream().map(OrderItem::getDishId).collect(Collectors.toSet());
    }
}
