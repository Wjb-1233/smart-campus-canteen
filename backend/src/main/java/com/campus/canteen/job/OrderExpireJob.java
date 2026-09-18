/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.service.DishService;
import com.campus.canteen.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时任务：超时未支付订单自动取消并回滚库存。
 *
 * @since 2026-09-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireJob {
    private final OrderService orderService;
    private final DishMapper dishMapper;
    private final DishService dishService;

    /**
     * 每分钟扫描一次超时未支付订单，取消订单并释放占用的库存。
     */
    @Scheduled(fixedDelay = 60000)
    public void cancelUnpaid() {
        int n = orderService.expireUnpaidOrders();
        if (n > 0) {
            log.info("Expired unpaid orders: {}", n);
        }
    }

    /**
     * 每 5 分钟把数据库库存同步回 Redis，防止缓存漂移。
     */
    @Scheduled(fixedDelay = 300000)
    public void syncStock() {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>().eq(Dish::getStatus, 1));
        dishes.forEach(d -> dishService.syncStockToRedis(d.getId(), d.getStock()));
    }
}
