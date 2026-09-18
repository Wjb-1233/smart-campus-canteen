/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.NutritionTag;
import com.campus.canteen.service.DishService;
import com.campus.canteen.service.RecommendService;
import com.campus.canteen.vo.DishVO;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 菜品接口：菜品查询、档口菜品维护、库存与标签管理。
 *
 * @since 2026-09-15
 */
@RestController
@RequestMapping("/dish")
@RequiredArgsConstructor
public class DishController {
    private final DishService dishService;
    private final RecommendService recommendService;

    /**
     * 查询供食堂端与管理员端使用的全量菜品列表。
     *
     * @return 菜品视图对象列表
     */
    @GetMapping("/admin/list")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<DishVO>> adminList() {
        return Result.ok(dishService.adminList());
    }

    /**
     * 按关键词、供餐时段与标签检索菜品。
     *
     * @param keyword    菜品名称关键词，可为空
     * @param mealPeriod 供餐时段，可为空
     * @param tags       逗号分隔的标签编码，可为空
     * @return 命中的菜品列表
     */
    @GetMapping("/search")
    public Result<List<DishVO>> search(@RequestParam(name = "keyword", required = false) String keyword,
                                       @RequestParam(name = "mealPeriod", required = false) String mealPeriod,
                                       @RequestParam(name = "tags", required = false) String tags) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return Result.ok(dishService.search(keyword, mealPeriod, tagList));
    }

    /**
     * 查询菜品详情。
     *
     * @param id 菜品 ID
     * @return 菜品视图对象
     */
    @GetMapping("/{id}")
    public Result<DishVO> detail(@PathVariable(name = "id") Long id) {
        return Result.ok(dishService.detail(id));
    }

    /**
     * 查询全部营养/健康标签，供筛选与菜品维护使用。
     *
     * @return 标签列表
     */
    @GetMapping("/tags")
    public Result<List<NutritionTag>> tags() {
        return Result.ok(dishService.allTags());
    }

    /**
     * 查询档口下拉选项。
     *
     * @return 含档口 ID 与名称的列表
     */
    @GetMapping("/stalls")
    public Result<List<Map<String, Object>>> stalls() {
        return Result.ok(dishService.stallOptions());
    }

    /**
     * 获取个性化推荐菜品。
     *
     * @param mealPeriod 供餐时段，可为空
     * @param limit      返回条数上限
     * @return 推荐菜品列表
     */
    @GetMapping("/recommend")
    public Result<List<?>> recommend(@RequestParam(name = "mealPeriod", required = false) String mealPeriod,
                                     @RequestParam(name = "limit", defaultValue = "6") int limit) {
        return Result.ok(recommendService.recommend(mealPeriod, limit));
    }

    /**
     * 查询库存低于阈值的菜品，用于缺货预警。
     *
     * @param threshold 库存阈值
     * @return 低库存菜品列表
     */
    @GetMapping("/stock/alerts")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<Map<String, Object>>> alerts(
            @RequestParam(name = "threshold", defaultValue = "10") int threshold) {
        return Result.ok(dishService.lowStockAlerts(threshold));
    }

    /**
     * 新增菜品。
     *
     * @param dish 菜品信息
     * @return 保存后的菜品
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Dish> save(@RequestBody Dish dish) {
        return Result.ok(dishService.saveOrUpdate(dish));
    }

    /**
     * 更新菜品。
     *
     * @param dish 菜品信息，必须携带 ID
     * @return 保存后的菜品
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Dish> update(@RequestBody Dish dish) {
        return Result.ok(dishService.saveOrUpdate(dish));
    }

    /**
     * 下架并删除菜品，同时释放其库存缓存。
     *
     * @param id 菜品 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Void> delete(@PathVariable(name = "id") Long id) {
        dishService.delete(id);
        return Result.ok();
    }
}
