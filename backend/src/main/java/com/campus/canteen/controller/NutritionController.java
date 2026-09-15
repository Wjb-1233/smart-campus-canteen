package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.dto.DishNutritionUpdateRequest;
import com.campus.canteen.service.NutritionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 营养师端专属接口。
 * <p>与 {@code AnalyticsController} 中的统计分析（只读报表）互补，
 * 这里提供「菜品营养数据核验」这一营养师职责范围内的写能力，
 * 保证学生端展示的热量 / 蛋白质数据经过专业核验。</p>
 */
@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
public class NutritionController {
    private final NutritionService nutritionService;

    /** 菜品营养数据核验清单：管理员端营养与核验工作台使用。 */
    @GetMapping("/dish-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> dishAudit() {
        return Result.ok(nutritionService.dishNutritionAudit());
    }

    /** 核验通过某菜品的营养数据。 */
    @PostMapping("/dish-audit/{dishId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> verify(@PathVariable("dishId") Long dishId) {
        return Result.ok(nutritionService.verifyDishNutrition(dishId, true));
    }

    /** 撤销核验（退回待核验）。 */
    @PostMapping("/dish-audit/{dishId}/unverify")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> unverify(@PathVariable("dishId") Long dishId) {
        return Result.ok(nutritionService.verifyDishNutrition(dishId, false));
    }

    /** 修正菜品营养数据（修正后需重新核验）。 */
    @PostMapping("/dish-audit/{dishId}/nutrition")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> updateNutrition(@PathVariable("dishId") Long dishId,
                                                       @Valid @RequestBody DishNutritionUpdateRequest request) {
        return Result.ok(nutritionService.updateDishNutrition(dishId, request));
    }
}
