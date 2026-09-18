/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.Order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 订单数据访问层，含运营看板用的聚合统计查询。
 *
 * @since 2026-09-15
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    /**
     * 统计近 24 小时每小时的下单量。
     *
     * @return 小时标签与订单数量的列表
     */
    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:00:00') AS hourLabel, COUNT(*) AS cnt
            FROM t_order
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            GROUP BY hourLabel
            ORDER BY hourLabel
            """)
    List<Map<String, Object>> orderCountLast24h();

    /**
     * 统计近 7 天销量前 10 的菜品。
     *
     * @return 菜品名称与销量合计的列表
     */
    @Select("""
            SELECT d.name AS name, SUM(oi.quantity) AS qty
            FROM t_order_item oi
            INNER JOIN t_order o ON oi.order_id = o.id
            INNER JOIN t_dish d ON oi.dish_id = d.id
            WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
              AND o.status NOT IN ('CANCELLED','CREATED')
            GROUP BY d.id, d.name
            ORDER BY qty DESC
            LIMIT 10
            """)
    List<Map<String, Object>> topDishes();
}
