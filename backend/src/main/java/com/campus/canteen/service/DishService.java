package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.NutritionTag;
import com.campus.canteen.entity.Stall;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.NutritionTagMapper;
import com.campus.canteen.mapper.StallMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import com.campus.canteen.vo.DishVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishService {
    private final DishMapper dishMapper;
    private final NutritionTagMapper tagMapper;
    private final StallMapper stallMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    /** 库存预警阈值：库存 <= 该值时推送订阅 */
    public static final int STOCK_ALERT_THRESHOLD = 10;

    public static String stockKey(Long dishId) {
        return "dish:stock:" + dishId;
    }

    public void syncStockToRedis(Long dishId, int stock) {
        redisTemplate.opsForValue().set(stockKey(dishId), String.valueOf(stock));
    }

    /** 管理端：返回全部菜品（含下架），供 CRUD 管理页使用 */
    public List<DishVO> adminList() {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .orderByDesc(Dish::getUpdatedAt));
        Map<Long, String> stallNames = stallMapper.selectList(null).stream()
                .collect(Collectors.toMap(Stall::getId, Stall::getName, (a, b) -> a));
        return dishes.stream().map(d -> toVO(d, stallNames.get(d.getStallId()))).toList();
    }

    public List<DishVO> search(String keyword, String mealPeriod, List<String> tags) {
        List<String> tagCodes = tags == null ? List.of() : tags.stream().filter(StringUtils::hasText).toList();
        List<Dish> dishes = dishMapper.search(keyword, mealPeriod, tagCodes, tagCodes.size());
        Map<Long, String> stallNames = stallMapper.selectList(null).stream()
                .collect(Collectors.toMap(Stall::getId, Stall::getName, (a, b) -> a));
        return dishes.stream().map(d -> toVO(d, stallNames.get(d.getStallId()))).toList();
    }

    public DishVO detail(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        Stall stall = stallMapper.selectById(dish.getStallId());
        return toVO(dish, stall == null ? null : stall.getName());
    }

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

    /** 维护菜品标签关联：先清后插（tagCodes 为标签码列表） */
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
        } catch (Exception e) {
            log.warn("saveTags failed: {}", e.getMessage());
        }
    }

    /** 低库存订阅推送：库存 <= 阈值时向 /topic/stock-alert 广播 */
    public void checkLowStockAndPush(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || dish.getStatus() == null || dish.getStatus() != 1) {
            return;
        }
        Integer stock = dish.getStock() == null ? 0 : dish.getStock();
        if (stock <= STOCK_ALERT_THRESHOLD) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "STOCK_ALERT");
            payload.put("dishId", dish.getId());
            payload.put("name", dish.getName());
            payload.put("stock", stock);
            payload.put("level", stock <= 0 ? "CRITICAL" : "WARN");
            payload.put("message", "菜品「" + dish.getName() + "」库存不足" + (stock <= 0 ? "，已售罄" : "（剩余" + stock + "份）"));
            try {
                messagingTemplate.convertAndSend("/topic/stock-alert", payload);
                log.info("Stock alert push: {}", dish.getName());
            } catch (Exception e) {
                log.warn("Stock alert push failed: {}", e.getMessage());
            }
        }
    }

    public List<NutritionTag> allTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<NutritionTag>().orderByAsc(NutritionTag::getCategory));
    }

    /** 管理端：档口下拉选项 */
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

    /** 下架并清理 Redis 库存缓存（保留历史订单引用，不物理删除） */
    public void delete(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        dish.setStatus(0);
        dishMapper.updateById(dish);
        try {
            jdbcTemplate.update("DELETE FROM t_dish_tag WHERE dish_id = ?", id);
        } catch (Exception ignored) {
        }
        redisTemplate.delete(stockKey(id));
    }

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

    private DishVO toVO(Dish d, String stallName) {
        String redisStock = redisTemplate.opsForValue().get(stockKey(d.getId()));
        int stock = redisStock != null ? Integer.parseInt(redisStock) : d.getStock();
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

    /** 供推荐引擎、营养分析复用的营养 JSON 解析。 */
    public Map<String, Object> nutritionOf(Dish dish) {
        return readNutrition(dish == null ? null : dish.getNutritionJson());
    }

    private Map<String, Object> readNutrition(String json) {
        try {
            if (json == null) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
