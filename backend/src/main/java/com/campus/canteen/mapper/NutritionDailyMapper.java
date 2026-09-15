package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.NutritionDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface NutritionDailyMapper extends BaseMapper<NutritionDaily> {

    @Select("""
            SELECT u.department AS department,
                   ROUND(AVG(IFNULL(n.protein_rate, 0)), 2) AS avgProteinRate
            FROM t_user u
            LEFT JOIN t_nutrition_daily n ON u.id = n.user_id AND n.stat_date = CURDATE()
            WHERE u.role = 'STUDENT' AND u.department IS NOT NULL
            GROUP BY u.department
            ORDER BY avgProteinRate ASC
            """)
    List<Map<String, Object>> deptProteinRateToday();

    @Select("""
            SELECT ranked.grade AS className,
                   ranked.real_name AS realName,
                   ranked.student_no AS studentNo,
                   ranked.protein_rate AS proteinRate,
                   ranked.rk AS rankNo
            FROM (
              SELECT u.grade,
                     u.real_name,
                     u.student_no,
                     IFNULL(n.protein_rate, 0) AS protein_rate,
                     ROW_NUMBER() OVER (PARTITION BY u.grade ORDER BY IFNULL(n.protein_rate, 0) DESC) AS rk
              FROM t_user u
              LEFT JOIN t_nutrition_daily n ON u.id = n.user_id AND n.stat_date = CURDATE()
              WHERE u.role = 'STUDENT' AND u.grade IS NOT NULL
            ) ranked
            WHERE ranked.rk <= 5
            ORDER BY ranked.grade, ranked.rk
            """)
    List<Map<String, Object>> classNutritionTop();

    @Select("""
            SELECT s.id AS stallId,
                   s.name AS stallName,
                   ROUND(AVG(CAST(JSON_EXTRACT(d.nutrition_json, '$.protein') AS DECIMAL(10,2))), 2) AS avgProtein,
                   ROUND(AVG(CAST(JSON_EXTRACT(d.nutrition_json, '$.calorie') AS DECIMAL(10,2))), 2) AS avgCalorie,
                   ROUND(
                     LEAST(100, GREATEST(0,
                       100 - ABS(AVG(CAST(JSON_EXTRACT(d.nutrition_json, '$.protein') AS DECIMAL(10,2))) - 20) * 2
                           - ABS(AVG(CAST(JSON_EXTRACT(d.nutrition_json, '$.calorie') AS DECIMAL(10,2))) - 450) / 10
                     )), 2
                   ) AS balanceScore
            FROM t_stall s
            INNER JOIN t_dish d ON d.stall_id = s.id AND d.status = 1
            GROUP BY s.id, s.name
            ORDER BY balanceScore DESC
            """)
    List<Map<String, Object>> stallNutritionScores();
}
