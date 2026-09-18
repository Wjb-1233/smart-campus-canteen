/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.common;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一把校验、鉴权与业务异常转换为接口响应。
 *
 * @since 2026-09-15
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，按业务错误码返回。
     *
     * @param e 业务异常
     * @return 带业务错误码的失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常，取首个字段错误提示返回。
     *
     * @param e 参数校验异常
     * @return 参数校验失败响应
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            msg = manv.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.fail(msg);
    }

    /**
     * 处理账号密码错误，返回 401。
     *
     * @param e 认证失败异常
     * @return 401 失败响应
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.fail(401, "用户名或密码错误");
    }

    /**
     * 处理越权访问，返回 403。
     *
     * @param e 权限不足异常
     * @return 403 失败响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleDenied(AccessDeniedException e) {
        return Result.fail(403, "无权限访问");
    }

    /**
     * 兜底处理未预期异常，避免异常堆栈直接暴露给前端。
     *
     * @param e 未预期异常
     * @return 500 失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return Result.fail(500, "服务器内部错误: " + e.getMessage());
    }
}
