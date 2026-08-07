-- OpenSpec: external-showtime-sandbox-ticketing-import
-- Owner: A；保存管理员明确创建的异步导入任务，不保存 D Provider 原始响应或异常正文。

CREATE TABLE `ticketing_external_showtime_import_task` (
    `id` BIGINT NOT NULL,
    `task_id` VARCHAR(36) NOT NULL,
    `client_request_id` VARCHAR(64) NULL,
    `show_date` DATE NOT NULL,
    `cinema_ids` JSON NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `lease_owner` VARCHAR(64) NULL,
    `lease_until` DATETIME(3) NULL,
    `total_count` INT NOT NULL DEFAULT 0,
    `success_count` INT NOT NULL DEFAULT 0,
    `failure_count` INT NOT NULL DEFAULT 0,
    `truncated` TINYINT(1) NOT NULL DEFAULT 0,
    `imported_show_ids` JSON NULL,
    `error_code` INT NULL,
    `started_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `expire_at` DATETIME(3) NOT NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticketing_external_import_task_id` (`task_id`),
    UNIQUE KEY `uk_ticketing_external_import_client_request` (`client_request_id`),
    KEY `idx_ticketing_external_import_recovery` (`status`, `lease_until`),
    KEY `idx_ticketing_external_import_expire` (`expire_at`),
    CONSTRAINT `chk_ticketing_external_import_task_id` CHECK (`id` > 0 AND `version` >= 0),
    CONSTRAINT `chk_ticketing_external_import_task_cinema_ids`
        CHECK (JSON_TYPE(`cinema_ids`) = 'ARRAY' AND JSON_LENGTH(`cinema_ids`) BETWEEN 1 AND 100),
    CONSTRAINT `chk_ticketing_external_import_task_show_ids`
        CHECK (`imported_show_ids` IS NULL OR JSON_TYPE(`imported_show_ids`) = 'ARRAY'),
    CONSTRAINT `chk_ticketing_external_import_task_status`
        CHECK (`status` IN ('PENDING', 'RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    CONSTRAINT `chk_ticketing_external_import_task_truncated` CHECK (`truncated` IN (0, 1)),
    CONSTRAINT `chk_ticketing_external_import_task_count`
        CHECK (`total_count` >= 0 AND `success_count` >= 0 AND `failure_count` >= 0
            AND `success_count` + `failure_count` <= `total_count`),
    CONSTRAINT `chk_ticketing_external_import_task_expire`
        CHECK (`expire_at` >= `create_time`),
    CONSTRAINT `chk_ticketing_external_import_task_error`
        CHECK (`error_code` IS NULL OR `error_code` > 0),
    CONSTRAINT `chk_ticketing_external_import_task_lease`
        CHECK ((`lease_owner` IS NULL) = (`lease_until` IS NULL)),
    CONSTRAINT `chk_ticketing_external_import_task_time_order`
        CHECK (`finished_at` IS NULL OR `finished_at` >= `started_at`),
    CONSTRAINT `chk_ticketing_external_import_task_state`
        CHECK ((`status` = 'PENDING' AND `started_at` IS NULL AND `finished_at` IS NULL
                    AND `lease_owner` IS NULL AND `total_count` = 0 AND `success_count` = 0
                    AND `failure_count` = 0 AND `truncated` = 0 AND `imported_show_ids` IS NULL
                    AND `error_code` IS NULL)
            OR (`status` = 'RUNNING' AND `started_at` IS NOT NULL AND `finished_at` IS NULL
                    AND `lease_owner` IS NOT NULL AND `total_count` = 0 AND `success_count` = 0
                    AND `failure_count` = 0 AND `truncated` = 0 AND `imported_show_ids` IS NULL
                    AND `error_code` IS NULL)
            OR (`status` = 'SUCCESS' AND `started_at` IS NOT NULL AND `finished_at` IS NOT NULL
                    AND `lease_owner` IS NULL AND `total_count` = `success_count` AND `failure_count` = 0
                    AND `imported_show_ids` IS NOT NULL AND JSON_LENGTH(`imported_show_ids`) = `success_count`
                    AND `error_code` IS NULL)
            OR (`status` = 'PARTIAL' AND `started_at` IS NOT NULL AND `finished_at` IS NOT NULL
                    AND `lease_owner` IS NULL AND `success_count` > 0 AND `failure_count` > 0
                    AND `total_count` = `success_count` + `failure_count`
                    AND `imported_show_ids` IS NOT NULL AND JSON_LENGTH(`imported_show_ids`) = `success_count`
                    AND `error_code` IS NOT NULL)
            OR (`status` = 'FAILED' AND `started_at` IS NOT NULL AND `finished_at` IS NOT NULL
                    AND `lease_owner` IS NULL AND `total_count` = `failure_count` AND `success_count` = 0
                    AND `truncated` = 0 AND `imported_show_ids` IS NULL AND `error_code` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
