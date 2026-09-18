/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.canteen.entity.AuditLog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 操作审计日志数据访问层。
 *
 * @since 2026-09-15
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    /**
     * 查询审计日志列表并关联操作人信息，供管理员端「操作审计」页面使用。
     *
     * @param limit 返回条数上限
     * @return 含操作人姓名与角色的日志列表
     */
    @Select("""
            SELECT a.id AS id,
                   a.user_id AS userId,
                   u.real_name AS realName,
                   u.role AS role,
                   a.action AS action,
                   a.detail AS detail,
                   a.ip AS ip,
                   a.created_at AS createdAt
            FROM t_audit_log a
            LEFT JOIN t_user u ON u.id = a.user_id
            ORDER BY a.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listWithUser(int limit);
}
