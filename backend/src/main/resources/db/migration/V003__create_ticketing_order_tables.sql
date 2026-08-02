-- OpenSpec: ticketing-transaction-flow
-- This migration creates the transaction baseline only; it does not implement state transitions.
-- A confirmed the schema: keep FLYWAY_ENABLED=false until the controlled MySQL 8.4 validation starts.

CREATE TABLE `ticket_order` (
    `id` BIGINT NOT NULL,
    `order_no` VARCHAR(32) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `show_id` BIGINT NOT NULL,
    `ticket_count` INT NOT NULL,
    `unit_price` DECIMAL(10, 2) NOT NULL,
    `total_amount` DECIMAL(10, 2) NOT NULL,
    `status` VARCHAR(24) NOT NULL,
    `expire_time` DATETIME(3) NOT NULL,
    `client_request_id` VARCHAR(64) NOT NULL,
    `idempotency_key` VARCHAR(64) NOT NULL,
    `version` INT NOT NULL DEFAULT 0,
    `paid_time` DATETIME(3) NULL,
    `cancelled_time` DATETIME(3) NULL,
    `refunded_time` DATETIME(3) NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_order_user_request` (`user_id`, `client_request_id`),
    UNIQUE KEY `uk_order_user_idempotency` (`user_id`, `idempotency_key`),
    KEY `idx_order_user_time` (`user_id`, `create_time`),
    KEY `idx_order_user_status_time` (`user_id`, `status`, `create_time`),
    KEY `idx_order_status_expire` (`status`, `expire_time`),
    KEY `idx_order_show` (`show_id`),
    CONSTRAINT `chk_order_ticket_count`
        CHECK (`ticket_count` BETWEEN 1 AND 6),
    CONSTRAINT `chk_order_amount`
        CHECK (`unit_price` >= 0 AND `total_amount` = `unit_price` * `ticket_count`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `ticket_order_seat` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `show_seat_id` BIGINT NOT NULL,
    `row_no_snapshot` VARCHAR(8) NOT NULL,
    `seat_no_snapshot` VARCHAR(8) NOT NULL,
    `unit_price` DECIMAL(10, 2) NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_show_seat` (`order_id`, `show_seat_id`),
    KEY `idx_order_seat_order` (`order_id`),
    KEY `idx_order_seat_show_seat` (`show_seat_id`),
    CONSTRAINT `chk_order_seat_price`
        CHECK (`unit_price` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `mock_payment` (
    `id` BIGINT NOT NULL,
    `payment_no` VARCHAR(32) NOT NULL,
    `order_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(64) NOT NULL,
    `amount` DECIMAL(10, 2) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'INITIALIZED',
    `request_time` DATETIME(3) NOT NULL,
    `paid_time` DATETIME(3) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    UNIQUE KEY `uk_payment_order` (`order_id`),
    UNIQUE KEY `uk_payment_idempotency` (`idempotency_key`),
    CONSTRAINT `chk_payment_amount`
        CHECK (`amount` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `electronic_ticket` (
    `id` BIGINT NOT NULL,
    `ticket_code` VARCHAR(48) NOT NULL,
    `order_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'VALID',
    `qr_payload` VARCHAR(255) NOT NULL,
    `issued_time` DATETIME(3) NOT NULL,
    `invalidated_time` DATETIME(3) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_code` (`ticket_code`),
    UNIQUE KEY `uk_ticket_order` (`order_id`),
    KEY `idx_ticket_user_status` (`user_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `refund_request` (
    `id` BIGINT NOT NULL,
    `refund_no` VARCHAR(32) NOT NULL,
    `order_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(64) NOT NULL,
    `action_id` VARCHAR(64) NULL,
    `reason` VARCHAR(255) NULL,
    `impact_snapshot` JSON NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    `request_time` DATETIME(3) NOT NULL,
    `processed_time` DATETIME(3) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_refund_order` (`order_id`),
    UNIQUE KEY `uk_refund_user_idempotency` (`user_id`, `idempotency_key`),
    KEY `idx_refund_user_time` (`user_id`, `create_time`),
    KEY `idx_refund_action` (`action_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
