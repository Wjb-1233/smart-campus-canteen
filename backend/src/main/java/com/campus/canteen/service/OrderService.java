/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.dto.PlaceOrderRequest;
import com.campus.canteen.entity.BalanceLedger;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.HealthProfile;
import com.campus.canteen.entity.NutritionDaily;
import com.campus.canteen.entity.Order;
import com.campus.canteen.entity.OrderItem;
import com.campus.canteen.entity.Stall;
import com.campus.canteen.entity.User;
import com.campus.canteen.mapper.BalanceLedgerMapper;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.HealthProfileMapper;
import com.campus.canteen.mapper.NutritionDailyMapper;
import com.campus.canteen.mapper.OrderItemMapper;
import com.campus.canteen.mapper.OrderMapper;
import com.campus.canteen.mapper.StallMapper;
import com.campus.canteen.mapper.UserMapper;
import com.campus.canteen.security.LoginUser;
import com.campus.canteen.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单业务：下单、支付、状态机流转、取消退款与订单查询。
 *
 * @since 2026-09-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    /** 食堂端 / 管理员端（可查看、操作他人订单）。 */
    private static final Set<String> STAFF_ROLES = Set.of("STALL", "ADMIN");

    /** 后台/系统可取消的订单状态（已取餐后不可取消）。 */
    private static final Set<String> CANCELLABLE_STATUS = Set.of("CREATED", "PAID", "PREPARING", "READY");

    /**
     * 学生/老师可自行取消的订单状态。
     * 已出餐（READY）后由档口投入了食材与人力，学生侧不再允许取消，避免食物浪费；
     * 若确需处理，由档口通过异常订单流程介入。
     */
    private static final Set<String> USER_CANCELLABLE_STATUS = Set.of("CREATED", "PAID", "PREPARING");

    /** 已支付状态（取消时需退款）。 */
    private static final Set<String> PAID_STATUS = Set.of("PAID", "PREPARING", "READY");

    /** 订单状态机：key 为当前状态，value 为允许流转到的目标状态。 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "CREATED", Set.of("PAID", "CANCELLED"),
            "PAID", Set.of("PREPARING", "READY", "ABNORMAL", "CANCELLED"),
            "PREPARING", Set.of("READY", "ABNORMAL", "CANCELLED"),
            "READY", Set.of("PICKED", "ABNORMAL", "CANCELLED"),
            "ABNORMAL", Set.of("PREPARING", "READY", "PICKED", "CANCELLED"),
            "PICKED", Set.of(),
            "CANCELLED", Set.of()
    );

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "CREATED", "待支付",
            "PAID", "已支付/待接单",
            "PREPARING", "备餐中",
            "READY", "待取餐",
            "PICKED", "已取餐",
            "ABNORMAL", "异常处理中",
            "CANCELLED", "已取消"
    );

    private static final Map<String, String> MEAL_PERIOD_NAMES = Map.of(
            "BREAKFAST", "早餐",
            "LUNCH", "午餐",
            "DINNER", "晚餐",
            "NIGHT", "夜宵"
    );

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final UserMapper userMapper;
    private final NutritionDailyMapper nutritionDailyMapper;
    private final HealthProfileMapper healthProfileMapper;
    private final StringRedisTemplate redisTemplate;
    @Qualifier("stockDeductScript")
    private final DefaultRedisScript<Long> stockDeductScript;
    @Qualifier("stockRestoreScript")
    private final DefaultRedisScript<Long> stockRestoreScript;
    private final ObjectMapper objectMapper;
    private final StallPushService stallPushService;
    private final DietGuardService dietGuardService;
    private final BalanceLedgerMapper balanceLedgerMapper;
    private final DishService dishService;

    @Value("${canteen.order.lock-hours-ahead:2}")
    private int lockHoursAhead;

    @Value("${canteen.order.unpaid-expire-minutes:15}")
    private int unpaidExpireMinutes;

    /**
     * 提交订单：校验锁单窗口、扣减库存（不足自动替换）、按档口拆单并可选自动支付。
     *
     * @param req 下单请求
     * @return 下单结果，含订单摘要、总金额、总热量与替换记录
     * @throws BizException 菜品不可用、库存不足、触发健康禁忌或余额不足时抛出
     */
    @Transactional
    public Map<String, Object> placeOrder(PlaceOrderRequest req) {
        Long userId = SecurityUtils.currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        validateMealLockWindow(req.getMealPeriod());

        List<Map<String, Object>> replaceLogs = new ArrayList<>();
        List<StockHold> holds = new ArrayList<>();
        List<ResolvedItem> resolved = resolveItems(userId, req, replaceLogs, holds);

        Map<Long, List<ResolvedItem>> byStall = resolved.stream()
                .collect(Collectors.groupingBy(ri -> ri.dish.getStallId()));
        LocalDateTime expectPickup = resolveExpectPickup(req.getMealPeriod());
        List<Map<String, Object>> createdOrders = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int grandCalorie = 0;
        for (Map.Entry<Long, List<ResolvedItem>> entry : byStall.entrySet()) {
            Order order = createOrder(userId, req, entry.getKey(), entry.getValue(), expectPickup);
            createdOrders.add(orderSummary(order));
            grandTotal = grandTotal.add(order.getTotalAmount());
            grandCalorie += order.getTotalCalorie();
        }

        boolean isAutoPay = req.getPayChannel() != null;
        List<Map<String, Object>> paidResults = isAutoPay
                ? payAll(user, createdOrders, req.getPayChannel(), grandTotal)
                : List.of();

        List<Map<String, Object>> finalOrders = isAutoPay ? paidResults : createdOrders;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", finalOrders);
        result.put("orderCount", finalOrders.size());
        result.put("totalAmount", grandTotal);
        result.put("totalCalorie", grandCalorie);
        result.put("replaceLogs", replaceLogs);
        result.put("lockHoursAhead", lockHoursAhead);
        result.put("status", isAutoPay ? "PAID" : "CREATED");
        if (!finalOrders.isEmpty()) {
            Map<String, Object> first = finalOrders.get(0);
            result.put("orderNo", first.get("orderNo"));
            result.put("orderId", first.get("orderId"));
            result.put("pickupCode", first.get("pickupCode"));
            result.put("expectPickupAt", first.get("expectPickupAt"));
        }
        return result;
    }

    /**
     * 确认支付订单；余额支付会扣减校园卡并写入消费流水。
     *
     * @param orderNo 订单号
     * @param channel 支付渠道
     * @param isSuccess 支付结果，false 表示支付失败并自动取消订单
     * @return 支付后的订单摘要
     * @throws BizException 订单不存在、状态不可支付或余额不足时抛出
     */
    @Transactional
    public Map<String, Object> confirmPay(String orderNo, String channel, boolean isSuccess) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if ("PAID".equals(order.getStatus())) {
            return orderSummary(order);
        }
        if (!"CREATED".equals(order.getStatus())) {
            throw new BizException("订单状态不可支付: " + order.getStatus());
        }
        if (!isSuccess) {
            cancelAndRelease(orderNo, "支付失败");
            return Map.of("orderNo", orderNo, "status", "CANCELLED");
        }
        if ("BALANCE".equalsIgnoreCase(channel)) {
            deductBalance(order);
        }
        order.setStatus("PAID");
        order.setPayChannel(channel);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        int cal = items.stream().mapToInt(i -> i.getCalorie() * i.getQuantity()).sum();
        BigDecimal protein = items.stream()
                .map(i -> i.getProtein().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        upsertNutrition(order.getUserId(), cal, protein);
        stallPushService.pushNewOrder(order);
        return orderSummary(order);
    }

    /**
     * 第三方支付异步回调，语义与确认支付一致。
     *
     * @param orderNo   订单号
     * @param channel   支付渠道
     * @param isSuccess 支付结果
     * @return 支付后的订单摘要
     */
    public Map<String, Object> payCallback(String orderNo, String channel, boolean isSuccess) {
        return confirmPay(orderNo, channel, isSuccess);
    }

    /**
     * 按订单号取消（系统内部调用：超时未支付、支付失败、余额不足）。
     *
     * @param orderNo 订单号
     * @param reason  取消原因
     * @return 取消结果；订单不存在时返回 NOT_FOUND
     */
    @Transactional
    public Map<String, Object> cancelAndRelease(String orderNo, String reason) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            return Map.of("status", "NOT_FOUND");
        }
        return doCancel(order, reason, null);
    }

    /**
     * 学生/老师主动取消自己的订单；食堂端与管理员端可取消任意订单。
     *
     * @param orderId 订单 ID
     * @param reason  取消原因，为空时按操作人身份生成
     * @return 取消结果与退款金额
     * @throws BizException 订单不存在、越权取消或状态不允许取消时抛出
     */
    @Transactional
    public Map<String, Object> cancelByUser(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        LoginUser current = SecurityUtils.currentUser();
        boolean isStaff = STAFF_ROLES.contains(current.getRole());
        if (!isStaff && !current.getId().equals(order.getUserId())) {
            throw new BizException(403, "无权取消他人订单");
        }
        String finalReason = (reason == null || reason.isBlank())
                ? (isStaff ? "档口取消订单" : "用户取消订单")
                : reason;
        if (isStaff) {
            return doCancel(order, finalReason, current.getId());
        }
        if (!USER_CANCELLABLE_STATUS.contains(order.getStatus())) {
            throw new BizException("订单已进入「" + statusName(order.getStatus())
                    + "」阶段，档口已投入备餐，无法自助取消；如有问题请联系档口工作人员处理");
        }
        return doCancel(order, finalReason, current.getId(), USER_CANCELLABLE_STATUS);
    }

    /**
     * 扫描并取消超时未支付订单，释放其占用的库存。
     *
     * @return 本次取消的订单数量
     */
    @Transactional
    public int expireUnpaidOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(unpaidExpireMinutes);
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "CREATED")
                .lt(Order::getCreatedAt, threshold));
        for (Order order : expired) {
            cancelAndRelease(order.getOrderNo(), "超时未支付自动释放库存");
        }
        return expired.size();
    }

    /**
     * 查询当前用户的订单列表，带明细与可取消标记。
     *
     * @return 订单列表
     */
    public List<Map<String, Object>> myOrders() {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, SecurityUtils.currentUserId())
                .orderByDesc(Order::getCreatedAt));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order order : orders) {
            rows.add(orderRow(order, true));
        }
        return rows;
    }

    /**
     * 查询订单详情，学生/老师仅能查看自己的订单。
     *
     * @param orderId 订单 ID
     * @return 订单详情
     * @throws BizException 订单不存在或无权查看时抛出
     */
    public Map<String, Object> orderDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        assertCanAccess(order);
        return orderRow(order, true);
    }

    /**
     * 查询档口下拉选项，含各档口待处理订单数量。
     *
     * @return 档口选项列表
     */
    public List<Map<String, Object>> stallOptions() {
        List<Stall> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .orderByAsc(Stall::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Stall stall : stalls) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", stall.getId());
            row.put("name", stall.getName());
            row.put("type", stall.getType());
            row.put("canteenId", stall.getCanteenId());
            row.put("pendingCount", orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getStallId, stall.getId())
                    .in(Order::getStatus, "PAID", "PREPARING", "READY")));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 查询订单明细。
     *
     * @param orderId 订单 ID
     * @return 订单明细列表
     * @throws BizException 订单不存在或无权查看时抛出
     */
    public List<OrderItem> orderItems(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        Long uid = SecurityUtils.currentUserId();
        String role = SecurityUtils.currentUser().getRole();
        boolean isStaff = STAFF_ROLES.contains(role);
        if (!isStaff && !uid.equals(order.getUserId())) {
            throw new BizException(403, "无权查看");
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

    /**
     * 查询档口待处理订单，供档口接单看板使用。
     *
     * @param stallId 档口 ID
     * @return 待处理订单列表
     */
    public List<Map<String, Object>> stallPending(Long stallId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStallId, stallId)
                .in(Order::getStatus, "PAID", "PREPARING", "READY")
                .orderByAsc(Order::getCreatedAt));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order order : orders) {
            rows.add(toStallBoardRow(order));
        }
        return rows;
    }

    /**
     * 流转订单状态：接单/备餐、出餐、核销或标记异常，非法流转直接拒绝。
     *
     * @param orderId        订单 ID
     * @param status         目标状态
     * @param abnormalReason 异常原因，标记异常时建议填写
     * @return 流转后的订单摘要
     * @throws BizException 订单不存在或状态流转非法时抛出
     */
    @Transactional
    public Map<String, Object> updateStatus(Long orderId, String status, String abnormalReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        String target = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(target)) {
            String reason = abnormalReason == null || abnormalReason.isBlank() ? "无" : abnormalReason;
            return doCancel(order, "档口取消：" + reason, SecurityUtils.currentUserId());
        }
        String from = order.getStatus();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
            throw new BizException("订单状态不允许从「" + statusName(from) + "」变更为「" + statusName(target) + "」");
        }
        order.setStatus(target);
        if ("ABNORMAL".equals(target)) {
            order.setAbnormalFlag(1);
            order.setAbnormalReason(abnormalReason == null || abnormalReason.isBlank()
                    ? "档口标记异常" : abnormalReason);
        } else if (order.getAbnormalFlag() != null && order.getAbnormalFlag() == 1) {
            order.setAbnormalFlag(0);
            order.setAbnormalReason(null);
        } else {
            order.setAbnormalFlag(0);
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        stallPushService.pushStatusChange(order);
        return toStallBoardRow(order);
    }

    /**
     * 扫码核销：按取餐码把订单置为已取餐。
     *
     * @param pickupCode 取餐码
     * @return 核销后的订单摘要
     * @throws BizException 取餐码无效或订单已核销时抛出
     */
    @Transactional
    public Map<String, Object> scanPickup(String pickupCode) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getPickupCode, pickupCode)
                .in(Order::getStatus, "READY", "PREPARING", "PAID")
                .orderByDesc(Order::getCreatedAt)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BizException("取餐码无效或订单已核销");
        }
        order.setStatus("PICKED");
        order.setUpdatedAt(LocalDateTime.now());
        order.setRemark(appendRemark(order.getRemark(), "扫码核销"));
        orderMapper.updateById(order);
        stallPushService.pushStatusChange(order);
        return toStallBoardRow(order);
    }

    /**
     * 催单，最多 5 次。
     *
     * @param orderId 订单 ID
     * @return 催单后的订单摘要
     * @throws BizException 订单不存在、无权操作、状态不可催单或已达催单上限时抛出
     */
    @Transactional
    public Map<String, Object> urgeOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        assertCanAccess(order);
        if (!List.of("PAID", "PREPARING", "READY").contains(order.getStatus())) {
            throw new BizException("当前状态不可催单");
        }
        int urge = order.getUrgeCount() == null ? 0 : order.getUrgeCount();
        if (urge >= 5) {
            throw new BizException("催单次数已达上限，请耐心等待或联系档口");
        }
        order.setUrgeCount(urge + 1);
        order.setRemark(appendRemark(order.getRemark(), "催单#" + order.getUrgeCount()));
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        stallPushService.pushUrge(order);
        return toStallBoardRow(order);
    }

    /** 逐项解析下单明细：校验菜品与健康禁忌、扣减库存，必要时自动替换。 */
    private List<ResolvedItem> resolveItems(Long userId, PlaceOrderRequest req,
                                            List<Map<String, Object>> replaceLogs, List<StockHold> holds) {
        List<ResolvedItem> resolved = new ArrayList<>();
        boolean isSuccess = false;
        try {
            for (PlaceOrderRequest.Item item : req.getItems()) {
                resolved.add(resolveItem(userId, req, item, replaceLogs, holds));
            }
            isSuccess = true;
            return resolved;
        } finally {
            if (!isSuccess) {
                rollbackHolds(holds);
            }
        }
    }

    private ResolvedItem resolveItem(Long userId, PlaceOrderRequest req, PlaceOrderRequest.Item item,
                                     List<Map<String, Object>> replaceLogs, List<StockHold> holds) {
        Dish dish = dishMapper.selectById(item.getDishId());
        if (dish == null || dish.getStatus() != 1) {
            throw new BizException("菜品不可用: " + item.getDishId());
        }
        dietGuardService.assertDishAllowed(userId, dish);

        Dish finalDish = dish;
        Long replacedFrom = null;
        ensureRedisStock(dish);
        Long remain = deductStock(dish, item.getQuantity());
        if (remain != null && remain == -1) {
            Dish sub = dishMapper.findSubstitute(dish.getId(), req.getMealPeriod());
            if (sub == null) {
                throw new BizException("菜品库存不足且无替代: " + dish.getName());
            }
            dietGuardService.assertDishAllowed(userId, sub);
            ensureRedisStock(sub);
            Long subRemain = deductStock(sub, item.getQuantity());
            if (subRemain == null || subRemain < 0) {
                throw new BizException("替代菜品库存也不足: " + sub.getName());
            }
            holds.add(new StockHold(sub.getId(), item.getQuantity()));
            replaceLogs.add(replaceLog(dish, sub));
            replacedFrom = dish.getId();
            finalDish = sub;
        } else if (remain != null && remain == -2) {
            throw new BizException("库存缓存未初始化: " + dish.getName());
        } else {
            holds.add(new StockHold(finalDish.getId(), item.getQuantity()));
        }

        JsonNode nutrition = readNutrition(finalDish.getNutritionJson());
        int unitCal = nutrition.path("calorie").asInt(0);
        BigDecimal unitProtein = BigDecimal.valueOf(nutrition.path("protein").asDouble(0));
        finalDish.setStock(Math.max(0, finalDish.getStock() - item.getQuantity()));
        dishMapper.updateById(finalDish);
        dishService.checkLowStockAndPush(finalDish.getId());
        return new ResolvedItem(finalDish, item.getQuantity(), unitCal, unitProtein, replacedFrom);
    }

    /** 按档口生成订单与明细。 */
    private Order createOrder(Long userId, PlaceOrderRequest req, Long stallId,
                              List<ResolvedItem> items, LocalDateTime expectPickup) {
        BigDecimal total = BigDecimal.ZERO;
        int totalCalorie = 0;
        for (ResolvedItem ri : items) {
            total = total.add(ri.dish.getPrice().multiply(BigDecimal.valueOf(ri.quantity)));
            totalCalorie += ri.calorie * ri.quantity;
        }

        Order order = new Order();
        order.setOrderNo(genOrderNo());
        order.setUserId(userId);
        order.setStallId(stallId);
        order.setMealPeriod(req.getMealPeriod());
        order.setTotalAmount(total);
        order.setTotalCalorie(totalCalorie);
        order.setStatus("CREATED");
        order.setPayChannel(req.getPayChannel());
        order.setPickupCode(String.format("%04d", ThreadLocalRandom.current().nextInt(10000)));
        order.setExpectPickupAt(expectPickup);
        order.setRemark(req.getRemark());
        order.setAbnormalFlag(0);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        for (ResolvedItem ri : items) {
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setDishId(ri.dish.getId());
            oi.setDishName(ri.dish.getName());
            oi.setUnitPrice(ri.dish.getPrice());
            oi.setQuantity(ri.quantity);
            oi.setCalorie(ri.calorie);
            oi.setProtein(ri.protein);
            oi.setReplacedFrom(ri.replacedFrom);
            orderItemMapper.insert(oi);
        }
        return order;
    }

    /** 批量自动支付；余额不足时取消全部已生成订单。 */
    private List<Map<String, Object>> payAll(User user, List<Map<String, Object>> createdOrders,
                                             String payChannel, BigDecimal grandTotal) {
        if ("BALANCE".equalsIgnoreCase(payChannel) && user.getBalance().compareTo(grandTotal) < 0) {
            for (Map<String, Object> o : createdOrders) {
                Object orderNo = o.get("orderNo");
                if (orderNo instanceof String no) {
                    cancelAndRelease(no, "余额不足自动取消");
                }
            }
            throw new BizException("余额不足");
        }
        List<Map<String, Object>> paidResults = new ArrayList<>();
        for (Map<String, Object> o : createdOrders) {
            Object orderNo = o.get("orderNo");
            if (orderNo instanceof String no) {
                paidResults.add(confirmPay(no, payChannel, true));
            }
        }
        return paidResults;
    }

    /** 余额支付：扣减校园卡余额并写入消费流水。 */
    private void deductBalance(Order order) {
        User user = userMapper.selectById(order.getUserId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (user.getBalance().compareTo(order.getTotalAmount()) < 0) {
            cancelAndRelease(order.getOrderNo(), "余额不足");
            throw new BizException("余额不足");
        }
        user.setBalance(user.getBalance().subtract(order.getTotalAmount()));
        userMapper.updateById(user);

        BalanceLedger ledger = new BalanceLedger();
        ledger.setUserId(user.getId());
        ledger.setChangeAmt(order.getTotalAmount().negate());
        ledger.setBalanceAfter(user.getBalance());
        ledger.setBizType("ORDER_PAY");
        ledger.setBizNo(order.getOrderNo());
        ledger.setRemark("订单支付");
        ledger.setCreatedAt(LocalDateTime.now());
        balanceLedgerMapper.insert(ledger);
    }

    /** 订单摘要：用于下单与支付结果返回。 */
    private Map<String, Object> orderSummary(Order order) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("orderNo", order.getOrderNo());
        one.put("orderId", order.getId());
        one.put("stallId", order.getStallId());
        one.put("pickupCode", order.getPickupCode());
        one.put("totalAmount", order.getTotalAmount());
        one.put("totalCalorie", order.getTotalCalorie());
        one.put("expectPickupAt", order.getExpectPickupAt());
        one.put("status", order.getStatus());
        return one;
    }

    private Map<String, Object> replaceLog(Dish from, Dish to) {
        Map<String, Object> logItem = new LinkedHashMap<>();
        logItem.put("from", from.getName());
        logItem.put("to", to.getName());
        logItem.put("reason", "库存不足自动替换");
        return logItem;
    }

    private Long deductStock(Dish dish, int quantity) {
        return redisTemplate.execute(stockDeductScript,
                List.of(DishService.stockKey(dish.getId())), String.valueOf(quantity));
    }

    /** 带可取消状态集合的取消逻辑：学生侧与后台侧的取消权限不同。 */
    private Map<String, Object> doCancel(Order order, String reason, Long operatorId) {
        return doCancel(order, reason, operatorId, CANCELLABLE_STATUS);
    }

    /**
     * 统一的取消逻辑：回滚 Redis + DB 库存；已支付订单（PAID/PREPARING/READY）按支付渠道退款；
     * 已取餐（PICKED）订单不可取消。
     */
    private Map<String, Object> doCancel(Order order, String reason, Long operatorId, Set<String> allowedStatus) {
        if ("CANCELLED".equals(order.getStatus())) {
            return cancelResult(order, "订单已取消", BigDecimal.ZERO);
        }
        if (!allowedStatus.contains(order.getStatus())) {
            throw new BizException("订单已进入「" + statusName(order.getStatus()) + "」阶段，不可取消");
        }
        boolean isPaid = PAID_STATUS.contains(order.getStatus());
        BigDecimal refundAmount = isPaid ? order.getTotalAmount() : BigDecimal.ZERO;
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            restoreStock(item.getDishId(), item.getQuantity());
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish != null) {
                dish.setStock(dish.getStock() + item.getQuantity());
                dishMapper.updateById(dish);
                dishService.checkLowStockAndPush(dish.getId());
            }
        }
        // 已支付订单需退款（模拟渠道记录流水说明，校园卡余额原路退回）
        if (isPaid) {
            refundOnCancel(order);
        }
        order.setStatus("CANCELLED");
        order.setRemark(appendRemark(order.getRemark(), reason));
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        stallPushService.pushStatusChange(order);
        log.info("Order {} cancelled by {}: {}", order.getOrderNo(),
                operatorId == null ? "SYSTEM" : operatorId, reason);
        return cancelResult(order, reason, refundAmount);
    }

    private Map<String, Object> cancelResult(Order order, String reason, BigDecimal refundAmount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());
        result.put("statusName", statusName(order.getStatus()));
        result.put("reason", reason);
        result.put("refund", refundAmount);
        return result;
    }

    /** 取消已支付订单时的退款处理：校园卡余额原路退回并写退款流水。 */
    private void refundOnCancel(Order order) {
        if (!"BALANCE".equalsIgnoreCase(order.getPayChannel())) {
            log.info("Order {} paid by {}, simulating third-party refund of {} yuan",
                    order.getOrderNo(), order.getPayChannel(), order.getTotalAmount());
            return;
        }
        User user = userMapper.selectById(order.getUserId());
        if (user == null) {
            return;
        }
        user.setBalance(user.getBalance().add(order.getTotalAmount()));
        userMapper.updateById(user);

        BalanceLedger ledger = new BalanceLedger();
        ledger.setUserId(user.getId());
        ledger.setChangeAmt(order.getTotalAmount());
        ledger.setBalanceAfter(user.getBalance());
        ledger.setBizType("REFUND");
        ledger.setBizNo(order.getOrderNo());
        ledger.setRemark("订单取消退款");
        ledger.setCreatedAt(LocalDateTime.now());
        balanceLedgerMapper.insert(ledger);
    }

    private void assertCanAccess(Order order) {
        LoginUser current = SecurityUtils.currentUser();
        if (!STAFF_ROLES.contains(current.getRole()) && !current.getId().equals(order.getUserId())) {
            throw new BizException(403, "无权查看该订单");
        }
    }

    private Map<String, Object> orderRow(Order order, boolean hasItems) {
        Map<String, Object> row = toStallBoardRow(order);
        row.put("userId", order.getUserId());
        row.put("mealPeriod", order.getMealPeriod());
        row.put("mealPeriodName", MEAL_PERIOD_NAMES.getOrDefault(order.getMealPeriod(), order.getMealPeriod()));
        row.put("payChannel", order.getPayChannel());
        row.put("statusName", statusName(order.getStatus()));
        row.put("totalCalorie", order.getTotalCalorie());
        row.put("abnormalReason", order.getAbnormalReason());
        row.put("createdAt", order.getCreatedAt());
        // cancellable：学生/老师自助取消入口（已出餐不可取消）
        row.put("cancellable", USER_CANCELLABLE_STATUS.contains(order.getStatus()));
        // staffCancellable：档口/后台强制取消入口（含已出餐的异常处置）
        row.put("staffCancellable", CANCELLABLE_STATUS.contains(order.getStatus()));
        row.put("urgent", order.getUrgeCount() != null && order.getUrgeCount() > 0);
        if (hasItems) {
            row.put("items", orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getOrderId, order.getId())));
        }
        return row;
    }

    private String statusName(String status) {
        return STATUS_NAMES.getOrDefault(status, status);
    }

    private Map<String, Object> toStallBoardRow(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("stallId", order.getStallId());
        row.put("status", order.getStatus());
        row.put("pickupCode", order.getPickupCode());
        row.put("expectPickupAt", order.getExpectPickupAt());
        row.put("urgeCount", order.getUrgeCount() == null ? 0 : order.getUrgeCount());
        row.put("totalAmount", order.getTotalAmount());
        row.put("remark", order.getRemark());
        long remainSeconds = 0L;
        if (order.getExpectPickupAt() != null) {
            remainSeconds = Math.max(0L, Duration.between(LocalDateTime.now(),
                    order.getExpectPickupAt()).getSeconds());
        }
        row.put("remainSeconds", remainSeconds);
        return row;
    }

    private String appendRemark(String old, String reason) {
        if (old == null || old.isBlank()) {
            return reason;
        }
        return old + " | " + reason;
    }

    /**
     * 预计取餐时间 = 该餐段开餐时间（已过则顺延为「当前时间 + 30 分钟」），
     * 与锁单窗口语义保持一致，档口倒计时才有实际意义。
     */
    private LocalDateTime resolveExpectPickup(String mealPeriod) {
        LocalTime start = mealStartTime(mealPeriod);
        LocalDateTime now = LocalDateTime.now();
        if (start == null) {
            return now.plusMinutes(30);
        }
        LocalDateTime mealStart = LocalDateTime.of(LocalDate.now(), start);
        return mealStart.isAfter(now) ? mealStart : now.plusMinutes(30);
    }

    private void validateMealLockWindow(String mealPeriod) {
        LocalTime start = mealStartTime(mealPeriod);
        if (start == null) {
            return;
        }
        LocalDateTime mealStart = LocalDateTime.of(LocalDate.now(), start);
        if (!LocalDateTime.now().isBefore(mealStart)) {
            return;
        }
        LocalDateTime earliest = mealStart.minusHours(lockHoursAhead);
        if (LocalDateTime.now().isBefore(earliest)) {
            throw new BizException("未到锁单窗口：请提前" + lockHoursAhead + "小时内预订「" + mealPeriod + "」");
        }
    }

    private LocalTime mealStartTime(String mealPeriod) {
        return switch (mealPeriod == null ? "" : mealPeriod.toUpperCase(Locale.ROOT)) {
            case "BREAKFAST" -> LocalTime.of(6, 30);
            case "LUNCH" -> LocalTime.of(11, 0);
            case "DINNER" -> LocalTime.of(17, 0);
            case "NIGHT" -> LocalTime.of(21, 0);
            default -> null;
        };
    }

    private void rollbackHolds(List<StockHold> holds) {
        for (StockHold hold : holds) {
            restoreStock(hold.dishId(), hold.qty());
            Dish dish = dishMapper.selectById(hold.dishId());
            if (dish != null) {
                dish.setStock(dish.getStock() + hold.qty());
                dishMapper.updateById(dish);
            }
        }
    }

    private void restoreStock(Long dishId, int qty) {
        ensureRedisStockById(dishId);
        redisTemplate.execute(stockRestoreScript, List.of(DishService.stockKey(dishId)), String.valueOf(qty));
    }

    private void ensureRedisStock(Dish dish) {
        ensureRedisStockById(dish.getId(), dish.getStock());
    }

    private void ensureRedisStockById(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish != null) {
            ensureRedisStockById(dishId, dish.getStock());
        }
    }

    private void ensureRedisStockById(Long dishId, int stock) {
        String key = DishService.stockKey(dishId);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().set(key, String.valueOf(stock));
        }
    }

    private void upsertNutrition(Long userId, int calorie, BigDecimal protein) {
        LocalDate today = LocalDate.now();
        NutritionDaily daily = nutritionDailyMapper.selectOne(new LambdaQueryWrapper<NutritionDaily>()
                .eq(NutritionDaily::getUserId, userId)
                .eq(NutritionDaily::getStatDate, today));
        int targetProtein = resolveTargetProtein(userId);
        if (daily == null) {
            daily = new NutritionDaily();
            daily.setUserId(userId);
            daily.setStatDate(today);
            daily.setCalorie(calorie);
            daily.setProtein(protein);
            daily.setFat(BigDecimal.ZERO);
            daily.setCarb(BigDecimal.ZERO);
            daily.setProteinRate(proteinRate(protein, targetProtein));
            nutritionDailyMapper.insert(daily);
        } else {
            daily.setCalorie(daily.getCalorie() + calorie);
            daily.setProtein(daily.getProtein().add(protein));
            daily.setProteinRate(proteinRate(daily.getProtein(), targetProtein));
            nutritionDailyMapper.updateById(daily);
        }
    }

    private int resolveTargetProtein(Long userId) {
        HealthProfile profile = healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId));
        if (profile != null && profile.getTargetProtein() != null) {
            return profile.getTargetProtein();
        }
        return 70;
    }

    private BigDecimal proteinRate(BigDecimal protein, int targetProtein) {
        return protein.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(targetProtein), 2, RoundingMode.HALF_UP);
    }

    private JsonNode readNutrition(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private String genOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private record ResolvedItem(Dish dish, int quantity, int calorie, BigDecimal protein, Long replacedFrom) {}
    private record StockHold(Long dishId, int qty) {}
}
