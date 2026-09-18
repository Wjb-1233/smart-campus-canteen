/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.dto.WasteReportRequest;
import com.campus.canteen.entity.Order;
import com.campus.canteen.entity.OrderItem;
import com.campus.canteen.entity.WasteRecord;
import com.campus.canteen.mapper.OrderItemMapper;
import com.campus.canteen.mapper.OrderMapper;
import com.campus.canteen.mapper.WasteRecordMapper;
import com.campus.canteen.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剩饭浪费登记与溯源业务，支撑菜品浪费分析与优化建议。
 *
 * @since 2026-09-15
 */
@Service
@RequiredArgsConstructor
public class WasteService {
    private final WasteRecordMapper wasteRecordMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 登记一条剩饭浪费记录，用于后续剩饭率统计与菜品优化。
     *
     * @param req 浪费登记请求
     * @return 登记结果，含记录 ID、浪费比例与提示信息
     * @throws BizException 订单不存在、越权登记或订单未取餐时抛出
     */
    @Transactional
    public Map<String, Object> report(WasteReportRequest req) {
        Order order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException("订单不存在");
        }
        Long uid = SecurityUtils.currentUserId();
        String role = SecurityUtils.currentUser().getRole();
        boolean isStaff = List.of("STALL", "ADMIN").contains(role);
        if (!isStaff && !uid.equals(order.getUserId())) {
            throw new BizException(403, "无权登记该订单浪费");
        }
        if (!List.of("PICKED", "READY", "ABNORMAL").contains(order.getStatus())
                && !isStaff) {
            throw new BizException("订单未取餐，暂不可登记浪费");
        }
        long itemCnt = orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, req.getOrderId())
                .eq(OrderItem::getDishId, req.getDishId()));
        if (itemCnt == 0) {
            throw new BizException("订单中不存在该菜品");
        }

        WasteRecord record = new WasteRecord();
        record.setOrderId(req.getOrderId());
        record.setDishId(req.getDishId());
        record.setWasteRatio(req.getWasteRatio());
        record.setReason(req.getReason() == null ? "用户反馈" : req.getReason());
        record.setCreatedAt(LocalDateTime.now());
        wasteRecordMapper.insert(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("wasteRatio", record.getWasteRatio());
        result.put("message", "浪费记录已登记，可用于剩饭率统计");
        return result;
    }
}
