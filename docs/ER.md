# ER 图文字描述（第三范式）

## 实体关系
- 用户(t_user) 1:1 健康档案(t_health_profile)
- 食堂(t_canteen) 1:N 档口(t_stall)
- 档口 1:N 菜品(t_dish)
- 菜品 N:M 营养标签(t_nutrition_tag) —— 通过 t_dish_tag
- 用户 1:N 购物车项(t_cart_item) / 订单(t_order) / 营养日统计(t_nutrition_daily)
- 订单 1:N 订单明细(t_order_item)
- 订单/菜品 可产生浪费记录(t_waste_record)

## 规范化说明
- 价格快照落在 t_order_item，避免菜品改价影响历史订单（适度反范式）
- 营养明细用 JSON 存于菜品，标签字典独立，满足标签检索与展示分离
- 外键与索引见 sql/01_schema.sql
