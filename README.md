# 智慧校园食堂订餐系统

基于 **Spring Boot 3.5 + Vue3 + MySQL 8 + Redis** 的高校智慧食堂实训项目，覆盖用户中心、菜品中心、智能订餐、食堂运营、营养分析、数据看板六大模块，并配套 AI Coding 提示词库与部署文档。

## 目录结构

```
D:\smart-campus-canteen
├── backend/          # Spring Boot 后端
├── frontend/         # Vue3 + TypeScript 前端
├── sql/              # 建表与种子数据
├── docker/           # MySQL + Redis Compose
├── ai-prompts/       # AI Coding 提示词模板
├── docs/             # 部署与答辩材料说明
└── README.md
```

## 环境要求

- JDK 25 运行时可编译目标为 17（已用 Spring Boot 3.5.5 适配）；推荐直接用本机 JDK25
- Maven 3.9+、Node.js 18+
- MySQL 8 / Redis 7（本机默认密码 `root/123456`；也可用 Docker Desktop）

## 一键启动（Windows）

```bat
D:\smart-campus-canteen\start-dev.bat
```

或分别启动：

```bash
# 导入数据（首次）
mysql --default-character-set=utf8mb4 -uroot -p123456 campus_canteen -e "SOURCE D:/smart-campus-canteen/sql/01_schema.sql"
mysql --default-character-set=utf8mb4 -uroot -p123456 campus_canteen -e "SOURCE D:/smart-campus-canteen/sql/02_seed.sql"

# 后端
cd D:\smart-campus-canteen\backend
mvn -DskipTests spring-boot:run
# 或：java -jar target\smart-canteen-1.0.0.jar

# 前端
cd D:\smart-campus-canteen\frontend
npm run dev
```

- 后端：`http://localhost:8080/api`
- 前端：`http://127.0.0.1:5173`
- 冒烟：`powershell -File D:\smart-campus-canteen\scripts\smoke-test.ps1`

系统共 **3 个端**：学生/老师端（订餐/吃饭）、食堂端（档口接单/出餐/核销）、管理员端（运营看板/营养核验/审计）。

| 账号 | 密码 | 端 |
|------|------|-----|
| 2021001001 | 123456 | 学生/老师端（学生） |
| T20210001 | 123456 | 学生/老师端（教师，与学生/老师端合并） |
| S10001 | 123456 | 食堂端（档口接单/出餐/核销） |
| A10001 | 123456 | 管理员端（运营/营养核验/审计） |
| L10001 | 123456 | 管理员端 |

> 应用启动时会将演示账号密码重置为 `123456`，并把菜品库存同步到 Redis。

## 核心能力对照

- JWT 鉴权 + 角色权限
- 菜品多标签组合检索（低脂/清真/主食等）
- Redis Lua 原子扣库存 + 库存不足同标签自动替换
- 规则 + 历史偏好混合推荐
- 订单状态机 + 档口看板
- 营养周报 / 院系蛋白质达标红色预警
- ECharts 数据看板 + Excel 日结导出
- WebSocket 档口订单推送（STOMP `/ws`）

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/profile` | 脱敏用户聚合画像 |
| GET | `/api/dish/search` | 标签组合搜索 |
| GET | `/api/dish/recommend` | 个性化推荐 |
| POST | `/api/order/place` | 下单（扣库存/替换） |
| GET | `/api/dashboard/overview` | 大屏数据 |
| GET | `/api/nutrition/report/dept-daily` | 院系营养日报 |
| GET | `/api/order/statistics/export` | 日结 Excel |

## 技术栈

后端：Spring Boot 3.3、MyBatis-Plus、Spring Security、JWT、Redis、WebSocket、Apache POI  
前端：Vue3、TypeScript、Pinia、Vue Router、Element Plus、ECharts、Vite  
数据库：MySQL 8（JSON 营养字段）

## 交付物建议

详见 `docs/` 与 `ai-prompts/`，可用于实训阶段评审与答辩。
