package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.WasteRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WasteRecordMapper extends BaseMapper<WasteRecord> {

    @Select("""
            SELECT ROUND(IFNULL(AVG(waste_ratio),0)*100, 2) AS wasteRate
            FROM t_waste_record
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
            """)
    Double avgWasteRateLast7Days();

    @Select("""
            SELECT DATE(created_at) AS dayLabel,
                   ROUND(IFNULL(AVG(waste_ratio),0)*100, 2) AS rate
            FROM t_waste_record
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(created_at)
            ORDER BY dayLabel
            """)
    List<Map<String, Object>> wasteCurveLast7Days();

    @Select("""
            SELECT ROUND(IFNULL(AVG(waste_ratio),0)*100, 2) AS rate
            FROM t_waste_record
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY)
              AND created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)
            """)
    Double avgWasteRatePrev7Days();
}
