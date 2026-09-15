package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
