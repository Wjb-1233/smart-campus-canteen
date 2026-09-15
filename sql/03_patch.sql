-- =============================================================================
-- 需求补齐补丁：催单字段、余额流水
-- =============================================================================
USE campus_canteen;
SET NAMES utf8mb4;

-- 若已存在 urge_count 会报错，可忽略后继续
ALTER TABLE t_order
  ADD COLUMN urge_count INT NOT NULL DEFAULT 0 COMMENT '催单次数' AFTER abnormal_reason;

CREATE TABLE IF NOT EXISTS t_balance_ledger (
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
