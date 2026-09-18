/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.dto.CartAddRequest;
import com.campus.canteen.dto.PlaceOrderRequest;
import com.campus.canteen.service.CartService;
import com.campus.canteen.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单接口：下单、支付、状态机流转、取消、催单与统计导出。
 *
 * @since 2026-09-15
 */
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final CartService cartService;

    /**
     * 提交订单，支持库存不足自动替换与自动支付。
     *
     * @param request 下单请求
     * @return 下单结果，含订单号与支付状态
     */
    @PostMapping("/order/place")
    public Result<Map<String, Object>> place(@Valid @RequestBody PlaceOrderRequest request) {
        return Result.ok(orderService.placeOrder(request));
    }

    /**
     * 确认支付订单。
     *
     * @param orderNo   订单号
     * @param channel   支付渠道，默认余额
     * @param isSuccess 支付结果，模拟第三方回调
     * @return 支付后的订单信息
     */
    @PostMapping("/order/pay/confirm")
    public Result<Map<String, Object>> confirmPay(
            @RequestParam(name = "orderNo") String orderNo,
            @RequestParam(name = "channel", defaultValue = "BALANCE") String channel,
            @RequestParam(name = "success", defaultValue = "true") boolean isSuccess) {
        return Result.ok(orderService.confirmPay(orderNo, channel, isSuccess));
    }

    /**
     * 查询当前用户的订单列表，含明细、状态中文名与是否可取消。
     *
     * @return 我的订单列表
     */
    @GetMapping("/order/mine")
    public Result<List<Map<String, Object>>> mine() {
        return Result.ok(orderService.myOrders());
    }

    /**
     * 查询订单详情，学生/老师只能看自己的订单，食堂端与管理员端可看任意订单。
     *
     * @param id 订单 ID
     * @return 订单详情
     */
    @GetMapping("/order/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable(name = "id") Long id) {
        return Result.ok(orderService.orderDetail(id));
    }

    /**
     * 查询订单明细。
     *
     * @param id 订单 ID
     * @return 订单明细列表
     */
    @GetMapping("/order/{id}/items")
    public Result<?> items(@PathVariable(name = "id") Long id) {
        return Result.ok(orderService.orderItems(id));
    }

    /**
     * 查询档口待处理订单，供档口接单看板使用。
     *
     * @param stallId 档口 ID
     * @return 待处理订单列表
     */
    @GetMapping("/order/stall/{stallId}/pending")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<Map<String, Object>>> pending(@PathVariable(name = "stallId") Long stallId) {
        return Result.ok(orderService.stallPending(stallId));
    }

    /**
     * 查询档口下拉选项，含待处理订单数量。
     *
     * @return 档口选项列表
     */
    @GetMapping("/order/stall/options")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<Map<String, Object>>> stallOptions() {
        return Result.ok(orderService.stallOptions());
    }

    /**
     * 流转订单状态：接单/备餐、出餐、核销或标记异常。
     *
     * @param id             订单 ID
     * @param status         目标状态
     * @param abnormalReason 异常原因，标记异常时必填
     * @return 流转后的订单信息
     */
    @PostMapping("/order/{id}/status")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Map<String, Object>> status(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "status") String status,
            @RequestParam(name = "abnormalReason", required = false) String abnormalReason) {
        return Result.ok(orderService.updateStatus(id, status, abnormalReason));
    }

    /**
     * 取消订单：未出餐可取消，已支付订单自动退款。
     *
     * @param id     订单 ID
     * @param reason 取消原因，可为空
     * @return 取消结果与退款金额
     */
    @PostMapping("/order/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable(name = "id") Long id,
                                              @RequestParam(name = "reason", required = false) String reason) {
        return Result.ok(orderService.cancelByUser(id, reason));
    }

    /**
     * 扫码核销：按取餐码完成取餐。
     *
     * @param pickupCode 取餐码
     * @return 核销后的订单信息
     */
    @PostMapping("/order/scan")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Map<String, Object>> scan(@RequestParam(name = "pickupCode") String pickupCode) {
        return Result.ok(orderService.scanPickup(pickupCode));
    }

    /**
     * 催单。
     *
     * @param id 订单 ID
     * @return 催单次数与最新状态
     */
    @PostMapping("/order/{id}/urge")
    public Result<Map<String, Object>> urge(@PathVariable(name = "id") Long id) {
        return Result.ok(orderService.urgeOrder(id));
    }

    /**
     * 支付回调（模拟第三方支付异步通知）。
     *
     * @param orderNo   订单号
     * @param channel   支付渠道
     * @param isSuccess 支付结果
     * @return 支付后的订单信息
     */
    @PostMapping("/order/pay/callback")
    public Result<Map<String, Object>> payCallback(
            @RequestParam(name = "orderNo") String orderNo,
            @RequestParam(name = "channel") String channel,
            @RequestParam(name = "success", defaultValue = "true") boolean isSuccess) {
        return Result.ok(orderService.payCallback(orderNo, channel, isSuccess));
    }

    /**
     * 加入购物车。
     *
     * @param request 加购请求
     * @return 空响应
     */
    @PostMapping("/cart")
    public Result<Void> addCart(@RequestBody CartAddRequest request) {
        cartService.add(request);
        return Result.ok();
    }

    /**
     * 查询当前用户的购物车。
     *
     * @return 购物车条目列表
     */
    @GetMapping("/cart")
    public Result<List<Map<String, Object>>> cart() {
        return Result.ok(cartService.list());
    }

    /**
     * 移除购物车中的某条记录。
     *
     * @param id 购物车条目 ID
     * @return 空响应
     */
    @DeleteMapping("/cart/{id}")
    public Result<Void> removeCart(@PathVariable(name = "id") Long id) {
        cartService.remove(id);
        return Result.ok();
    }

    /**
     * 清空当前用户的购物车。
     *
     * @return 空响应
     */
    @DeleteMapping("/cart")
    public Result<Void> clearCart() {
        cartService.clear();
        return Result.ok();
    }
}
