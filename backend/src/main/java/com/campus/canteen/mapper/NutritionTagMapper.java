package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.NutritionTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NutritionTagMapper extends BaseMapper<NutritionTag> {

    @Select("""
            SELECT t.* FROM t_nutrition_tag t
            INNER JOIN t_dish_tag dt ON t.id = dt.tag_id
            WHERE dt.dish_id = #{dishId}
            """)
    List<NutritionTag> listByDishId(@Param("dishId") Long dishId);
}
