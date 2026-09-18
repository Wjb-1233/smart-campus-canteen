/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.NutritionTag;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 营养标签字典数据访问层。
 *
 * @since 2026-09-15
 */
@Mapper
public interface NutritionTagMapper extends BaseMapper<NutritionTag> {
    /**
     * 查询某个菜品关联的全部标签。
     *
     * @param dishId 菜品 ID
     * @return 标签列表，无关联时返回空列表
     */
    @Select("""
            SELECT t.* FROM t_nutrition_tag t
            INNER JOIN t_dish_tag dt ON t.id = dt.tag_id
            WHERE dt.dish_id = #{dishId}
            """)
    List<NutritionTag> listByDishId(@Param("dishId") Long dishId);
}
