/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.util;

/**
 * 敏感信息脱敏工具。
 *
 * @since 2026-09-15
 */
public final class MaskUtil {
    private MaskUtil() {}

    /**
     * 对手机号做脱敏，仅保留前 3 位与后 4 位。
     *
     * @param phone 原始手机号，允许为 null
     * @return 脱敏后的手机号；入参为 null 或长度不足 7 位时原样返回
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
