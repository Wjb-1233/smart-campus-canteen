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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendService {
    private final DishMapper dishMapper;
    private final HealthProfileMapper healthProfileMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishService dishService;
    private final DietGuardService dietGuardService;

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
            if (profile != null && "HALAL".equalsIgnoreCase(profile.getReligionTag())
                    && Objects.equals(d.getStallId(), 6L)) {
                s += 20;
            }
            score.put(d.getId(), s);
        }

        return candidates.stream()
                .sorted((a, b) -> Double.compare(score.getOrDefault(b.getId(), 0.0), score.getOrDefault(a.getId(), 0.0)))
                .limit(limit)
                .map(d -> {
                    DishVO vo = dishService.detail(d.getId());
                    vo.setReason(buildReason(d, profile, preferredDishIds.contains(d.getId()), score.getOrDefault(d.getId(), 0.0)));
                    return vo;
                })
                .toList();
    }

    /**
     * 生成可解释的推荐理由：命中历史偏好 / 宗教饮食规则 / 营养目标 / 热度四个维度。
     */
    private String buildReason(Dish dish, HealthProfile profile, boolean preferred, double score) {
        List<String> reasons = new ArrayList<>();
        if (preferred) {
            reasons.add("你最近点过");
        }
        if (profile != null && "HALAL".equalsIgnoreCase(profile.getReligionTag()) && Objects.equals(dish.getStallId(), 6L)) {
            reasons.add("符合清真饮食");
        }
        int protein = nutritionInt(dish, "protein");
        if (profile != null && profile.getTargetProtein() != null && protein >= profile.getTargetProtein() / 3) {
            reasons.add("补足今日蛋白质目标");
        } else if (protein >= 25) {
            reasons.add("高蛋白");
        }
        int calorie = nutritionInt(dish, "calorie");
        if (profile != null && profile.getTargetCalorie() != null && calorie > 0 && calorie <= profile.getTargetCalorie() / 3) {
            reasons.add("热量适配你的日目标");
        }
        if (reasons.isEmpty()) {
            reasons.add(score >= 60 ? "档口热销" : "营养均衡");
        }
        return String.join(" · ", reasons.subList(0, Math.min(reasons.size(), 3)));
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
