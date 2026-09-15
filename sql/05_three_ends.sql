-- =============================================================================
-- 角色收敛补丁：5 角色 → 3 端
--   学生/老师端（订餐/吃饭）      STUDENT
--   食堂端（档口接单/出餐/核销）   STALL
--   管理员端（运营看板/营养核验/审计）ADMIN
--
-- 原角色映射：
--   STUDENT / TEACHER            → STUDENT（教师同样在食堂用餐）
--   CANTEEN_ADMIN                → STALL
--   NUTRITIONIST / LOGISTICS     → ADMIN
--
-- 适用于已按旧脚本初始化过的库；全新初始化直接执行 01~04 即可。
-- 提示：后端启动时 DataInitializer 也会自动完成同样的角色归一化与演示账号补齐。
-- =============================================================================
USE campus_canteen;
SET NAMES utf8mb4;

-- 1) 教师并入学生/老师端
UPDATE t_user SET role = 'STUDENT' WHERE role = 'TEACHER';
-- 2) 营养师职责并入管理员端
UPDATE t_user SET role = 'ADMIN' WHERE role = 'NUTRITIONIST';
-- 3) 后勤主管职责并入管理员端
UPDATE t_user SET role = 'ADMIN' WHERE role = 'LOGISTICS';
-- 4) 食堂管理员改为食堂端
UPDATE t_user SET role = 'STALL' WHERE role = 'CANTEEN_ADMIN';

-- 5) 演示账号规整：食堂端主账号 S10001，管理员端主账号 A10001
UPDATE t_user SET student_no = 'S10001', real_name = '王师傅', department = '第一食堂' WHERE student_no = 'A10001';
UPDATE t_user SET student_no = 'A10001', real_name = '王管理员', department = '后勤处'   WHERE student_no = 'L10001';

-- 6) 角色列注释同步
ALTER TABLE t_user
  MODIFY COLUMN role VARCHAR(32) NOT NULL COMMENT 'STUDENT(学生/老师端)/STALL(食堂端)/ADMIN(管理员端)';

-- 7) 核验人兜底：食堂端不再承担营养核验，历史核验人统一归属管理员端
UPDATE t_dish SET verified_by = (SELECT id FROM t_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1)
WHERE verified_by IN (SELECT id FROM t_user WHERE role = 'STALL');

-- 校验：应只出现 STUDENT / STALL / ADMIN 三类
SELECT role, COUNT(*) AS user_count FROM t_user GROUP BY role;
