/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 应用通用 Bean 配置，提供密码加密器与认证管理器。
 *
 * @since 2026-09-15
 */
@Configuration
public class AppConfig {
    /**
     * 提供字符串 Redis 操作模板。
     *
     * @param factory Redis 连接工厂
     * @return 字符串 Redis 模板
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * 加载库存扣减 Lua 脚本，保证扣减的原子性。
     *
     * @return 返回扣减后剩余库存的脚本对象
     */
    @Bean
    public DefaultRedisScript<Long> stockDeductScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_deduct.lua")));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 加载库存回滚 Lua 脚本，用于取消订单时归还库存。
     *
     * @return 返回回滚后剩余库存的脚本对象
     */
    @Bean
    public DefaultRedisScript<Long> stockRestoreScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_restore.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
