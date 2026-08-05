-- OpenSpec: user-profile-management
-- Status: D draft for A static review. V010 is allocated; do not execute, commit, or push without A review and explicit authorization.
-- All DATETIME(3) values are supplied by Java through an injected UTC Clock. No database timestamp default is used.
-- The four tables use logical user/order references only. No physical foreign keys are created.
-- The rolling 24-hour behavior normalization is implemented by D in a short application transaction; this SQL only supplies its lookup indexes and eventId uniqueness.

CREATE TABLE `user_preference` (
    `user_id` BIGINT NOT NULL,
    `personalization_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    `deleted_at` DATETIME(3) NULL,
    PRIMARY KEY (`user_id`),
    KEY `idx_user_preference_deleted_at` (`deleted_at`),
    CONSTRAINT `chk_user_preference_user_id` CHECK (`user_id` > 0),
    CONSTRAINT `chk_user_preference_version` CHECK (`version` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `user_profile_tag` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `tag_type` VARCHAR(32) NOT NULL,
    `tag_value` VARCHAR(128) NOT NULL,
    `polarity` VARCHAR(16) NOT NULL,
    `weight` DECIMAL(4,3) NOT NULL,
    `source` VARCHAR(32) NOT NULL,
    `confidence` DECIMAL(4,3) NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `expires_at` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted_at` DATETIME(3) NULL,
    `active_flag` TINYINT GENERATED ALWAYS AS (IF(`deleted_at` IS NULL, 1, NULL)) STORED,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_tag_active` (`user_id`, `tag_type`, `tag_value`, `source`, `active_flag`),
    KEY `idx_profile_tag_query` (`user_id`, `status`, `expires_at`),
    KEY `idx_profile_tag_deleted_at` (`deleted_at`),
    CONSTRAINT `chk_profile_tag_positive_ids` CHECK (`id` > 0 AND `user_id` > 0),
    CONSTRAINT `chk_profile_tag_weight` CHECK (`weight` >= 0 AND `weight` <= 1),
    CONSTRAINT `chk_profile_tag_confidence` CHECK (`confidence` >= 0 AND `confidence` <= 1),
    CONSTRAINT `chk_profile_tag_version` CHECK (`version` >= 0),
    CONSTRAINT `chk_profile_tag_type` CHECK (`tag_type` IN ('MOVIE_GENRE', 'TIME', 'CINEMA', 'HALL', 'PRICE', 'SEAT')),
    CONSTRAINT `chk_profile_tag_polarity` CHECK (`polarity` IN ('LIKE', 'DISLIKE')),
    CONSTRAINT `chk_profile_tag_source` CHECK (`source` IN ('MANUAL', 'CONVERSATION', 'BEHAVIOR')),
    CONSTRAINT `chk_profile_tag_status` CHECK (`status` IN ('ACTIVE', 'DISABLED', 'EXPIRED', 'DELETED')),
    CONSTRAINT `chk_profile_tag_source_weight_expiry` CHECK (
        (`source` = 'MANUAL' AND `weight` >= 0.100 AND `weight` <= 1.000)
        OR (`source` = 'BEHAVIOR' AND `weight` >= 0 AND `weight` <= 0.800 AND `expires_at` IS NOT NULL)
        OR (`source` = 'CONVERSATION' AND `weight` >= 0 AND `weight` <= 1.000)
    ),
    CONSTRAINT `chk_profile_tag_deleted_state` CHECK (
        (`status` = 'DELETED' AND `deleted_at` IS NOT NULL)
        OR (`status` <> 'DELETED' AND `deleted_at` IS NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `user_behavior_event` (
    `id` BIGINT NOT NULL,
    `event_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `event_type` VARCHAR(32) NOT NULL,
    `target_type` VARCHAR(32) NOT NULL,
    `target_id` VARCHAR(64) NOT NULL,
    `order_id` BIGINT NULL,
    `order_version` BIGINT NULL,
    `session_id` VARCHAR(64) NULL,
    `payload_json` JSON NULL,
    `occurred_at` DATETIME(3) NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_behavior_event` (`event_id`),
    KEY `idx_behavior_user_time` (`user_id`, `occurred_at`),
    KEY `idx_behavior_event_occurred_at` (`occurred_at`),
    CONSTRAINT `chk_behavior_positive_ids` CHECK (`id` > 0 AND `user_id` > 0),
    CONSTRAINT `chk_behavior_event_type` CHECK (`event_type` IN ('CLICK', 'FAVORITE', 'ACCEPT_PLAN', 'REJECT_PLAN', 'PAID_ORDER', 'NOT_INTERESTED')),
    CONSTRAINT `chk_behavior_target_type` CHECK (`target_type` IN ('MOVIE', 'PLAN', 'SHOW')),
    CONSTRAINT `chk_behavior_event_target` CHECK (
        (`event_type` IN ('CLICK', 'FAVORITE', 'NOT_INTERESTED') AND `target_type` = 'MOVIE')
        OR (`event_type` IN ('ACCEPT_PLAN', 'REJECT_PLAN') AND `target_type` = 'PLAN')
        OR (`event_type` = 'PAID_ORDER' AND `target_type` = 'SHOW')
    ),
    CONSTRAINT `chk_behavior_paid_order_trace` CHECK (
        (`event_type` = 'PAID_ORDER' AND `order_id` IS NOT NULL AND `order_id` > 0 AND `order_version` IS NOT NULL AND `order_version` >= 0)
        OR (`event_type` <> 'PAID_ORDER' AND `order_id` IS NULL AND `order_version` IS NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `profile_write_request` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `operation` VARCHAR(32) NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `request_hash` CHAR(64) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    `http_status` SMALLINT NOT NULL,
    `response_json` JSON NOT NULL,
    `completed_at` DATETIME(3) NOT NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_write_request_idempotency` (`user_id`, `operation`, `idempotency_key`),
    KEY `idx_profile_write_request_expire_at` (`expires_at`),
    CONSTRAINT `chk_profile_write_request_positive_ids` CHECK (`id` > 0 AND `user_id` > 0),
    CONSTRAINT `chk_profile_write_request_hash` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `chk_profile_write_request_status` CHECK (`status` = 'COMPLETED'),
    CONSTRAINT `chk_profile_write_request_http_status` CHECK (`http_status` IN (200, 201)),
    CONSTRAINT `chk_profile_write_request_time` CHECK (`expires_at` > `completed_at` AND `completed_at` >= `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
