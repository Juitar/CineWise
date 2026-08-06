-- OpenSpec: user-profile-management
-- Owner: C / auth
-- Review status: draft for A private static review only.
-- Final Flyway filename allocated by A: V017__create_profile_data_consent_tables.sql.
-- A must complete static review before this draft becomes a Flyway migration.
-- Do not execute, commit, or copy into backend/src/main/resources/db/migration before A authorization.

CREATE TABLE `sys_profile_data_consent` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `consent_version` BIGINT NOT NULL,
    `privacy_policy_version` VARCHAR(32) NOT NULL,
    `granted_at` DATETIME(3) NULL,
    `withdrawn_at` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_data_consent_user` (`user_id`),
    CONSTRAINT `chk_profile_data_consent_ids` CHECK (`id` > 0 AND `user_id` > 0),
    CONSTRAINT `chk_profile_data_consent_status`
        CHECK (`status` IN ('GRANTED', 'WITHDRAWN')),
    CONSTRAINT `chk_profile_data_consent_versions`
        CHECK (`consent_version` >= 1 AND `version` >= 0),
    CONSTRAINT `chk_profile_data_consent_policy_version`
        CHECK (CHAR_LENGTH(TRIM(`privacy_policy_version`)) > 0),
    CONSTRAINT `chk_profile_data_consent_state_time` CHECK (
        (`status` = 'GRANTED' AND `granted_at` IS NOT NULL AND `withdrawn_at` IS NULL)
        OR (`status` = 'WITHDRAWN'
            AND `granted_at` IS NOT NULL
            AND `withdrawn_at` IS NOT NULL
            AND `withdrawn_at` >= `granted_at`)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `sys_profile_data_consent_outbox` (
    `id` BIGINT NOT NULL,
    `event_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `consent_version` BIGINT NOT NULL,
    `consent_record_version` BIGINT NOT NULL,
    `occurred_at` DATETIME(3) NOT NULL,
    `trace_id` VARCHAR(64) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `retry_count` INT NOT NULL DEFAULT 0,
    `next_attempt_at` DATETIME(3) NULL,
    `delivered_at` DATETIME(3) NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_consent_outbox_event` (`event_id`),
    UNIQUE KEY `uk_profile_consent_outbox_withdrawal` (`user_id`, `consent_record_version`),
    KEY `idx_profile_consent_outbox_scan` (`status`, `next_attempt_at`, `id`),
    CONSTRAINT `chk_profile_consent_outbox_id` CHECK (`id` > 0 AND `user_id` > 0),
    CONSTRAINT `chk_profile_consent_outbox_versions`
        CHECK (`consent_version` >= 1 AND `consent_record_version` >= 0),
    CONSTRAINT `chk_profile_consent_outbox_event_id`
        CHECK (CHAR_LENGTH(TRIM(`event_id`)) > 0),
    CONSTRAINT `chk_profile_consent_outbox_trace_id`
        CHECK (CHAR_LENGTH(TRIM(`trace_id`)) > 0),
    CONSTRAINT `chk_profile_consent_outbox_status`
        CHECK (`status` IN ('PENDING', 'DELIVERED', 'EXHAUSTED')),
    CONSTRAINT `chk_profile_consent_outbox_retry_count`
        CHECK (`retry_count` >= 0 AND `retry_count` <= 10),
    CONSTRAINT `chk_profile_consent_outbox_delivery_time` CHECK (
        (`status` = 'PENDING' AND `delivered_at` IS NULL AND `next_attempt_at` IS NOT NULL)
        OR (`status` = 'DELIVERED' AND `delivered_at` IS NOT NULL AND `next_attempt_at` IS NULL)
        OR (`status` = 'EXHAUSTED' AND `delivered_at` IS NULL AND `next_attempt_at` IS NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- C proposed state rules for A review:
-- 1. 首次授权：创建记录，status=GRANTED，consent_version=1，version=0。
-- 2. 撤回：status=WITHDRAWN，保留 consent_version 和 granted_at，写 withdrawn_at，version 加一；
--    同一事务插入一条 outbox，event_id 全局唯一。
-- 3. 再次授权：status=GRANTED，consent_version 加一，更新 privacy_policy_version 和 granted_at，
--    清空 withdrawn_at，version 加一。
-- 4. 未有记录不落库，ProfileDataConsentQuery 固定返回 granted=false、两个版本为 0、时间为空。
-- 5. 发送失败只更新同一 outbox 行；人工恢复复用原 event_id，绝不新增事件。
-- 6. 两张表均保留至账户生命周期结束；当前版本不实现账户删除或清理任务。
-- 7. retry_count 只累计自动重试，达到 10 次后状态为 EXHAUSTED 且 next_attempt_at 置空。
--    人工恢复直接投递同一行，不重置或增加 retry_count；成功改为 DELIVERED，失败仍为 EXHAUSTED。
