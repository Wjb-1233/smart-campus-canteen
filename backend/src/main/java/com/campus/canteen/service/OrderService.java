package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.dto.PlaceOrderRequest;
import com.campus.canteen.entity.*;
import com.campus.canteen.mapper.*;
import com.campus.canteen.security.LoginUser;
import com.campus.canteen.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    /** 食堂端 / 管理员端（可查看、操作他人订单）。 */
    private static final Set<String> STAFF_ROLES = Set.of("STALL", "ADMIN");
    /** 后台/系统可取消的订单状态（已取餐后不可取消）。 */
    private static final Set<String> CANCELLABLE_STATUS = Set.of("CREATED", "PAID", "PREPARING", "READY");
    /**
     * 学生/教师可自行取消的订单状态。
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
    private final DefaultRedisScript<Long> stockDeductScript;
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

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        DishMapper dishMapper,
                        StallMapper stallMapper,
                        UserMapper userMapper,
                        NutritionDailyMapper nutritionDailyMapper,
                        HealthProfileMapper healthProfileMapper,
                        StringRedisTemplate redisTemplate,
                        @Qualifier("stockDeductScript") DefaultRedisScript<Long> stockDeductScript,
                        @Qualifier("stockRestoreScript") DefaultRedisScript<Long> stockRestoreScript,
                        ObjectMapper objectMapper,
                        StallPushService stallPushService,
                        DietGuardService dietGuardService,
                        BalanceLedgerMapper balanceLedgerMapper,
                        DishService dishService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.dishMapper = dishMapper;
        this.stallMapper = stallMapper;
        this.userMapper = userMapper;
        this.nutritionDailyMapper = nutritionDailyMapper;
        this.healthProfileMapper = healthProfileMapper;
        this.redisTemplate = redisTemplate;
        this.stockDeductScript = stockDeductScript;
        this.stockRestoreScript = stockRestoreScript;
        this.objectMapper = objectMapper;
        this.stallPushService = stallPushService;
        this.dietGuardService = dietGuardService;
        this.balanceLedgerMapper = balanceLedgerMapper;
        this.dishService = dishService;
    }

    @Transactional
    public Map<String, Object> placeOrder(PlaceOrderRequest req) {
        Long userId = SecurityUtils.currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        validateMealLockWindow(req.getMealPeriod());

        List<Map<String, Object>> replaceLogs = new ArrayList<>();
        List<ResolvedItem> resolved = new ArrayList<>();
        List<StockHold> holds = new ArrayList<>();

        try {
            for (PlaceOrderRequest.Item item : req.getItems()) {
                Dish dish = dishMapper.selectById(item.getDishId());
                if (dish == null || dish.getStatus() != 1) {
                    throw new BizException("菜品不可用: " + item.getDishId());
                }
                dietGuardService.assertDishAllowed(userId, dish);

                ensureRedisStock(dish);
                Long remain = redisTemplate.execute(stockDeductScript,
                        List.of(DishService.stockKey(dish.getId())), String.valueOf(item.getQuantity()));
                Dish finalDish = dish;
                Long replacedFrom = null;

                if (remain != null && remain == -1) {
                    Dish sub = dishMapper.findSubstitute(dish.getId(), req.getMealPeriod());
                    if (sub == null) {
                        throw new BizException("菜品库存不足且无替代: " + dish.getName());
                    }
                    dietGuardService.assertDishAllowed(userId, sub);
                    ensureRedisStock(sub);
                    Long subRemain = redisTemplate.execute(stockDeductScript,
                            List.of(DishService.stockKey(sub.getId())), String.valueOf(item.getQuantity()));
                    if (subRemain == null || subRemain < 0) {
                        throw new BizException("替代菜品库存也不足: " + sub.getName());
                    }
                    holds.add(new StockHold(sub.getId(), item.getQuantity()));
                    Map<String, Object> logItem = new LinkedHashMap<>();
                    logItem.put("from", dish.getName());
                    logItem.put("to", sub.getName());
                    logItem.put("reason", "库存不足自动替换");
                    replaceLogs.add(logItem);
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
                resolved.add(new ResolvedItem(finalDish, item.getQuantity(), unitCal, unitProtein, replacedFrom));

                finalDish.setStock(Math.max(0, finalDish.getStock() - item.getQuantity()));
                dishMapper.updateById(finalDish);
                dishService.checkLowStockAndPush(finalDish.getId());
            }
        } catch (RuntimeException ex) {
            rollbackHolds(holds);
            throw ex;
        }

        Map<Long, List<ResolvedItem>> byStall = resolved.stream()
                .collect(Collectors.groupingBy(ri -> ri.dish.getStallId()));

        LocalDateTime expectPickup = resolveExpectPickup(req.getMealPeriod());
        List<Map<String, Object>> createdOrders = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int grandCalorie = 0;

        for (Map.Entry<Long, List<ResolvedItem>> entry : byStall.entrySet()) {
            BigDecimal total = BigDecimal.ZERO;
            int totalCalorie = 0;
            for (ResolvedItem ri : entry.getValue()) {
                total = total.add(ri.dish.getPrice().multiply(BigDecimal.valueOf(ri.quantity)));
                totalCalorie += ri.calorie * ri.quantity;
            }
            grandTotal = grandTotal.add(total);
            grandCalorie += totalCalorie;

            Order order = new Order();
            order.setOrderNo(genOrderNo());
            order.setUserId(userId);
            order.setStallId(entry.getKey());
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

            for (ResolvedItem ri : entry.getValue()) {
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

            Map<String, Object> one = new LinkedHashMap<>();
            one.put("orderNo", order.getOrderNo());
            one.put("orderId", order.getId());
            one.put("stallId", order.getStallId());
            one.put("pickupCode", order.getPickupCode());
            one.put("totalAmount", order.getTotalAmount());
            one.put("totalCalorie", order.getTotalCalorie());
            one.put("expectPickupAt", order.getExpectPickupAt());
            one.put("status", order.getStatus());
            createdOrders.add(one);
        }

        boolean autoPay = req.getPayChannel() != null;
        List<Map<String, Object>> paidResults = new ArrayList<>();
        if (autoPay) {
            if ("BALANCE".equalsIgnoreCase(req.getPayChannel())
                    && user.getBalance().compareTo(grandTotal) < 0) {
                for (Map<String, Object> o : createdOrders) {
                    cancelAndRelease((String) o.get("orderNo"), "余额不足自动取消");
                }
                throw new BizException("余额不足");
            }
            for (Map<String, Object> o : createdOrders) {
                paidResults.add(confirmPay((String) o.get("orderNo"), req.getPayChannel(), true));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> finalOrders = autoPay ? paidResults : createdOrders;
        result.put("orders", finalOrders);
        result.put("orderCount", finalOrders.size());
        result.put("totalAmount", grandTotal);
        result.put("totalCalorie", grandCalorie);
        result.put("replaceLogs", replaceLogs);
        result.put("lockHoursAhead", lockHoursAhead);
        result.put("status", autoPay ? "PAID" : "CREATED");
        if (!finalOrders.isEmpty()) {
            Map<String, Object> first = finalOrders.get(0);
            result.put("orderNo", first.get("orderNo"));
            result.put("orderId", first.get("orderId"));
            result.put("pickupCode", first.get("pickupCode"));
            result.put("expectPickupAt", first.get("expectPickupAt"));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> confirmPay(String orderNo, String channel, boolean success) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if ("PAID".equals(order.getStatus())) {
            Map<String, Object> dup = new LinkedHashMap<>();
            dup.put("orderNo", orderNo);
            dup.put("orderId", order.getId());
            dup.put("stallId", order.getStallId());
            dup.put("pickupCode", order.getPickupCode());
            dup.put("totalAmount", order.getTotalAmount());
            dup.put("totalCalorie", order.getTotalCalorie());
            dup.put("expectPickupAt", order.getExpectPickupAt());
            dup.put("status", "PAID");
            return dup;
        }
        if (!"CREATED".equals(order.getStatus())) {
            throw new BizException("订单状态不可支付: " + order.getStatus());
        }
        if (!success) {
            cancelAndRelease(orderNo, "支付失败");
            return Map.of("orderNo", orderNo, "status", "CANCELLED");
        }

        if ("BALANCE".equalsIgnoreCase(channel)) {
            User user = userMapper.selectById(order.getUserId());
            if (user.getBalance().compareTo(order.getTotalAmount()) < 0) {
                cancelAndRelease(orderNo, "余额不足");
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("orderId", order.getId());
        result.put("stallId", order.getStallId());
        result.put("pickupCode", order.getPickupCode());
        result.put("totalAmount", order.getTotalAmount());
        result.put("totalCalorie", order.getTotalCalorie());
        result.put("expectPickupAt", order.getExpectPickupAt());
        result.put("status", order.getStatus());
        return result;
    }

    public Map<String, Object> payCallback(String orderNo, String channel, boolean success) {
        return confirmPay(orderNo, channel, success);
    }

    /** 按订单号取消（系统内部调用：超时未支付、支付失败、余额不足）。 */
    @Transactional
    public Map<String, Object> cancelAndRelease(String orderNo, String reason) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            return Map.of("status", "NOT_FOUND");
        }
        return doCancel(order, reason, null);
    }

    /** 学生/教师主动取消自己的订单；后台角色可取消任意订单。 */
    @Transactional
    public Map<String, Object> cancelByUser(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        LoginUser current = SecurityUtils.currentUser();
        boolean staff = STAFF_ROLES.contains(current.getRole());
        if (!staff && !current.getId().equals(order.getUserId())) {
            throw new BizException(403, "无权取消他人订单");
        }
        String finalReason = (reason == null || reason.isBlank())
                ? (staff ? "档口取消订单" : "用户取消订单")
                : reason;
        if (!staff) {
            if (!USER_CANCELLABLE_STATUS.contains(order.getStatus())) {
                throw new BizException("订单已进入「" + statusName(order.getStatus())
                        + "」阶段，档口已投入备餐，无法自助取消；如有问题请联系档口工作人员处理");
            }
            return doCancel(order, finalReason, current.getId(), USER_CANCELLABLE_STATUS);
        }
        return doCancel(order, finalReason, current.getId());
    }

    /**
     * 统一的取消逻辑：回滚 Redis + DB 库存；已支付订单（PAID/PREPARING/READY）按支付渠道退款；
     * 已取餐（PICKED）订单不可取消。
     */
    private Map<String, Object> doCancel(Order order, String reason, Long operatorId) {
        return doCancel(order, reason, operatorId, CANCELLABLE_STATUS);
    }

    /** 带可取消状态集合的取消逻辑：学生侧与后台侧的取消权限不同。 */
    private Map<String, Object> doCancel(Order order, String reason, Long operatorId, Set<String> allowedStatus) {
        if ("CANCELLED".equals(order.getStatus())) {
            return cancelResult(order, "订单已取消", BigDecimal.ZERO);
        }
        if (!allowedStatus.contains(order.getStatus())) {
            throw new BizException("订单已进入「" + statusName(order.getStatus()) + "」阶段，不可取消");
        }
        boolean paid = PAID_STATUS.contains(order.getStatus());
        BigDecimal refundAmount = paid ? order.getTotalAmount() : BigDecimal.ZERO;
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
        if (paid) {
            refundOnCancel(order);
        }
        order.setStatus("CANCELLED");
        order.setRemark(appendRemark(order.getRemark(), reason));
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        stallPushService.pushStatusChange(order);
        log.info("Order {} cancelled by {}: {}", order.getOrderNo(), operatorId == null ? "SYSTEM" : operatorId, reason);
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
            log.info("Order {} paid by {}, 模拟第三方原路退回 {} 元",
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

    /** 我的订单（带明细与可取消标记，前端无需二次请求）。 */
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

    /** 订单详情（学生查看自己的，后台角色可查看任意订单）。 */
    public Map<String, Object> orderDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        assertCanAccess(order);
        return orderRow(order, true);
    }

    /** 档口下拉选项（含待处理数量），供档口看板与运营大屏使用。 */
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

    private void assertCanAccess(Order order) {
        LoginUser current = SecurityUtils.currentUser();
        if (!STAFF_ROLES.contains(current.getRole()) && !current.getId().equals(order.getUserId())) {
            throw new BizException(403, "无权查看该订单");
        }
    }

    private Map<String, Object> orderRow(Order order, boolean withItems) {
        Map<String, Object> row = toStallBoardRow(order);
        row.put("userId", order.getUserId());
        row.put("mealPeriod", order.getMealPeriod());
        row.put("mealPeriodName", MEAL_PERIOD_NAMES.getOrDefault(order.getMealPeriod(), order.getMealPeriod()));
        row.put("payChannel", order.getPayChannel());
        row.put("statusName", statusName(order.getStatus()));
        row.put("totalCalorie", order.getTotalCalorie());
        row.put("abnormalReason", order.getAbnormalReason());
        row.put("createdAt", order.getCreatedAt());
        // cancellable：学生/教师自助取消入口（已出餐不可取消）
        row.put("cancellable", USER_CANCELLABLE_STATUS.contains(order.getStatus()));
        // staffCancellable：档口/后台强制取消入口（含已出餐的异常处置）
        row.put("staffCancellable", CANCELLABLE_STATUS.contains(order.getStatus()));
        row.put("urgent", order.getUrgeCount() != null && order.getUrgeCount() > 0);
        if (withItems) {
            row.put("items", orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getOrderId, order.getId())));
        }
        return row;
    }

    private String statusName(String status) {
        return STATUS_NAMES.getOrDefault(status, status);
    }

    public List<OrderItem> orderItems(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        Long uid = SecurityUtils.currentUserId();
        String role = SecurityUtils.currentUser().getRole();
        boolean staff = STAFF_ROLES.contains(role);
        if (!staff && !uid.equals(order.getUserId())) {
            throw new BizException(403, "无权查看");
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

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

    /** 档口接单/出餐/异常：带状态机校验，非法流转直接拒绝。 */
    @Transactional
    public Map<String, Object> updateStatus(Long orderId, String status, String abnormalReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        String target = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(target)) {
            return doCancel(order, "档口取消：" + (abnormalReason == null || abnormalReason.isBlank() ? "无" : abnormalReason),
                    SecurityUtils.currentUserId());
        }
        String from = order.getStatus();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
            throw new BizException("订单状态不允许从「" + statusName(from) + "」变更为「" + statusName(target) + "」");
        }
        order.setStatus(target);
        if ("ABNORMAL".equals(target)) {
            order.setAbnormalFlag(1);
            order.setAbnormalReason(abnormalReason == null || abnormalReason.isBlank() ? "档口标记异常" : abnormalReason);
        } else if (order.getAbnormalFlag() != null && order.getAbnormalFlag() == 1) {
            order.setAbnormalFlag(0);
            order.setAbnormalReason(null);
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        stallPushService.pushStatusChange(order);
        return toStallBoardRow(order);
    }

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
        long remainSeconds = 0;
        if (order.getExpectPickupAt() != null) {
            remainSeconds = Math.max(0, java.time.Duration.between(LocalDateTime.now(), order.getExpectPickupAt()).getSeconds());
        }
        row.put("remainSeconds", remainSeconds);
        return row;
    }

    private String appendRemark(String old, String reason) {
        if (old == null || old.isBlank()) return reason;
        return old + " | " + reason;
    }

    /**
     * 预计取餐时间 = 该餐段开餐时间（已过则顺延为「当前时间 + 30 分钟」），
     * 与锁单窗口语义保持一致，档口倒计时才有实际意义。
     */
    private LocalDateTime resolveExpectPickup(String mealPeriod) {
        LocalTime start = switch (mealPeriod == null ? "" : mealPeriod.toUpperCase(Locale.ROOT)) {
            case "BREAKFAST" -> LocalTime.of(6, 30);
            case "LUNCH" -> LocalTime.of(11, 0);
            case "DINNER" -> LocalTime.of(17, 0);
            case "NIGHT" -> LocalTime.of(21, 0);
            default -> null;
        };
        LocalDateTime now = LocalDateTime.now();
        if (start == null) {
            return now.plusMinutes(30);
        }
        LocalDateTime mealStart = LocalDateTime.of(LocalDate.now(), start);
        return mealStart.isAfter(now) ? mealStart : now.plusMinutes(30);
    }

    private void validateMealLockWindow(String mealPeriod) {
        LocalTime start = switch (mealPeriod == null ? "" : mealPeriod.toUpperCase(Locale.ROOT)) {
            case "BREAKFAST" -> LocalTime.of(6, 30);
            case "LUNCH" -> LocalTime.of(11, 0);
            case "DINNER" -> LocalTime.of(17, 0);
            case "NIGHT" -> LocalTime.of(21, 0);
            default -> null;
        };
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
        int targetProtein = 70;
        HealthProfile profile = healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId));
        if (profile != null && profile.getTargetProtein() != null) {
            targetProtein = profile.getTargetProtein();
        }
        if (daily == null) {
            daily = new NutritionDaily();
            daily.setUserId(userId);
            daily.setStatDate(today);
            daily.setCalorie(calorie);
            daily.setProtein(protein);
            daily.setFat(BigDecimal.ZERO);
            daily.setCarb(BigDecimal.ZERO);
            daily.setProteinRate(protein.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(targetProtein), 2, java.math.RoundingMode.HALF_UP));
            nutritionDailyMapper.insert(daily);
        } else {
            daily.setCalorie(daily.getCalorie() + calorie);
            daily.setProtein(daily.getProtein().add(protein));
            daily.setProteinRate(daily.getProtein().multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(targetProtein), 2, java.math.RoundingMode.HALF_UP));
            nutritionDailyMapper.updateById(daily);
        }
    }

    private JsonNode readNutrition(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
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
