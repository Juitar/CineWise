-- OpenSpec: real-content-coverage-completion
-- Status: V014 draft for A static review only. Do not execute before A explicitly authorizes
-- cinewise_migration_check + cinewise_migrator validation.
-- Scope: forward-only real-content metadata, stable external identity mappings, city audit fields,
-- and a backward-compatible PENDING state. V015 will tighten lease and failure-category constraints
-- after D publishes the corresponding writer and completes the approved compatibility processing.
-- No data backfill, seeds, or physical foreign keys are included here.

ALTER TABLE `movie`
    ADD COLUMN `poster_url` VARCHAR(2048) NULL AFTER `rating`,
    ADD COLUMN `summary` VARCHAR(2000) NULL AFTER `poster_url`,
    ADD COLUMN `release_status` VARCHAR(16) NULL AFTER `summary`,
    ADD COLUMN `release_date` DATE NULL AFTER `release_status`,
    ADD KEY `idx_movie_release_status_date_deleted` (`release_status`, `release_date`, `deleted_at`),
    ADD CONSTRAINT `chk_movie_release_status`
        CHECK (`release_status` IS NULL OR `release_status` IN ('NOW_SHOWING', 'COMING_SOON'));

CREATE TABLE `content_identity_mapping` (
    `id` BIGINT NOT NULL,
    `provider` VARCHAR(64) NOT NULL,
    `resource_type` VARCHAR(16) NOT NULL,
    `external_id` VARCHAR(128) NOT NULL,
    `internal_content_id` BIGINT NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `invalid_reason` VARCHAR(32) NULL,
    `invalidated_at` DATETIME(3) NULL,
    `active_internal_content_id` BIGINT GENERATED ALWAYS AS (
        CASE WHEN `status` = 'ACTIVE' THEN `internal_content_id` ELSE NULL END
    ) STORED,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_identity_external` (`provider`, `resource_type`, `external_id`),
    UNIQUE KEY `uk_content_identity_active_internal` (`provider`, `resource_type`, `active_internal_content_id`),
    KEY `idx_content_identity_resource_internal_status` (`resource_type`, `internal_content_id`, `status`),
    CONSTRAINT `chk_content_identity_positive_ids`
        CHECK (`id` > 0 AND `internal_content_id` > 0),
    CONSTRAINT `chk_content_identity_resource_type`
        CHECK (`resource_type` IN ('MOVIE', 'CINEMA')),
    CONSTRAINT `chk_content_identity_status`
        CHECK (`status` IN ('ACTIVE', 'INVALID')),
    CONSTRAINT `chk_content_identity_invalid_reason`
        CHECK (`invalid_reason` IS NULL OR `invalid_reason` IN (
            'SOURCE_REPLACED', 'CONTENT_DELETED', 'IDENTITY_CONFLICT', 'MANUAL_CORRECTION'
        )),
    CONSTRAINT `chk_content_identity_invalidation`
        CHECK (
            (`status` = 'ACTIVE' AND `invalid_reason` IS NULL AND `invalidated_at` IS NULL)
            OR (`status` = 'INVALID' AND `invalid_reason` IS NOT NULL AND `invalidated_at` IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE `cinema`
    ADD COLUMN `city_name` VARCHAR(64) NULL AFTER `city_code`,
    ADD COLUMN `provider_city_id` VARCHAR(32) NULL AFTER `city_name`,
    ADD KEY `idx_cinema_city_name_deleted_id` (`city_name`, `deleted_at`, `id`),
    ADD KEY `idx_cinema_provider_city_deleted` (`source`, `provider_city_id`, `deleted_at`);

ALTER TABLE `data_sync_log`
    ADD COLUMN `city_name` VARCHAR(64) NULL AFTER `resource_type`,
    ADD COLUMN `provider_city_id` VARCHAR(32) NULL AFTER `city_name`,
    ADD COLUMN `failure_category` VARCHAR(32) NULL AFTER `error_code`,
    ADD COLUMN `lease_owner` VARCHAR(64) NULL AFTER `failure_category`,
    ADD COLUMN `lease_until` DATETIME(3) NULL AFTER `lease_owner`,
    ADD KEY `idx_sync_city_resource_started` (`city_name`, `resource_type`, `started_at`),
    ADD KEY `idx_sync_recovery` (`status`, `lease_until`);

-- V004 only knows RUNNING/SUCCESS/FAILED/PARTIAL. V014 adds PENDING while preserving
-- the current writer's RUNNING and FAILED/PARTIAL shapes. The strict lease/category rules
-- are deliberately deferred to V015 so this forward migration cannot reject current writes.
ALTER TABLE `data_sync_log`
    DROP CHECK `chk_sync_status`,
    DROP CHECK `chk_sync_status_count_relation`,
    DROP CHECK `chk_sync_completion`;

ALTER TABLE `data_sync_log`
    ADD CONSTRAINT `chk_sync_status`
        CHECK (`status` IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL')),
    ADD CONSTRAINT `chk_sync_failure_category`
        CHECK (`failure_category` IS NULL OR `failure_category` IN (
            'NETWORK', 'RATE_LIMIT', 'PROVIDER_RESPONSE', 'DATA_VALIDATION', 'INTERNAL'
        )),
    ADD CONSTRAINT `chk_sync_lease_pair`
        CHECK (
            (`lease_owner` IS NULL AND `lease_until` IS NULL)
            OR (`lease_owner` IS NOT NULL AND `lease_until` IS NOT NULL)
        ),
    ADD CONSTRAINT `chk_sync_status_count_relation`
        CHECK (
            (`status` = 'PENDING'
                AND `total_count` = 0 AND `success_count` = 0 AND `failure_count` = 0
                AND `error_code` IS NULL AND `error_summary` IS NULL AND `failure_category` IS NULL
                AND `finished_at` IS NULL AND `lease_owner` IS NULL AND `lease_until` IS NULL)
            OR (`status` = 'RUNNING'
                AND `finished_at` IS NULL)
            OR (`status` = 'SUCCESS'
                AND `success_count` = `total_count` AND `failure_count` = 0
                AND `finished_at` IS NOT NULL AND `finished_at` >= `started_at`)
            OR (`status` = 'FAILED'
                AND `success_count` = 0 AND `failure_count` = `total_count`
                AND `finished_at` IS NOT NULL AND `finished_at` >= `started_at`)
            OR (`status` = 'PARTIAL'
                AND `success_count` > 0 AND `failure_count` > 0
                AND `success_count` + `failure_count` = `total_count`
                AND `finished_at` IS NOT NULL AND `finished_at` >= `started_at`)
        );
