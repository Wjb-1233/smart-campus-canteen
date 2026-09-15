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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireJob {
    private final OrderService orderService;
    private final DishMapper dishMapper;
    private final DishService dishService;

    /** 超时未支付：取消 CREATED 并释放 Redis/DB 库存 */
    @Scheduled(fixedDelay = 60000)
    public void cancelUnpaid() {
        int n = orderService.expireUnpaidOrders();
        if (n > 0) {
            log.info("Expired unpaid orders: {}", n);
        }
    }

    /** 每5分钟同步库存到 Redis，防缓存漂移 */
    @Scheduled(fixedDelay = 300000)
    public void syncStock() {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>().eq(Dish::getStatus, 1));
        dishes.forEach(d -> dishService.syncStockToRedis(d.getId(), d.getStock()));
    }
}
