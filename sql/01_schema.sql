-- =============================================================================
-- 智慧校园食堂订餐系统 - 建表脚本 (MySQL 8 / utf8mb4)
-- =============================================================================
CREATE DATABASE IF NOT EXISTS campus_canteen DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_canteen;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS t_waste_record;
DROP TABLE IF EXISTS t_nutrition_daily;
DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_cart_item;
DROP TABLE IF EXISTS t_dish_tag;
DROP TABLE IF EXISTS t_dish;
DROP TABLE IF EXISTS t_stall;
DROP TABLE IF EXISTS t_canteen;
DROP TABLE IF EXISTS t_nutrition_tag;
DROP TABLE IF EXISTS t_health_profile;
DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS t_audit_log;

-- 用户
CREATE TABLE t_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  student_no    VARCHAR(32)  NOT NULL UNIQUE COMMENT '学号/工号',
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt密码',
  real_name     VARCHAR(64)  NOT NULL COMMENT '姓名',
  phone         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  role          VARCHAR(32)  NOT NULL COMMENT 'STUDENT(学生/老师端)/STALL(食堂端)/ADMIN(管理员端)',
  department    VARCHAR(64)  DEFAULT NULL COMMENT '院系',
  grade         VARCHAR(32)  DEFAULT NULL COMMENT '年级',
  balance       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '消费账户余额',
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_dept (department),
  INDEX idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 健康档案
