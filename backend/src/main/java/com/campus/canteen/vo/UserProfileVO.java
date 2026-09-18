/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户资料视图对象，聚合账户、消费与营养信息。
 *
 * @since 2026-09-15
 */
@Data
@Builder
public class UserProfileVO {
    private Long id;
    private String studentNo;
    private String realName;
    private String phoneMasked;
    private String role;
    private String department;
    private String grade;
    private BigDecimal balance;
    private BigDecimal weekSpend;
    private BigDecimal nutritionRate;
    private List<String> allergies;
    private Map<String, Object> health;
}
