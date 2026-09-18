/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.WasteRecord;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 浪费溯源数据访问层。
 *
 * @since 2026-09-15
 */
@Mapper
public interface WasteRecordMapper extends BaseMapper<WasteRecord> {
    /**
     * 统计近 7 天的平均剩饭浪费率。
     *
     * @return 浪费率百分比，无数据时返回 0
     */
    @Select("""
            SELECT ROUND(IFNULL(AVG(waste_ratio),0)*100, 2) AS wasteRate
            FROM t_waste_record
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
            """)
    Double avgWasteRateLast7Days();

    /**
     * 查询近 7 天每天的剩饭浪费率，用于趋势曲线展示。
     *
     * @return 日期与浪费率的列表
     */
    @Select("""
            SELECT DATE(created_at) AS dayLabel,
                   ROUND(IFNULL(AVG(waste_ratio),0)*100, 2) AS rate
            FROM t_waste_record
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(created_at)
            ORDER BY dayLabel
            """)
    List<Map<String, Object>> wasteCurveLast7Days();

    /**
     * 统计上一个 7 天的平均浪费率，与近 7 天对比得到环比变化。
     *
     * @return 浪费率百分比，无数据时返回 0
     */
    @Select("""
            SELECT ROUND(IFNULL(AVG(waste_ratio),0)*100, 2) AS rate
            FROM t_waste_record
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY)
              AND created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)
            """)
    Double avgWasteRatePrev7Days();
}
