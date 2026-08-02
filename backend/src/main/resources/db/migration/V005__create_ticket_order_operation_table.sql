-- OpenSpec: ticketing-transaction-flow
-- A-owned forward migration for durable write-operation idempotency; no physical foreign keys.

CREATE TABLE `ticket_order_operation` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `action` VARCHAR(24) NOT NULL,
    `idempotency_key` VARCHAR(64) NOT NULL,
    `order_id` BIGINT NOT NULL,
    `order_no_snapshot` VARCHAR(32) NOT NULL,
    `parameter_hash` CHAR(64) NOT NULL,
    `result_status` VARCHAR(24) NOT NULL,
    `result_version` INT NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_operation_user_action_key` (`user_id`, `action`, `idempotency_key`),
    KEY `idx_order_operation_order_action` (`order_id`, `action`),
    CONSTRAINT `chk_order_operation_result_version`
        CHECK (`result_version` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
