/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.BalanceLedger;

import org.apache.ibatis.annotations.Mapper;

/**
 * 消费账户流水数据访问层。
 *
 * @since 2026-09-15
 */
@Mapper
public interface BalanceLedgerMapper extends BaseMapper<BalanceLedger> {
}
