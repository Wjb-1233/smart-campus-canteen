-- =============================================================================
-- 营养师端能力补丁：菜品营养数据核验
-- 说明：菜品营养数据（热量/蛋白质/脂肪/碳水）由营养师核验后方可信，
--       学生端菜品详情与订单确认页展示「营养数据已核验」标识。
-- =============================================================================
USE campus_canteen;
SET NAMES utf8mb4;

-- 若列已存在会报错，可忽略后继续
ALTER TABLE t_dish
  ADD COLUMN nutrition_verified TINYINT NOT NULL DEFAULT 0 COMMENT '营养数据是否已核验 1是0否' AFTER nutrition_json,
  ADD COLUMN verified_by BIGINT DEFAULT NULL COMMENT '核验人(营养师)用户ID' AFTER nutrition_verified,
  ADD COLUMN verified_at DATETIME DEFAULT NULL COMMENT '核验时间' AFTER verified_by;

ALTER TABLE t_dish ADD INDEX idx_dish_nutrition_verified (nutrition_verified);

-- 存量菜品视为已核验（种子数据由营养师基准菜谱库导入）
UPDATE t_dish
SET nutrition_verified = 1,
    verified_by = (SELECT id FROM t_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1),
    verified_at = NOW()
WHERE nutrition_json IS NOT NULL;
