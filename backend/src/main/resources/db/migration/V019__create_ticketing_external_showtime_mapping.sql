-- OpenSpec: external-showtime-sandbox-ticketing-import
-- Owner: A；仅保存 A 的外部场次幂等映射，不保存 D 的原始快照或交易库存。
-- 迁移审查：草案，未经 A 专用空 MySQL 验证前不得执行。

CREATE TABLE `ticketing_external_showtime_mapping` (
    `id` BIGINT NOT NULL,
    `provider` VARCHAR(32) NOT NULL,
    `external_cinema_id` VARCHAR(64) NOT NULL,
    `external_show_id` VARCHAR(64) NOT NULL,
    `show_id` BIGINT NOT NULL,
    `import_mode` VARCHAR(24) NOT NULL,
    `source` VARCHAR(32) NOT NULL,
    `data_at` DATETIME(3) NOT NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticketing_external_show_key`
        (`provider`, `external_cinema_id`, `external_show_id`),
    UNIQUE KEY `uk_ticketing_external_show_local_show` (`show_id`),
    KEY `idx_ticketing_external_show_cinema` (`external_cinema_id`, `provider`),
    CONSTRAINT `chk_ticketing_external_show_ids`
        CHECK (`id` > 0 AND `show_id` > 0),
    CONSTRAINT `chk_ticketing_external_show_text`
        CHECK (CHAR_LENGTH(TRIM(`provider`)) > 0
            AND CHAR_LENGTH(TRIM(`external_cinema_id`)) > 0
            AND CHAR_LENGTH(TRIM(`external_show_id`)) > 0
            AND CHAR_LENGTH(TRIM(`source`)) > 0),
    CONSTRAINT `chk_ticketing_external_show_import_mode`
        CHECK (`import_mode` IN ('ACCEPTED', 'SANDBOX_REFERENCE')),
    CONSTRAINT `chk_ticketing_external_show_snapshot_time`
        CHECK (`data_at` < `expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
