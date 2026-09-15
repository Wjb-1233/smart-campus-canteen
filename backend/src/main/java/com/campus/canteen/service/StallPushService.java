package com.campus.canteen.service;

import com.campus.canteen.entity.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 订单实时推送：
 * <ul>
 *     <li>档口主题 {@code /topic/stall/{stallId}}：面向食堂管理员/后勤的接单看板。</li>
 *     <li>用户主题 {@code /topic/user/{userId}}：面向下单学生/教师的「我的订单」实时状态。</li>
 * </ul>
 * 一次状态变更会同时投递到两个主题，保证「学生下单 → 管理员接单出餐 → 学生看到进度」的端间联动闭环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StallPushService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void pushNewOrder(Order order) {
        pushOrder(order, "NEW");
    }

    public void pushStatusChange(Order order) {
        pushOrder(order, "STATUS");
    }

    public void pushUrge(Order order) {
        pushOrder(order, "URGE");
    }

    private void pushOrder(Order order, String type) {
        Map<String, Object> payload = buildPayload(order, type);
        // 档口看板
        push("/topic/stall/" + order.getStallId(), payload);
        // 下单用户的「我的订单」实时刷新
        push("/topic/user/" + order.getUserId(), payload);
    }

    private Map<String, Object> buildPayload(Order order, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", order.getUserId());
        payload.put("stallId", order.getStallId());
        payload.put("status", order.getStatus());
        payload.put("expectPickupAt", order.getExpectPickupAt());
        long remainSeconds = 0;
        if (order.getExpectPickupAt() != null) {
            remainSeconds = Math.max(0, Duration.between(LocalDateTime.now(), order.getExpectPickupAt()).getSeconds());
        }
        payload.put("remainSeconds", remainSeconds);
        payload.put("pickupCode", order.getPickupCode());
        payload.put("urgeCount", order.getUrgeCount() == null ? 0 : order.getUrgeCount());
        payload.put("thumbnailUrl", "/images/order-thumb.png");
        return payload;
    }

    private void push(String destination, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.info("WS push {} -> {}", destination, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("WS push failed: {}", e.getMessage());
        }
    }
}
