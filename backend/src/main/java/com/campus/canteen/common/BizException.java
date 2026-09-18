/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.common;

/**
 * 业务异常，携带业务错误码，由全局异常处理器统一转换为接口响应。
 *
 * @since 2026-09-15
 */
public class BizException extends RuntimeException {
    private final int code;

    /**
     * 构造业务异常，错误码默认为 400。
     *
     * @param message 错误提示
     */
    public BizException(String message) {
        this(400, message);
    }

    /**
     * 构造带指定错误码的业务异常。
     *
     * @param code    业务错误码
     * @param message 错误提示
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码。
     *
     * @return 业务错误码
     */
    public int getCode() {
        return code;
    }
}
