/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.entity.Order;
import com.campus.canteen.entity.Stall;
import com.campus.canteen.mapper.OrderMapper;
import com.campus.canteen.mapper.StallMapper;
import com.campus.canteen.mapper.WasteRecordMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营数据看板业务：交易、热销、在线人数与浪费概览。
 *
 * @since 2026-09-15
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final OrderMapper orderMapper;
    private final StallMapper stallMapper;
    private final WasteRecordMapper wasteRecordMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 汇总运营数据看板所需的全部指标。
     *
     * @param date      统计日期，为空时取当天
     * @param canteenId 食堂 ID，可为空
     * @param stallId   档口 ID，可为空
     * @return 在线人数、待取餐订单、交易趋势、热销菜品与浪费概览
     */
    public Map<String, Object> overview(LocalDate date, Long canteenId, Long stallId) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<Stall> stalls = selectStalls(canteenId, stallId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("onlineUsers", countOnlineUsers());
        result.put("pendingOrders", countPendingPickup(day, stalls, canteenId, stallId));
        result.put("orderTrend24h", orderMapper.orderCountLast24h());

        List<Map<String, Object>> topDishes = orderMapper.topDishes();
        result.put("topDishes", topDishes.size() > 5 ? topDishes.subList(0, 5) : topDishes);
        result.put("topDishes10", topDishes);

        result.put("stallQueue", buildStallQueue(stalls));
        result.putAll(buildWasteOverview());
        result.put("filterDate", day.toString());
        result.put("filterCanteenId", canteenId);
        result.put("filterStallId", stallId);
        return result;
    }

    private List<Stall> selectStalls(Long canteenId, Long stallId) {
        LambdaQueryWrapper<Stall> query = new LambdaQueryWrapper<Stall>().eq(Stall::getStatus, 1);
        if (canteenId != null) {
            query.eq(Stall::getCanteenId, canteenId);
        }
        if (stallId != null) {
            query.eq(Stall::getId, stallId);
        }
        return stallMapper.selectList(query);
    }

    private long countPendingPickup(LocalDate day, List<Stall> stalls, Long canteenId, Long stallId) {
        Set<Long> stallIds = stalls.stream().map(Stall::getId).collect(Collectors.toSet());
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, "PAID", "PREPARING", "READY")
                .ge(Order::getCreatedAt, day.atStartOfDay())
                .lt(Order::getCreatedAt, day.plusDays(1).atStartOfDay());
        if (!stallIds.isEmpty()) {
            query.in(Order::getStallId, stallIds);
        } else if (stallId != null || canteenId != null) {
            query.eq(Order::getStallId, -1L);
        }
        return orderMapper.selectCount(query);
    }

    private List<Map<String, Object>> buildStallQueue(List<Stall> stalls) {
        return stalls.stream().map(stall -> {
            long pending = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getStallId, stall.getId())
                    .in(Order::getStatus, "PAID", "PREPARING"));
            Integer configuredQueue = stall.getQueueCount();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", stall.getName());
            row.put("value", pending > 0 ? pending : (configuredQueue == null ? 0L : configuredQueue));
            return row;
        }).toList();
    }

    private Map<String, Object> buildWasteOverview() {
        Double last7Rate = wasteRecordMapper.avgWasteRateLast7Days();
        Double prev7Rate = wasteRecordMapper.avgWasteRatePrev7Days();
        double current = last7Rate == null ? 0.0 : last7Rate;
        double previous = prev7Rate == null ? 0.0 : prev7Rate;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wasteRate", current);
        result.put("wasteYoYPercent", calcChangePercent(current, previous));
        result.put("wasteCurve", buildWasteCurve(wasteRecordMapper.wasteCurveLast7Days()));
        return result;
    }

    /** 环比变化率（%），保留 1 位小数；基数为 0 时返回 0。 */
    private double calcChangePercent(double current, double previous) {
        if (previous <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 把近 7 天浪费率补齐为连续 7 天的曲线数据。 */
    private List<Double> buildWasteCurve(List<Map<String, Object>> rows) {
        List<Double> curve = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String dayLabel = LocalDate.now().minusDays(i).toString();
            double hit = rows.stream()
                    .filter(row -> dayLabel.equals(String.valueOf(row.get("dayLabel"))))
                    .mapToDouble(this::extractRate)
                    .findFirst()
                    .orElse(0.0);
            curve.add(hit);
        }
        return curve;
    }

    private double extractRate(Map<String, Object> row) {
        Object raw = row.get("rate");
        return raw instanceof Number number ? number.doubleValue() : 0.0;
    }

    private long countOnlineUsers() {
        long online = 0L;
        try {
            Set<String> keys = redisTemplate.keys("online:user:*");
            online = keys == null ? 0L : (long) keys.size();
        } catch (DataAccessException e) {
            // Redis 不可用时降级为按最近下单量估算
            online = 0L;
        }
        if (online == 0) {
            online = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreatedAt, LocalDateTime.now().minusHours(1)));
        }
        return online;
    }
}
