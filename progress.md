# Progress Log

## 2026-09-15（端名优化 + 登录跳转修复）
- 端名对外改为：**学生/老师**（原用餐端）、**食堂**（原出餐端）、管理员端不变
  - 前端：`router` 的 `ROLE_NAMES`、登录页快捷登录按钮与提示、`MainLayout` 角色徽标与工作台标签、`StallBoardView`/`NutritionConsoleView`/`dict.ts`/`user` store 文案
  - 文档与 SQL 注释同步（`README`、`docs/REQUIREMENTS_AUDIT`、`01_schema`、`05_three_ends`）
- 修复「食堂端账号 S10001 提示用户名或密码错误」与「管理员端登录成功却不跳转」：
  - 根因：数据库仍是旧 5 角色数据（`A10001` 为 `CANTEEN_ADMIN`、无 `S10001`），前端拿不到 `ADMIN` 角色导致路由守卫反复重定向
  - 后端 `DataInitializer` 增加启动自愈：历史角色归一化（`TEACHER→STUDENT`、`CANTEEN_ADMIN→STALL`、`NUTRITIONIST/LOGISTICS→ADMIN`）、账号规整（`A10001→S10001`、`L10001→A10001`）、三端演示账号缺失即补齐
  - 前端路由守卫增加兜底：角色非三端之一时清理登录态回登录页；目标已是该角色首页仍不匹配则放行，杜绝无限重定向
  - 数据侧执行 `sql/05_three_ends.sql` 完成历史库迁移
- 校验：前端 `vue-tsc + vite build` 通过；后端 `mvn compile` 通过

## 2026-09-15（角色收敛：5 角色 → 3 端）
- 角色精简为三端：学生/老师端 `STUDENT`、食堂端 `STALL`、管理员端 `ADMIN`
  - 教师并入学生/老师端；营养师、后勤主管并入管理员端；食堂管理员转为食堂端
- 后端：`SecurityConfig`（`/dashboard`、`/nutrition/report`、`/admin/audit` 仅 `ADMIN`）、各控制器 `@PreAuthorize("hasAnyRole('STALL','ADMIN')")`、`StallWriteScopeInterceptor` 收敛食堂端写接口
- 前端：路由 `ROLE_HOME/ROLE_NAMES/DINER_ROLES/STALL_ROLES/ADMIN_ROLES`、`MainLayout` 三套菜单、登录页三端演示账号（`2021001001`/`S10001`/`A10001`）、`user` store 的 `isDiner/isStallStaff/isAdmin`
- 营养工作台与菜品营养核验归属管理员端（`NutritionConsoleView`）
- SQL：`01_schema` 角色注释、`02_seed` 种子数据同步为三端，新增 `05_three_ends.sql` 供旧库迁移
- 校验：前端 `vue-tsc + vite build` 通过；后端 `mvn compile` 通过

## 2026-09-01（第三轮需求补齐）
- 菜品管理前端页 `/dish-manage`：CRUD、标签勾选、Base64 图片上传、库存预警
- 后端新增：`DELETE /dish/{id}`、`GET /dish/admin/list`、`GET /dish/stalls`
- 购物车支持微信/支付宝/余额模拟支付 + 支付弹窗 + 营养搭配建议弹窗
- 审计日志：`AuditInterceptor` 写入 `t_audit_log`（含用户、动作、IP、状态码）
- 缺货预警推送：库存 ≤10 时 WebSocket 广播 `/topic/stock-alert`
- Dashboard 渲染近7日剩饭率曲线（含 20% 预警线）
- 登录页新增教务 OAuth2 模拟授权登录按钮

## 2026-09-01（高优整改）
- 下单改为 CREATED 锁库存 → 支付确认 PAID；超时释放 Redis/DB 库存
- 按档口自动拆单；提前 N 小时锁单窗口校验
- DietGuard：过敏/禁忌/清真硬拦截（下单+推荐）
- 浪费登记 API + 看板真实剩饭率/曲线；登录写入 online:user Redis
- 前端购物车拆单提示、防重复提交、订单浪费反馈弹窗

