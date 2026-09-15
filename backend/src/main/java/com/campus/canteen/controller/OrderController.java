package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.dto.CartAddRequest;
import com.campus.canteen.dto.PlaceOrderRequest;
import com.campus.canteen.service.CartService;
import com.campus.canteen.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final CartService cartService;

    @PostMapping("/order/place")
    public Result<Map<String, Object>> place(@Valid @RequestBody PlaceOrderRequest request) {
        return Result.ok(orderService.placeOrder(request));
    }

    @PostMapping("/order/pay/confirm")
    public Result<Map<String, Object>> confirmPay(@RequestParam(name = "orderNo") String orderNo,
                                                  @RequestParam(name = "channel", defaultValue = "BALANCE") String channel,
                                                  @RequestParam(name = "success", defaultValue = "true") boolean success) {
        return Result.ok(orderService.confirmPay(orderNo, channel, success));
    }

    /** 我的订单（含明细、状态中文名、是否可取消）。 */
    @GetMapping("/order/mine")
    public Result<List<Map<String, Object>>> mine() {
        return Result.ok(orderService.myOrders());
    }

    /** 订单详情（学生看自己的，后台角色可看任意订单）。 */
    @GetMapping("/order/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable(name = "id") Long id) {
        return Result.ok(orderService.orderDetail(id));
    }

    @GetMapping("/order/{id}/items")
    public Result<?> items(@PathVariable(name = "id") Long id) {
        return Result.ok(orderService.orderItems(id));
    }

    @GetMapping("/order/stall/{stallId}/pending")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<Map<String, Object>>> pending(@PathVariable(name = "stallId") Long stallId) {
        return Result.ok(orderService.stallPending(stallId));
    }

    /** 档口下拉选项（含待处理数量）。 */
    @GetMapping("/order/stall/options")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<Map<String, Object>>> stallOptions() {
        return Result.ok(orderService.stallOptions());
    }

    @PostMapping("/order/{id}/status")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Map<String, Object>> status(@PathVariable(name = "id") Long id,
                                              @RequestParam(name = "status") String status,
                                              @RequestParam(name = "abnormalReason", required = false) String abnormalReason) {
        return Result.ok(orderService.updateStatus(id, status, abnormalReason));
    }

    /** 取消订单：未出餐可取消，已支付订单自动退款。 */
    @PostMapping("/order/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable(name = "id") Long id,
                                              @RequestParam(name = "reason", required = false) String reason) {
        return Result.ok(orderService.cancelByUser(id, reason));
    }

    @PostMapping("/order/scan")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Map<String, Object>> scan(@RequestParam(name = "pickupCode") String pickupCode) {
        return Result.ok(orderService.scanPickup(pickupCode));
    }

    @PostMapping("/order/{id}/urge")
    public Result<Map<String, Object>> urge(@PathVariable(name = "id") Long id) {
        return Result.ok(orderService.urgeOrder(id));
    }

    @PostMapping("/order/pay/callback")
    public Result<Map<String, Object>> payCallback(@RequestParam(name = "orderNo") String orderNo,
                                                   @RequestParam(name = "channel") String channel,
                                                   @RequestParam(name = "success", defaultValue = "true") boolean success) {
        return Result.ok(orderService.payCallback(orderNo, channel, success));
    }

    @PostMapping("/cart")
    public Result<Void> addCart(@RequestBody CartAddRequest request) {
        cartService.add(request);
        return Result.ok();
    }

    @GetMapping("/cart")
    public Result<List<Map<String, Object>>> cart() {
        return Result.ok(cartService.list());
    }

    @DeleteMapping("/cart/{id}")
    public Result<Void> removeCart(@PathVariable(name = "id") Long id) {
        cartService.remove(id);
        return Result.ok();
    }

    @DeleteMapping("/cart")
    public Result<Void> clearCart() {
        cartService.clear();
        return Result.ok();
    }
}
