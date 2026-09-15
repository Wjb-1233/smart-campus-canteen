package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.HealthProfile;
import com.campus.canteen.entity.NutritionTag;
import com.campus.canteen.mapper.HealthProfileMapper;
import com.campus.canteen.mapper.NutritionTagMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 健康档案硬拦截：过敏原、饮食禁忌、清真约束
 */
@Service
@RequiredArgsConstructor
public class DietGuardService {
    private final HealthProfileMapper healthProfileMapper;
    private final NutritionTagMapper tagMapper;
    private final ObjectMapper objectMapper;

    public HealthProfile profileOf(Long userId) {
        return healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId));
    }

    public void assertDishAllowed(Long userId, Dish dish) {
        HealthProfile profile = profileOf(userId);
        if (profile == null || dish == null) {
            return;
        }
        Set<String> allergies = toLowerSet(readList(profile.getAllergyJson()));
        Set<String> taboos = toLowerSet(readList(profile.getDietTabooJson()));
        String name = dish.getName() == null ? "" : dish.getName().toLowerCase(Locale.ROOT);
        String nutrition = dish.getNutritionJson() == null ? "" : dish.getNutritionJson().toLowerCase(Locale.ROOT);

        for (String a : allergies) {
            if (StringUtils.hasText(a) && (name.contains(a) || nutrition.contains(a))) {
                throw new BizException("健康档案拦截：菜品含过敏原「" + a + "」—" + dish.getName());
            }
        }
        for (String t : taboos) {
            if (!StringUtils.hasText(t)) continue;
            if (name.contains(t) || nutrition.contains(t)) {
                throw new BizException("健康档案拦截：触犯饮食禁忌「" + t + "」—" + dish.getName());
            }
            // 常见映射：猪肉 <-> 非清真
            if (("猪肉".equals(t) || "pork".equals(t)) && name.contains("猪")) {
                throw new BizException("健康档案拦截：触犯饮食禁忌「猪肉」—" + dish.getName());
            }
        }
        if ("HALAL".equalsIgnoreCase(profile.getReligionTag())) {
            List<NutritionTag> tags = tagMapper.listByDishId(dish.getId());
            boolean halal = tags.stream().anyMatch(x -> "HALAL".equalsIgnoreCase(x.getCode()));
            boolean porkLike = name.contains("猪") || name.contains("宫保") || name.contains("回锅");
            if (!halal && porkLike) {
                throw new BizException("健康档案拦截：非清真菜品不可下单—" + dish.getName());
            }
            if (!halal && tags.stream().noneMatch(x -> "VEGETARIAN".equalsIgnoreCase(x.getCode()))) {
                // 软约束：无清真标签时，若明确猪肉相关已拦；其余放行但清真用户优先已在推荐侧处理
            }
        }
    }

    public boolean isDishAllowed(Long userId, Dish dish) {
        try {
            assertDishAllowed(userId, dish);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    private List<String> readList(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Set<String> toLowerSet(List<String> list) {
        Set<String> set = new HashSet<>();
        for (String s : list) {
            if (s != null) set.add(s.toLowerCase(Locale.ROOT).trim());
        }
        return set;
    }
}
