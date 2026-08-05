-- OpenSpec: email-code-authentication
-- Owner: C / auth
-- Review status: draft for A static review; not a Flyway migration.
-- Confirmed target filename: V011__create_auth_email_code_and_registration_tables.sql
-- This draft must not be executed against a shared database.

CREATE TABLE `sys_email_verify_code` (
    `id` BIGINT NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `purpose` VARCHAR(32) NOT NULL,
    `code_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    `send_time` DATETIME(3) NOT NULL,
    `expire_time` DATETIME(3) NOT NULL,
    `used_time` DATETIME(3) NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_verify_lookup` (`email`, `purpose`, `status`, `expire_time`),
    KEY `idx_verify_expire` (`expire_time`),
    CONSTRAINT `chk_verify_positive_id` CHECK (`id` > 0),
    CONSTRAINT `chk_verify_code_hash`
        CHECK (`code_hash` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `chk_verify_purpose` CHECK (`purpose` IN ('REGISTER', 'LOGIN')),
    CONSTRAINT `chk_verify_status` CHECK (`status` IN ('UNUSED', 'USED', 'INVALID')),
    CONSTRAINT `chk_verify_attempt_count`
        CHECK (`attempt_count` >= 0 AND `attempt_count` <= 5),
    CONSTRAINT `chk_verify_time_range` CHECK (`expire_time` > `send_time`),
    CONSTRAINT `chk_verify_used_time` CHECK (
        (`status` = 'USED'
            AND `used_time` IS NOT NULL
            AND `used_time` >= `send_time`
            AND `used_time` <= `expire_time`)
        OR (`status` IN ('UNUSED', 'INVALID') AND `used_time` IS NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `sys_registration_invite` (
    `id` BIGINT NOT NULL,
    `code_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    `max_uses` INT NOT NULL,
    `used_count` INT NOT NULL DEFAULT 0,
    `valid_from` DATETIME(3) NOT NULL,
    `expire_time` DATETIME(3) NOT NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invite_hash` (`code_hash`),
    KEY `idx_invite_status_expire` (`status`, `expire_time`),
    CONSTRAINT `chk_invite_positive_id` CHECK (`id` > 0),
    CONSTRAINT `chk_invite_code_hash`
        CHECK (`code_hash` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `chk_invite_status` CHECK (`status` IN ('ENABLED', 'DISABLED')),
    CONSTRAINT `chk_invite_usage`
        CHECK (`max_uses` > 0 AND `used_count` >= 0 AND `used_count` <= `max_uses`),
    CONSTRAINT `chk_invite_time_range` CHECK (`expire_time` > `valid_from`),
    CONSTRAINT `chk_invite_version` CHECK (`version` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `sys_registration_invite_use` (
    `id` BIGINT NOT NULL,
    `invite_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `client_request_id` VARCHAR(64) NOT NULL,
    `used_at` DATETIME(3) NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invite_use_user` (`user_id`),
    UNIQUE KEY `uk_invite_use_request` (`client_request_id`),
    KEY `idx_invite_use_time` (`invite_id`, `used_at`),
    CONSTRAINT `chk_invite_use_positive_ids`
        CHECK (`id` > 0 AND `invite_id` > 0 AND `user_id` > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE `sys_login_log`
    DROP CHECK `chk_sys_login_log_type`,
    ADD CONSTRAINT `chk_sys_login_log_type`
        CHECK (`login_type` IN ('PASSWORD', 'ADMIN_PASSWORD', 'EMAIL_CODE'));
