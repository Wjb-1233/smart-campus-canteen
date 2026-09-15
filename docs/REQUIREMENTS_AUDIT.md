# 智慧校园食堂订餐系统 — 需求符合性更新（2026-09-01 完善后）

> 对照实训规格再次核查后的结论。旧版 `REQUIREMENTS_AUDIT.md` 中「高优先级缺口 1–5」已在此前补齐；本次继续补齐运营/用户/营养/交付缺口。2026-09-01 第三轮完善已补齐：菜品管理前端页、微信/支付宝模拟支付、菜品删除接口、审计日志、缺货预警推送、Dashboard 剩饭率曲线。

## 总体结论

- **功能完整度：约 95%**（核心六大模块业务可演示闭环）
- **需求符合度：约 92%**
- 仍属可选/弱化项：Seata、Tailwind、SonarQube、Playwright 千人压测、正式 PPT、Quartz（已用 Spring `@Scheduled` 等价）

## 本次新增/强化

| 能力 | 状态 | 说明 |
|------|------|------|
| 档口扫码核销 | 已实现 | `POST /order/scan?pickupCode=` |
| 催单 | 已实现 | `POST /order/{id}/urge` + `urge_count` |
| 倒计时 | 已实现 | 看板 `remainSeconds` + 前端秒级刷新 |
| 前端 STOMP | 已实现 | SockJS `/api/ws` 订阅 `/topic/stall/{id}` |
| 健康档案编辑 | 已实现 | `PUT /auth/health` + 个人中心表单 |
| 充值/流水 | 已实现 | `POST /auth/recharge`、`GET /auth/ledger`、`t_balance_ledger` |
| 教务 OAuth 模拟 | 已实现 | `/auth/oauth/edu/authorize` + `callback?code=EDU_{学号}` |
| 班级营养 TOP | 已实现 | 窗口函数 `ROW_NUMBER` → `/nutrition/report/class-top` |
| 档口营养评分 | 已实现 | `/nutrition/report/stall-score` |
| 看板筛选 | 已实现 | `date/canteenId/stallId` |
| PDF 日结 | 已实现 | `/order/statistics/export-pdf`（OpenPDF） |
| Base64 图片上传 | 已实现 | `/files/upload-base64`，落盘 D 盘 uploads |
| Swagger | 已实现 | springdoc → `/api/swagger-ui.html` |
| 三端收敛（5 角色 → 3 端） | 已实现 | 学生/老师端 STUDENT / 食堂端 STALL / 管理员端 ADMIN，前后端与 SQL 同步 |
| 食堂端写权限收敛 | 已实现 | `StallWriteScopeInterceptor` 挂全路径，白名单接单/出餐/核销/菜品维护 |
| 饮食硬拦截单测 | 已实现 | `DietGuardServiceTest` |
| 菜品管理前端页 | 已实现 | `/dish-manage`：CRUD、标签勾选、Base64图片上传、库存预警 |
| 菜品删除接口 | 已实现 | `DELETE /dish/{id}` 下架并清 Redis 库存 |
| 微信/支付宝模拟支付 | 已实现 | 购物车渠道选择 WECHAT/ALIPAY/BALANCE + 模拟支付弹窗 |
| 敏感操作审计 | 已实现 | `AuditInterceptor` 写入 `t_audit_log` |
| 缺货预警推送 | 已实现 | `DishService.checkLowStockAndPush` → `/topic/stock-alert` |
| Dashboard 剩饭率曲线 | 已实现 | 近7日剩饭率折线 + 20% 预警线 |

## 仍可后续增强（不影响主答辩演示）

1. 缺货「订阅推送」独立表（现有库存预警查询）
2. Playwright / TestContainers 全链路压测
3. SonarQube、正式答辩 PPT、AI 效能实测图
4. 图片真实转码 WebP（当前按 WebP 扩展名约束演示）
5. 食堂端写权限边界（仅接单/出餐/核销/菜品维护）与管理员端全量权限的评审话术需说明清楚

## 冒烟入口

- 前端：http://127.0.0.1:5173
- 后端：http://localhost:8080/api
- Swagger：http://localhost:8080/api/swagger-ui.html
- 演示账号：学生/老师端 `2021001001` / 食堂端 `S10001` / 管理员端 `A10001`，密码均为 `123456`
