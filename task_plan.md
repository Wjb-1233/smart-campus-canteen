# 智慧校园食堂订餐系统 — 任务计划

## Goal
在 D:\smart-campus-canteen 交付可运行的全栈实训项目：SpringBoot3 + Vue3 + MySQL + Redis，覆盖用户/菜品/订餐/运营/营养/看板六大模块，含 SQL、Docker、AI 提示词与文档。

## Phases
| # | Phase | Status | Notes |
|---|-------|--------|-------|
| 1 | 目录与规划 | completed | D 盘项目根目录已创建 |
| 2 | 数据库脚本与 Docker | completed | schema + 种子数据 + compose |
| 3 | 后端 SpringBoot 核心 | completed | 鉴权、CRUD、库存、订单、推荐、看板 |
| 4 | 前端 Vue3 | completed | 登录、点餐、运营、营养、大屏 |
| 5 | AI 提示词与交付文档 | completed | prompts + README + 部署说明 |
| 6 | 联调自检 | completed | 前后端已跑通；冒烟 SMOKE_OK |
| 7 | 启动脚本与交付补齐 | completed | start-dev / smoke / AI 协作日志 |

## Decisions
- 项目路径：`D:\smart-campus-canteen`
- 推荐引擎：纯 Java 规则 + 简易协同过滤
- 支付：模拟回调 / 余额支付
- 库存：Redis Lua 原子扣减
- 前端：Vite + Vue3 + TS + Pinia + Element Plus + ECharts

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| Spring Boot 3.3 插件不支持 JDK25 | 2 | 升级至 Spring Boot 3.5.5 |
| seed SQL 中文乱码 | 2 | mysql --default-character-set=utf8mb4 |
| 本机 MySQL 密码非 root123 | 1 | 改为 123456 并同步 yml/docs |
