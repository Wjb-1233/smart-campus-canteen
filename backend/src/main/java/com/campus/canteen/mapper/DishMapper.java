package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    List<Dish> search(@Param("keyword") String keyword,
                      @Param("mealPeriod") String mealPeriod,
                      @Param("tagCodes") List<String> tagCodes,
                      @Param("tagCount") int tagCount);

    Dish findSubstitute(@Param("excludeId") Long excludeId, @Param("mealPeriod") String mealPeriod);
}
