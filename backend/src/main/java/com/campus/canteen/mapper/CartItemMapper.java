/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.CartItem;

import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车数据访问层。
 *
 * @since 2026-09-15
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
