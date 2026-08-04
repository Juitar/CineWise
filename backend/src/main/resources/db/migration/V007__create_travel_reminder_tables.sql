CREATE TABLE `travel_task` (
    `id` BIGINT NOT NULL,
    `task_id` VARCHAR(64) NOT NULL,
    `payment_event_id` VARCHAR(64) NULL,
    `invalidation_event_id` VARCHAR(64) NULL,
    `user_id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `show_id` BIGINT NOT NULL,
    `cinema_area` VARCHAR(128) NOT NULL,
    `start_at` DATETIME(3) NOT NULL,
    `trigger_at` DATETIME(3) NOT NULL,
    `order_version` BIGINT NOT NULL DEFAULT 0,
    `version` BIGINT NOT NULL DEFAULT 0,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `retry_count` INT NOT NULL DEFAULT 0,
    `closed_at` DATETIME(3) NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_travel_task_task_id` (`task_id`),
    UNIQUE KEY `uk_travel_task_order_id` (`order_id`),
    UNIQUE KEY `uk_travel_task_payment_event_id` (`payment_event_id`),
    UNIQUE KEY `uk_travel_task_invalidation_event_id` (`invalidation_event_id`),
    KEY `idx_travel_task_status_trigger_at` (`status`, `trigger_at`),
    KEY `idx_travel_task_closed_at` (`closed_at`),
    CONSTRAINT `chk_travel_task_order_version_non_negative`
        CHECK (`order_version` >= 0),
    CONSTRAINT `chk_travel_task_version_non_negative`
        CHECK (`version` >= 0),
    CONSTRAINT `chk_travel_task_retry_count_non_negative`
        CHECK (`retry_count` >= 0),
    CONSTRAINT `chk_travel_task_status`
        CHECK (`status` IN ('PENDING', 'GENERATING', 'READY', 'NOTIFIED', 'COMPLETED', 'CANCELLED', 'FAILED')),
    CONSTRAINT `chk_travel_task_closed_at`
        CHECK (
            (`status` IN ('COMPLETED', 'CANCELLED', 'FAILED') AND `closed_at` IS NOT NULL)
            OR (`status` NOT IN ('COMPLETED', 'CANCELLED', 'FAILED') AND `closed_at` IS NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `travel_advice_snapshot` (
    `id` BIGINT NOT NULL,
    `travel_task_id` BIGINT NOT NULL,
    `task_version` BIGINT NOT NULL,
    `weather_json` JSON NULL,
    `route_json` JSON NULL,
    `food_json` JSON NULL,
    `advice_json` JSON NOT NULL,
    `source` VARCHAR(32) NOT NULL,
    `data_time` DATETIME(3) NOT NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `is_expired` TINYINT NOT NULL DEFAULT 0,
    `degraded` TINYINT NOT NULL DEFAULT 0,
    `fallback_type` VARCHAR(32) NULL,
    `create_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_travel_advice_snapshot_task_version` (`travel_task_id`, `task_version`),
    KEY `idx_travel_advice_snapshot_expires_at` (`expires_at`),
    CONSTRAINT `chk_travel_advice_snapshot_task_version_non_negative`
        CHECK (`task_version` >= 0),
    CONSTRAINT `chk_travel_advice_snapshot_is_expired`
        CHECK (`is_expired` IN (0, 1)),
    CONSTRAINT `chk_travel_advice_snapshot_degraded`
        CHECK (`degraded` IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `travel_notification_log` (
    `id` BIGINT NOT NULL,
    `travel_task_id` BIGINT NOT NULL,
    `trigger_type` VARCHAR(32) NOT NULL,
    `task_version` BIGINT NOT NULL,
    `channel` VARCHAR(16) NOT NULL DEFAULT 'EMAIL',
    `template_code` VARCHAR(32) NOT NULL DEFAULT 'VIEWING_REMINDER',
    `delivery_key` VARCHAR(160) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `attempt_count` INT NOT NULL DEFAULT 0,
    `next_retry_at` DATETIME(3) NULL,
    `lease_until` DATETIME(3) NULL,
    `sent_at` DATETIME(3) NULL,
    `resolved_at` DATETIME(3) NULL,
    `provider_message_id` VARCHAR(128) NULL,
    `error_code` INT NULL,
    `scheduled_at` DATETIME(3) NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_travel_notification_log_delivery_key` (`delivery_key`),
    KEY `idx_travel_notification_log_status_retry` (`status`, `next_retry_at`),
    KEY `idx_travel_notification_log_task_created` (`travel_task_id`, `create_time`),
    KEY `idx_travel_notification_log_resolved_at` (`resolved_at`),
    CONSTRAINT `chk_travel_notification_log_task_version_non_negative`
        CHECK (`task_version` >= 0),
    CONSTRAINT `chk_travel_notification_log_attempt_count_non_negative`
        CHECK (`attempt_count` >= 0),
    CONSTRAINT `chk_travel_notification_log_channel`
        CHECK (`channel` = 'EMAIL'),
    CONSTRAINT `chk_travel_notification_log_template_code`
        CHECK (`template_code` = 'VIEWING_REMINDER'),
    CONSTRAINT `chk_travel_notification_log_status`
        CHECK (`status` IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'UNKNOWN')),
    CONSTRAINT `chk_travel_notification_log_resolved_at`
        CHECK (
            (`status` IN ('SENT', 'FAILED') AND `resolved_at` IS NOT NULL)
            OR (`status` NOT IN ('SENT', 'FAILED') AND `resolved_at` IS NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