CREATE TABLE t_health_profile (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL UNIQUE,
  allergy_json    JSON DEFAULT NULL COMMENT '过敏原列表',
  diet_taboo_json JSON DEFAULT NULL COMMENT '饮食禁忌',
  target_calorie  INT DEFAULT 2000 COMMENT '每日目标热量',
  target_protein  INT DEFAULT 70 COMMENT '每日目标蛋白质(g)',
  religion_tag    VARCHAR(32) DEFAULT NULL COMMENT '如HALAL',
  FOREIGN KEY (user_id) REFERENCES t_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康档案';

-- 食堂
CREATE TABLE t_canteen (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(64) NOT NULL,
  location    VARCHAR(128) DEFAULT NULL,
  open_time   VARCHAR(32) DEFAULT '06:30-21:30',
  status      TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食堂';

-- 档口
CREATE TABLE t_stall (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  canteen_id  BIGINT NOT NULL,
  name        VARCHAR(64) NOT NULL,
  type        VARCHAR(32) NOT NULL COMMENT 'MAIN/FLAVOR/LIGHT/DRINK',
  queue_count INT NOT NULL DEFAULT 0,
  status      TINYINT NOT NULL DEFAULT 1,
  FOREIGN KEY (canteen_id) REFERENCES t_canteen(id),
  INDEX idx_stall_canteen (canteen_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档口';

-- 营养/健康标签字典
CREATE TABLE t_nutrition_tag (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  code        VARCHAR(32) NOT NULL UNIQUE,
  name        VARCHAR(64) NOT NULL,
  category    VARCHAR(32) NOT NULL COMMENT 'NUTRITION/RELIGION/HEALTH/FLAVOR',
  description VARCHAR(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签字典';

-- 菜品
CREATE TABLE t_dish (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  stall_id        BIGINT NOT NULL,
  category_id     BIGINT DEFAULT NULL COMMENT '分类扩展',
  name            VARCHAR(64) NOT NULL,
  price           DECIMAL(8,2) NOT NULL,
  stock           INT NOT NULL DEFAULT 0,
  image_url       VARCHAR(512) DEFAULT NULL,
  nutrition_json  JSON DEFAULT NULL COMMENT '热量/蛋白质/脂肪/碳水等',
  nutrition_verified TINYINT NOT NULL DEFAULT 0 COMMENT '营养数据是否已核验 1是0否',
  verified_by     BIGINT DEFAULT NULL COMMENT '核验人(营养师)用户ID',
  verified_at     DATETIME DEFAULT NULL COMMENT '核验时间',
  meal_period     VARCHAR(32) NOT NULL DEFAULT 'LUNCH' COMMENT 'BREAKFAST/LUNCH/DINNER/NIGHT',
  heat_score      INT NOT NULL DEFAULT 0 COMMENT '热度',
  status          TINYINT NOT NULL DEFAULT 1 COMMENT '1上架0下架',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (stall_id) REFERENCES t_stall(id),
  INDEX idx_dish_category (category_id),
  INDEX idx_dish_stall_period (stall_id, meal_period),
  INDEX idx_dish_status (status),
  INDEX idx_dish_nutrition_verified (nutrition_verified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品';

CREATE TABLE t_dish_tag (
  dish_id BIGINT NOT NULL,
  tag_id  BIGINT NOT NULL,
  PRIMARY KEY (dish_id, tag_id),
  FOREIGN KEY (dish_id) REFERENCES t_dish(id),
  FOREIGN KEY (tag_id) REFERENCES t_nutrition_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品标签关联';

-- 购物车
CREATE TABLE t_cart_item (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  dish_id    BIGINT NOT NULL,
  quantity   INT NOT NULL DEFAULT 1,
  meal_period VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cart (user_id, dish_id, meal_period),
  FOREIGN KEY (user_id) REFERENCES t_user(id),
  FOREIGN KEY (dish_id) REFERENCES t_dish(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- 订单 (状态机: CREATED->PAID->PREPARING->READY->PICKED / CANCELLED / ABNORMAL)
CREATE TABLE t_order (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no        VARCHAR(32) NOT NULL UNIQUE,
  user_id         BIGINT NOT NULL,
  stall_id        BIGINT NOT NULL,
  meal_period     VARCHAR(32) NOT NULL,
  total_amount    DECIMAL(10,2) NOT NULL,
  total_calorie   INT NOT NULL DEFAULT 0,
  status          VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  pay_channel     VARCHAR(32) DEFAULT NULL COMMENT 'WECHAT/ALIPAY/BALANCE',
  pickup_code     VARCHAR(16) DEFAULT NULL,
  expect_pickup_at DATETIME DEFAULT NULL,
  remark          VARCHAR(255) DEFAULT NULL,
  abnormal_flag   TINYINT NOT NULL DEFAULT 0,
  abnormal_reason VARCHAR(255) DEFAULT NULL,
  urge_count      INT NOT NULL DEFAULT 0 COMMENT '催单次数',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES t_user(id),
  FOREIGN KEY (stall_id) REFERENCES t_stall(id),
  INDEX idx_order_created (created_at),
  INDEX idx_order_user_status (user_id, status),
  INDEX idx_order_stall_status (stall_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

CREATE TABLE t_order_item (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT NOT NULL,
  dish_id       BIGINT NOT NULL,
  dish_name     VARCHAR(64) NOT NULL,
  unit_price    DECIMAL(8,2) NOT NULL COMMENT '价格快照',
  quantity      INT NOT NULL,
  calorie       INT NOT NULL DEFAULT 0,
  protein       DECIMAL(8,2) NOT NULL DEFAULT 0,
  replaced_from BIGINT DEFAULT NULL COMMENT '库存不足时的原菜品ID',
  FOREIGN KEY (order_id) REFERENCES t_order(id),
  FOREIGN KEY (dish_id) REFERENCES t_dish(id),
  INDEX idx_item_dish (dish_id),
  INDEX idx_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- 营养日聚合
CREATE TABLE t_nutrition_daily (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  stat_date       DATE NOT NULL,
  calorie         INT NOT NULL DEFAULT 0,
  protein         DECIMAL(8,2) NOT NULL DEFAULT 0,
  fat             DECIMAL(8,2) NOT NULL DEFAULT 0,
  carb            DECIMAL(8,2) NOT NULL DEFAULT 0,
  protein_rate    DECIMAL(5,2) DEFAULT NULL COMMENT '蛋白质达标率%',
  UNIQUE KEY uk_user_date (user_id, stat_date),
  FOREIGN KEY (user_id) REFERENCES t_user(id),
  INDEX idx_nutrition_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人营养日统计';

-- 剩饭浪费记录
CREATE TABLE t_waste_record (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id    BIGINT NOT NULL,
  dish_id     BIGINT NOT NULL,
  waste_ratio DECIMAL(5,2) NOT NULL COMMENT '估计浪费比例0-1',
  reason      VARCHAR(128) DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (order_id) REFERENCES t_order(id),
  FOREIGN KEY (dish_id) REFERENCES t_dish(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浪费溯源';

CREATE TABLE t_audit_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT DEFAULT NULL,
  action      VARCHAR(64) NOT NULL,
  detail      VARCHAR(512) DEFAULT NULL,
  ip          VARCHAR(64) DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

CREATE TABLE t_balance_ledger (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT NOT NULL,
  change_amt    DECIMAL(10,2) NOT NULL COMMENT '正充值负消费',
  balance_after DECIMAL(10,2) NOT NULL,
  biz_type      VARCHAR(32) NOT NULL COMMENT 'RECHARGE/ORDER_PAY/REFUND',
  biz_no        VARCHAR(64) DEFAULT NULL,
  remark        VARCHAR(255) DEFAULT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES t_user(id),
  INDEX idx_ledger_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费账户流水';

SET FOREIGN_KEY_CHECKS = 1;
