/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.NutritionTag;
import com.campus.canteen.entity.Stall;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.NutritionTagMapper;
import com.campus.canteen.mapper.StallMapper;
import com.campus.canteen.vo.DishVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜品业务：查询、维护、库存扣减回滚与 Redis 库存缓存同步。
 *
 * @since 2026-09-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DishService {
    /** 库存预警阈值：库存小于等于该值时推送订阅。 */
    public static final int STOCK_ALERT_THRESHOLD = 10;

    private final DishMapper dishMapper;
    private final NutritionTagMapper tagMapper;
    private final StallMapper stallMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 生成菜品库存的 Redis 缓存键。
     *
     * @param dishId 菜品 ID
     * @return Redis 键名
     */
    public static String stockKey(Long dishId) {
        return "dish:stock:" + dishId;
    }

    /**
     * 把数据库库存同步到 Redis 缓存。
     *
     * @param dishId 菜品 ID
     * @param stock  最新库存
     */
    public void syncStockToRedis(Long dishId, int stock) {
        redisTemplate.opsForValue().set(stockKey(dishId), String.valueOf(stock));
    }

    /**
     * 查询全部菜品（含已下架），供菜品管理页使用。
     *
     * @return 菜品视图对象列表
     */
    public List<DishVO> adminList() {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .orderByDesc(Dish::getUpdatedAt));
        Map<Long, String> stallNames = stallNames();
        return dishes.stream().map(d -> toVO(d, stallNames.get(d.getStallId()))).toList();
    }

    /**
     * 按关键词、供餐时段与标签检索菜品。
     *
     * @param keyword    菜品名称关键词，可为空
     * @param mealPeriod 供餐时段，可为空
     * @param tags       标签编码集合，可为空
     * @return 命中的菜品列表
     */
    public List<DishVO> search(String keyword, String mealPeriod, List<String> tags) {
        List<String> tagCodes = tags == null ? List.of() : tags.stream().filter(StringUtils::hasText).toList();
        List<Dish> dishes = dishMapper.search(keyword, mealPeriod, tagCodes, tagCodes.size());
        Map<Long, String> stallNames = stallNames();
        return dishes.stream().map(d -> toVO(d, stallNames.get(d.getStallId()))).toList();
    }

    /**
     * 查询菜品详情。
     *
     * @param id 菜品 ID
     * @return 菜品视图对象
     * @throws BizException 菜品不存在时抛出
     */
    public DishVO detail(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        Stall stall = stallMapper.selectById(dish.getStallId());
        return toVO(dish, stall == null ? null : stall.getName());
    }

    /**
     * 新增或更新菜品，同时同步库存缓存、低库存推送与标签关联。
     *
     * @param dish 菜品信息
     * @return 保存后的菜品
     */
    public Dish saveOrUpdate(Dish dish) {
        if (dish.getId() == null) {
            dishMapper.insert(dish);
        } else {
            dishMapper.updateById(dish);
        }
        if (dish.getStock() != null) {
            syncStockToRedis(dish.getId(), dish.getStock());
            checkLowStockAndPush(dish.getId());
        }
        saveTags(dish.getId(), dish.getTagCodes());
        return dish;
    }

    /** 维护菜品标签关联：先清后插（tagCodes 为标签码列表）。 */
    private void saveTags(Long dishId, List<String> tagCodes) {
        if (dishId == null) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM t_dish_tag WHERE dish_id = ?", dishId);
            if (tagCodes == null || tagCodes.isEmpty()) {
                return;
            }
            List<NutritionTag> all = tagMapper.selectList(null);
            Map<String, Long> codeToId = all.stream()
                    .collect(Collectors.toMap(NutritionTag::getCode, NutritionTag::getId, (a, b) -> a));
            for (String code : tagCodes) {
                Long tagId = codeToId.get(code);
                if (tagId != null) {
                    jdbcTemplate.update("INSERT INTO t_dish_tag (dish_id, tag_id) VALUES (?, ?)", dishId, tagId);
                }
            }
        } catch (DataAccessException e) {
            log.warn("saveTags failed: {}", e.getMessage());
        }
    }

    /**
     * 低库存预警：库存小于等于阈值时向 /topic/stock-alert 广播。
     *
     * @param dishId 菜品 ID
     */
    public void checkLowStockAndPush(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || dish.getStatus() == null || dish.getStatus() != 1) {
            return;
        }
        Integer stock = dish.getStock() == null ? 0 : dish.getStock();
        if (stock > STOCK_ALERT_THRESHOLD) {
            return;
        }
        String stockLabel = stock <= 0 ? "，已售罄" : "（剩余" + stock + "份）";
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "STOCK_ALERT");
        payload.put("dishId", dish.getId());
        payload.put("name", dish.getName());
        payload.put("stock", stock);
        payload.put("level", stock <= 0 ? "CRITICAL" : "WARN");
        payload.put("message", "菜品「" + dish.getName() + "」库存不足" + stockLabel);
        try {
            messagingTemplate.convertAndSend("/topic/stock-alert", payload);
            log.info("Stock alert push: {}", dish.getName());
        } catch (MessagingException e) {
            log.warn("Stock alert push failed: {}", e.getMessage());
        }
    }

    /**
     * 查询全部营养/健康标签。
     *
     * @return 按分类排序的标签列表
     */
    public List<NutritionTag> allTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<NutritionTag>().orderByAsc(NutritionTag::getCategory));
    }

    /**
     * 查询档口下拉选项。
     *
     * @return 含档口 ID、名称、类型与所属食堂的列表
     */
    public List<Map<String, Object>> stallOptions() {
        return stallMapper.selectList(new LambdaQueryWrapper<Stall>().eq(Stall::getStatus, 1)).stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("type", s.getType());
                    m.put("canteenId", s.getCanteenId());
                    return m;
                }).toList();
    }

    /**
     * 下架菜品并清理缓存（保留历史订单引用，不做物理删除）。
     *
     * @param id 菜品 ID
     * @throws BizException 菜品不存在时抛出
     */
    public void delete(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        dish.setStatus(0);
        dishMapper.updateById(dish);
        try {
            jdbcTemplate.update("DELETE FROM t_dish_tag WHERE dish_id = ?", id);
        } catch (DataAccessException e) {
            // 标签关联清理失败不影响下架主流程
            log.warn("clean dish tags failed: {}", e.getMessage());
        }
        redisTemplate.delete(stockKey(id));
    }

    /**
     * 查询库存低于阈值的菜品，用于缺货预警。
     *
     * @param threshold 库存阈值
     * @return 低库存菜品列表
     */
    public List<Map<String, Object>> lowStockAlerts(int threshold) {
        List<Dish> low = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, 1)
                .le(Dish::getStock, threshold));
        return low.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dishId", d.getId());
            m.put("name", d.getName());
            m.put("stock", d.getStock());
            m.put("level", d.getStock() <= 0 ? "CRITICAL" : "WARN");
            return m;
        }).toList();
    }

    /**
     * 解析菜品的营养 JSON，供推荐引擎与营养分析复用。
     *
     * @param dish 菜品
     * @return 营养数据，格式异常时返回空 Map
     */
    public Map<String, Object> nutritionOf(Dish dish) {
        return readNutrition(dish == null ? null : dish.getNutritionJson());
    }

    private Map<Long, String> stallNames() {
        return stallMapper.selectList(null).stream()
                .collect(Collectors.toMap(Stall::getId, Stall::getName, (a, b) -> a));
    }

    private DishVO toVO(Dish d, String stallName) {
        String redisStock = redisTemplate.opsForValue().get(stockKey(d.getId()));
        int stock = redisStock != null ? Integer.parseInt(redisStock) : (d.getStock() == null ? 0 : d.getStock());
        String hint = stock <= 0 ? "已售罄" : stock <= 10 ? "余量紧张" : "库存充足";
        List<NutritionTag> tagList = tagMapper.listByDishId(d.getId());
        List<String> tags = tagList.stream().map(NutritionTag::getName).toList();
        List<String> tagCodes = tagList.stream().map(NutritionTag::getCode).toList();
        return DishVO.builder()
                .id(d.getId())
                .name(d.getName())
                .price(d.getPrice())
                .stock(stock)
                .stockHint(hint)
                .imageUrl(d.getImageUrl())
                .mealPeriod(d.getMealPeriod())
                .heatScore(d.getHeatScore())
                .stallId(d.getStallId())
                .stallName(stallName)
                .nutrition(readNutrition(d.getNutritionJson()))
                .tags(tags)
                .tagCodes(tagCodes)
                .nutritionVerified(d.getNutritionVerified() != null && d.getNutritionVerified() == 1)
                .build();
    }

    private Map<String, Object> readNutrition(String json) {
        try {
            if (json == null) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
