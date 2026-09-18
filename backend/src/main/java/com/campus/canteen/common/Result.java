/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.common;

import lombok.Data;

/**
 * 统一接口响应包装，约定 code/message/data 三段结构。
 *
 * @since 2026-09-15
 */
@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    /**
     * 构造带数据的成功响应。
     *
     * @param data 业务数据，允许为 null
     * @param <T>  业务数据类型
     * @return 成功响应，code 固定为 0
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    /**
     * 构造不带数据的成功响应。
     *
     * @param <T> 业务数据类型
     * @return 成功响应，data 为 null
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应，错误码默认为 400。
     *
     * @param message 错误提示
     * @param <T>     业务数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(String message) {
        return fail(400, message);
    }

    /**
     * 构造带指定错误码的失败响应。
     *
     * @param code    业务错误码
     * @param message 错误提示
     * @param <T>     业务数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
