# Findings

## Requirements Snapshot
- 三端收敛：学生/老师端（订餐/吃饭）、食堂端（档口接单/出餐/核销）、管理员端（运营看板/营养核验/审计）

## 角色映射（5 → 3）
- STUDENT / TEACHER → STUDENT（学生/老师端）
- CANTEEN_ADMIN → STALL（食堂端）
- NUTRITIONIST / LOGISTICS → ADMIN（管理员端）
- 多时段：早/中/晚/夜宵；多档口类型
- 核心能力：标签菜品、动态库存、混合推荐、订单状态机、营养分析、数据大屏
- 技术栈：SpringBoot 3.3、MyBatis-Plus、Security、Redis、Quartz、Vue3、Pinia、Element Plus、ECharts、MySQL8

## Implementation Choices
- JWT + BCrypt；Redis 缓存权限与库存计数
- 营养数据用 MySQL JSON
- WebSocket 档口推送（简化实现）
- 演示账号与种子菜品写入 SQL
