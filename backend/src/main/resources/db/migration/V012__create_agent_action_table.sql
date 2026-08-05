-- OpenSpec: agent-confirmed-order-execution
-- Owner: B / agent
-- Review status: draft for A static review; do not execute against any database.
-- V011 is the preceding migration on dev. No physical foreign keys are created.

CREATE TABLE `agent_action` (
    `id` BIGINT NOT NULL,
    `action_id` VARCHAR(36) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `agent_session_id` BIGINT NOT NULL,
    `agent_run_id` BIGINT NOT NULL,
    `run_id` VARCHAR(36) NOT NULL,
    `plan_id` VARCHAR(36) NOT NULL,
    `plan_version` INT NOT NULL,
    `node_id` VARCHAR(128) NOT NULL,
    `tool_name` VARCHAR(128) NOT NULL,
    `command_snapshot` JSON NOT NULL,
    `parameter_hash_version` VARCHAR(16) NOT NULL,
    `parameter_hash` CHAR(64) NOT NULL,
    `expire_at` DATETIME(3) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `client_request_id` VARCHAR(64) NULL,
    `idempotency_key` VARCHAR(128) NULL,
    `result_reference` VARCHAR(128) NULL,
    `recovery_hint` VARCHAR(256) NULL,
    `result_unknown_at` DATETIME(3) NULL,
    `recovery_until` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_action_action_id` (`action_id`),
    UNIQUE KEY `uk_agent_action_user_client_request` (`user_id`, `client_request_id`),
    UNIQUE KEY `uk_agent_action_user_idempotency` (`user_id`, `idempotency_key`),
    UNIQUE KEY `uk_agent_action_create_dedup` (
        `user_id`, `agent_run_id`, `plan_id`, `plan_version`, `node_id`, `tool_name`, `parameter_hash`
    ),
    KEY `idx_agent_action_user_action` (`user_id`, `action_id`),
    KEY `idx_agent_action_run_plan_node` (`agent_run_id`, `plan_id`, `plan_version`, `node_id`),
    KEY `idx_agent_action_status_expire` (`status`, `expire_at`),
    KEY `idx_agent_action_status_recovery_until` (`status`, `recovery_until`),
    CONSTRAINT `chk_agent_action_positive_ids`
        CHECK (`id` > 0 AND `user_id` > 0 AND `agent_session_id` > 0 AND `agent_run_id` > 0),
    CONSTRAINT `chk_agent_action_plan_version`
        CHECK (`plan_version` > 0),
    CONSTRAINT `chk_agent_action_hash_version`
        CHECK (`parameter_hash_version` = 'v1'),
    CONSTRAINT `chk_agent_action_hash`
        CHECK (REGEXP_LIKE(`parameter_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `chk_agent_action_command_object`
        CHECK (JSON_TYPE(`command_snapshot`) = 'OBJECT'),
    CONSTRAINT `chk_agent_action_status`
        CHECK (`status` IN (
            'PENDING_CONFIRMATION', 'EXECUTING', 'RESULT_UNKNOWN', 'SUCCEEDED',
            'FAILED', 'REJECTED', 'EXPIRED', 'INVALIDATED'
        )),
    CONSTRAINT `chk_agent_action_write_ids`
        CHECK (
            (`status` IN ('PENDING_CONFIRMATION', 'REJECTED', 'EXPIRED', 'INVALIDATED')
                AND `client_request_id` IS NULL AND `idempotency_key` IS NULL)
            OR (`status` IN ('EXECUTING', 'RESULT_UNKNOWN', 'SUCCEEDED', 'FAILED')
                AND `client_request_id` IS NOT NULL AND `idempotency_key` IS NOT NULL)
        ),
    CONSTRAINT `chk_agent_action_result_reference`
        CHECK (
            (`status` = 'SUCCEEDED' AND `result_reference` IS NOT NULL)
            OR (`status` <> 'SUCCEEDED' AND `result_reference` IS NULL)
        ),
    CONSTRAINT `chk_agent_action_recovery_window`
        CHECK (
            (`status` = 'RESULT_UNKNOWN'
                AND `recovery_hint` IS NOT NULL
                AND `result_unknown_at` IS NOT NULL
                AND `recovery_until` = DATE_ADD(`result_unknown_at`, INTERVAL 30 DAY))
            OR (`status` <> 'RESULT_UNKNOWN'
                AND `recovery_hint` IS NULL
                AND `result_unknown_at` IS NULL
                AND `recovery_until` IS NULL)
        ),
    CONSTRAINT `chk_agent_action_version`
        CHECK (`version` >= 0),
    CONSTRAINT `chk_agent_action_time_order`
        CHECK (`update_time` >= `create_time` AND `expire_at` >= `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
