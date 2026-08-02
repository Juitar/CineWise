-- OpenSpec: d-demo-content-recommendation-baseline
-- Status: D draft for A secondary static review. Do not execute before A authorization.

CREATE TABLE `external_data_snapshot` (
    `id` BIGINT NOT NULL,
    `provider` VARCHAR(64) NOT NULL,
    `external_id` VARCHAR(128) NOT NULL,
    `data_type` VARCHAR(32) NOT NULL,
    `payload_json` JSON NOT NULL,
    `data_time` DATETIME(3) NOT NULL,
    `expire_time` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_external_snapshot` (`provider`, `external_id`, `data_type`),
    KEY `idx_external_expire` (`expire_time`),
    CONSTRAINT `chk_external_snapshot_expire_time`
        CHECK (`expire_time` IS NULL OR `expire_time` >= `data_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `data_sync_log` (
    `id` BIGINT NOT NULL,
    `provider` VARCHAR(64) NOT NULL,
    `resource_type` VARCHAR(32) NOT NULL,
    `request_id` VARCHAR(64) NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `error_code` INT NULL,
    `total_count` INT NOT NULL DEFAULT 0,
    `success_count` INT NOT NULL DEFAULT 0,
    `failure_count` INT NOT NULL DEFAULT 0,
    `started_at` DATETIME(3) NOT NULL,
    `finished_at` DATETIME(3) NULL,
    `error_summary` VARCHAR(1000) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sync_request` (`provider`, `request_id`),
    KEY `idx_sync_provider_time` (`provider`, `create_time`),
    KEY `idx_sync_type_time` (`resource_type`, `started_at`),
    KEY `idx_sync_cleanup_create_time` (`create_time`),
    CONSTRAINT `chk_sync_counts_nonnegative`
        CHECK (`total_count` >= 0 AND `success_count` >= 0 AND `failure_count` >= 0),
    CONSTRAINT `chk_sync_processed_count`
        CHECK (`success_count` + `failure_count` <= `total_count`),
    CONSTRAINT `chk_sync_status`
        CHECK (`status` IN ('RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL')),
    CONSTRAINT `chk_sync_status_count_relation`
        CHECK (
            `status` = 'RUNNING'
            OR (`status` = 'SUCCESS' AND `success_count` = `total_count` AND `failure_count` = 0)
            OR (`status` = 'FAILED' AND `success_count` = 0 AND `failure_count` = `total_count`)
            OR (`status` = 'PARTIAL' AND `success_count` > 0 AND `failure_count` > 0
                AND `success_count` + `failure_count` = `total_count`)
        ),
    CONSTRAINT `chk_sync_completion`
        CHECK (
            (`status` = 'RUNNING' AND `finished_at` IS NULL)
            OR (
                `status` IN ('SUCCESS', 'FAILED', 'PARTIAL')
                AND `finished_at` IS NOT NULL
                AND `finished_at` >= `started_at`
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
