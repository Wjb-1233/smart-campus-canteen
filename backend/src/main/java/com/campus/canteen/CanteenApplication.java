/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智慧校园食堂订餐系统 Spring Boot 启动类。
 *
 * @since 2026-09-15
 */
@SpringBootApplication
@MapperScan("com.campus.canteen.mapper")
@EnableScheduling
public class CanteenApplication {
    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CanteenApplication.class, args);
    }
}
