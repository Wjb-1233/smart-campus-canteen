package com.campus.canteen;

import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.HealthProfile;
import com.campus.canteen.mapper.HealthProfileMapper;
import com.campus.canteen.mapper.NutritionTagMapper;
import com.campus.canteen.service.DietGuardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DietGuardServiceTest {
    @Mock HealthProfileMapper healthProfileMapper;
    @Mock NutritionTagMapper nutritionTagMapper;
    DietGuardService dietGuardService;

    @BeforeEach
    void setUp() {
        dietGuardService = new DietGuardService(healthProfileMapper, nutritionTagMapper, new ObjectMapper());
    }

    @Test
    void blocksAllergyMatch() {
        HealthProfile profile = new HealthProfile();
        profile.setUserId(1L);
        profile.setAllergyJson("[\"花生\"]");
        when(healthProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        Dish dish = new Dish();
        dish.setId(9L);
        dish.setName("花生酱拌面");
        dish.setNutritionJson("{\"allergy\":[\"花生\"],\"calorie\":500}");

        assertFalse(dietGuardService.isDishAllowed(1L, dish));
        assertThrows(RuntimeException.class, () -> dietGuardService.assertDishAllowed(1L, dish));
    }

    @Test
    void allowsSafeDish() {
        HealthProfile profile = new HealthProfile();
        profile.setUserId(1L);
        profile.setAllergyJson("[\"海鲜\"]");
        when(healthProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        Dish dish = new Dish();
        dish.setId(1L);
        dish.setName("番茄炒蛋");
        dish.setNutritionJson("{\"calorie\":300,\"protein\":12}");

        assertTrue(dietGuardService.isDishAllowed(1L, dish));
    }
}
