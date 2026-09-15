package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.entity.Order;
import com.campus.canteen.entity.Stall;
import com.campus.canteen.mapper.OrderMapper;
import com.campus.canteen.mapper.StallMapper;
import com.campus.canteen.mapper.WasteRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final OrderMapper orderMapper;
    private final StallMapper stallMapper;
    private final WasteRecordMapper wasteRecordMapper;
    private final StringRedisTemplate redisTemplate;

    public Map<String, Object> overview(LocalDate date, Long canteenId, Long stallId) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        List<Map<String, Object>> orderTrend = orderMapper.orderCountLast24h();
        List<Map<String, Object>> topDishes = orderMapper.topDishes();

        LambdaQueryWrapper<Stall> stallQw = new LambdaQueryWrapper<Stall>().eq(Stall::getStatus, 1);
        if (canteenId != null) {
            stallQw.eq(Stall::getCanteenId, canteenId);
        }
        if (stallId != null) {
            stallQw.eq(Stall::getId, stallId);
        }
        List<Stall> stalls = stallMapper.selectList(stallQw);
        Set<Long> stallIds = new HashSet<>();
        stalls.forEach(s -> stallIds.add(s.getId()));

        LambdaQueryWrapper<Order> pendingQw = new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, "PAID", "PREPARING", "READY")
                .ge(Order::getCreatedAt, start)
                .lt(Order::getCreatedAt, end);
        if (!stallIds.isEmpty()) {
            pendingQw.in(Order::getStallId, stallIds);
        } else if (stallId != null || canteenId != null) {
            pendingQw.eq(Order::getStallId, -1L);
        }
        long pendingPickup = orderMapper.selectCount(pendingQw);

        List<Map<String, Object>> queueRing = stalls.stream().map(s -> {
            long q = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getStallId, s.getId())
                    .in(Order::getStatus, "PAID", "PREPARING"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getName());
            m.put("value", q > 0 ? q : s.getQueueCount());
            return m;
        }).toList();

        Double wasteRate = wasteRecordMapper.avgWasteRateLast7Days();
        Double prev = wasteRecordMapper.avgWasteRatePrev7Days();
        if (wasteRate == null) wasteRate = 0.0;
        if (prev == null) prev = 0.0;
        double yoy = prev <= 0 ? 0 : Math.round((wasteRate - prev) / prev * 1000.0) / 10.0;

        List<Map<String, Object>> curveRows = wasteRecordMapper.wasteCurveLast7Days();
        List<Double> wasteCurve = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = java.time.LocalDate.now().minusDays(i);
            Double hit = curveRows.stream()
                    .filter(r -> d.toString().equals(String.valueOf(r.get("dayLabel"))))
                    .map(r -> ((Number) r.get("rate")).doubleValue())
                    .findFirst().orElse(0.0);
            wasteCurve.add(hit);
        }

        Long online = 0L;
        try {
            Set<String> keys = redisTemplate.keys("online:user:*");
            online = keys == null ? 0L : (long) keys.size();
        } catch (Exception ignored) {
        }
        if (online == 0) {
            online = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreatedAt, java.time.LocalDateTime.now().minusHours(1)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("onlineUsers", online);
        result.put("pendingOrders", pendingPickup);
        result.put("orderTrend24h", orderTrend);
        result.put("topDishes", topDishes.size() > 5 ? topDishes.subList(0, 5) : topDishes);
        result.put("topDishes10", topDishes);
        result.put("stallQueue", queueRing);
        result.put("wasteRate", wasteRate);
        result.put("wasteYoYPercent", yoy);
        result.put("wasteCurve", wasteCurve);
        result.put("filterDate", day.toString());
        result.put("filterCanteenId", canteenId);
        result.put("filterStallId", stallId);
        return result;
    }
}
