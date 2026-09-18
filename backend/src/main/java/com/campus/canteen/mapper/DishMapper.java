/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.Dish;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜品数据访问层。
 *
 * @since 2026-09-15
 */
@Mapper
public interface DishMapper extends BaseMapper<Dish> {
    /**
     * 按关键词、供餐时段与标签组合检索菜品。
     *
     * @param keyword    菜品名称关键词，可为空
     * @param mealPeriod 供餐时段，可为空
     * @param tagCodes   标签编码集合，可为空
     * @param tagCount   标签命中数量下限，配合标签集合实现「同时满足多个标签」
     * @return 命中的上架菜品列表
     */
    List<Dish> search(@Param("keyword") String keyword,
                      @Param("mealPeriod") String mealPeriod,
                      @Param("tagCodes") List<String> tagCodes,
                      @Param("tagCount") int tagCount);

    /**
     * 为库存不足的菜品查找同时段的替代菜品。
     *
     * @param excludeId  需要排除的菜品 ID，避免替代品就是原菜品
     * @param mealPeriod 供餐时段
     * @return 替代菜品，找不到时返回 null
     */
    Dish findSubstitute(@Param("excludeId") Long excludeId, @Param("mealPeriod") String mealPeriod);
}
