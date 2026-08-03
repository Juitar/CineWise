-- OpenSpec: password-login-and-session
-- Status: C revised draft for A review. Do not commit, push, or execute before A authorization.

CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `nickname` VARCHAR(64) NULL,
    `role_code` VARCHAR(16) NOT NULL DEFAULT 'USER',
    `status` VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    `email_verified` TINYINT(1) NOT NULL DEFAULT 0,
    `token_version` BIGINT NOT NULL DEFAULT 0,
    `privacy_policy_version` VARCHAR(32) NOT NULL,
    `privacy_accepted_at` DATETIME(3) NOT NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_email` (`email`),
    KEY `idx_sys_user_status_update` (`status`, `update_time`),
    CONSTRAINT `chk_sys_user_role_code`
        CHECK (`role_code` IN ('USER', 'ADMIN')),
    CONSTRAINT `chk_sys_user_status`
        CHECK (`status` IN ('NORMAL', 'DISABLED', 'LOCKED')),
    CONSTRAINT `chk_sys_user_email_verified`
        CHECK (`email_verified` IN (0, 1)),
    CONSTRAINT `chk_sys_user_versions_nonnegative`
        CHECK (`token_version` >= 0 AND `version` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `sys_login_log` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NULL,
    `login_type` VARCHAR(32) NOT NULL,
    `success` TINYINT(1) NOT NULL,
    `failure_code` VARCHAR(32) NULL,
    `ip_hash` VARCHAR(128) NULL,
    `user_agent_summary` VARCHAR(255) NULL,
    `trace_id` VARCHAR(64) NOT NULL,
    `create_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_login_user_time` (`user_id`, `create_time`),
    KEY `idx_login_result_time` (`success`, `create_time`),
    KEY `idx_login_trace` (`trace_id`),
    KEY `idx_login_cleanup_create_time` (`create_time`),
    CONSTRAINT `chk_sys_login_log_type`
        CHECK (`login_type` IN ('PASSWORD', 'ADMIN_PASSWORD')),
    CONSTRAINT `chk_sys_login_log_success`
        CHECK (`success` IN (0, 1)),
    CONSTRAINT `chk_sys_login_log_result_consistency`
        CHECK (
            (`success` = 1 AND `user_id` IS NOT NULL AND `failure_code` IS NULL)
            OR (`success` = 0 AND `failure_code` IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
