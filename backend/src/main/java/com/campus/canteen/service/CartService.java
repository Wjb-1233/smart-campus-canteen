package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.dto.CartAddRequest;
import com.campus.canteen.entity.CartItem;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.mapper.CartItemMapper;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartItemMapper cartItemMapper;
    private final DishMapper dishMapper;

    public void add(CartAddRequest req) {
        Long userId = SecurityUtils.currentUserId();
        Dish dish = dishMapper.selectById(req.getDishId());
        if (dish == null || dish.getStatus() != 1) {
            throw new BizException("菜品不存在或已下架");
        }
        String period = req.getMealPeriod() == null ? dish.getMealPeriod() : req.getMealPeriod();
        CartItem exist = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getDishId, req.getDishId())
                .eq(CartItem::getMealPeriod, period));
        if (exist == null) {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setDishId(req.getDishId());
            item.setQuantity(req.getQuantity() == null ? 1 : req.getQuantity());
            item.setMealPeriod(period);
            cartItemMapper.insert(item);
        } else {
            exist.setQuantity(exist.getQuantity() + (req.getQuantity() == null ? 1 : req.getQuantity()));
            cartItemMapper.updateById(exist);
        }
    }

    public List<Map<String, Object>> list() {
        Long userId = SecurityUtils.currentUserId();
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CartItem item : items) {
            Dish dish = dishMapper.selectById(item.getDishId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("dishId", item.getDishId());
            row.put("quantity", item.getQuantity());
            row.put("mealPeriod", item.getMealPeriod());
            if (dish != null) {
                row.put("name", dish.getName());
                row.put("price", dish.getPrice());
                row.put("nutritionJson", dish.getNutritionJson());
                row.put("stallId", dish.getStallId());
            }
            result.add(row);
        }
        return result;
    }

    public void clear() {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, SecurityUtils.currentUserId()));
    }

    public void remove(Long id) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, id)
                .eq(CartItem::getUserId, SecurityUtils.currentUserId()));
    }
}
