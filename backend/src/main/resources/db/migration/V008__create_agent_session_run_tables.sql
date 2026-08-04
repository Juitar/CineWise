-- OpenSpec: agent-session-run-persistence
-- Status: B local draft for A SQL static review. A allocated V008; do not execute, commit, or push without further authorization.
-- The four tables use logical references only. No physical foreign keys are created.

CREATE TABLE `agent_session` (
    `id` BIGINT NOT NULL,
    `session_id` VARCHAR(36) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `summary` TEXT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `active_run_id` BIGINT NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `expire_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_session_session_id` (`session_id`),
    UNIQUE KEY `uk_agent_session_active_run_id` (`active_run_id`),
    KEY `idx_agent_session_user_update_time` (`user_id`, `update_time`),
    KEY `idx_agent_session_expire_at` (`expire_at`),
    CONSTRAINT `chk_agent_session_positive_ids`
        CHECK (`id` > 0 AND `user_id` > 0 AND (`active_run_id` IS NULL OR `active_run_id` > 0)),
    CONSTRAINT `chk_agent_session_status`
        CHECK (`status` IN ('ACTIVE', 'CLEARED')),
    CONSTRAINT `chk_agent_session_version`
        CHECK (`version` >= 0),
    CONSTRAINT `chk_agent_session_cleared_inactive`
        CHECK (`status` <> 'CLEARED' OR `active_run_id` IS NULL)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `agent_run` (
    `id` BIGINT NOT NULL,
    `run_id` VARCHAR(36) NOT NULL,
    `session_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `client_request_id` VARCHAR(36) NOT NULL,
    `request_hash_version` VARCHAR(8) NOT NULL DEFAULT 'v1',
    `request_hash` CHAR(64) NOT NULL,
    `plan_id` VARCHAR(36) NULL,
    `plan_version` INT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    `trace_id` VARCHAR(64) NOT NULL,
    `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `finished_at` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `expire_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_run_run_id` (`run_id`),
    UNIQUE KEY `uk_agent_run_user_session_request` (`user_id`, `session_id`, `client_request_id`),
    KEY `idx_agent_run_session_status_create_time` (`session_id`, `status`, `create_time`),
    KEY `idx_agent_run_expire_at` (`expire_at`),
    CONSTRAINT `chk_agent_run_positive_ids`
        CHECK (`id` > 0 AND `session_id` > 0 AND `user_id` > 0),
    CONSTRAINT `chk_agent_run_hash_version`
        CHECK (`request_hash_version` = 'v1'),
    CONSTRAINT `chk_agent_run_hash`
        CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `chk_agent_run_plan_version`
        CHECK (`plan_version` IS NULL OR `plan_version` >= 1),
    CONSTRAINT `chk_agent_run_status`
        CHECK (`status` IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT `chk_agent_run_completion`
        CHECK (
            (`status` = 'RUNNING' AND `finished_at` IS NULL)
            OR (`status` IN ('COMPLETED', 'FAILED', 'CANCELLED') AND `finished_at` IS NOT NULL)
        ),
    CONSTRAINT `chk_agent_run_version`
        CHECK (`version` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `agent_message` (
    `id` BIGINT NOT NULL,
    `message_id` VARCHAR(36) NOT NULL,
    `session_id` BIGINT NOT NULL,
    `run_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `role` VARCHAR(16) NOT NULL,
    `message_type` VARCHAR(32) NOT NULL,
    `text` VARCHAR(2000) NOT NULL,
    `payload_json` JSON NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    `completed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `expire_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_message_message_id` (`message_id`),
    KEY `idx_agent_message_session_id` (`session_id`, `id`),
    KEY `idx_agent_message_run_id` (`run_id`),
    KEY `idx_agent_message_expire_at` (`expire_at`),
    CONSTRAINT `chk_agent_message_positive_ids`
        CHECK (`id` > 0 AND `session_id` > 0 AND `run_id` > 0 AND `user_id` > 0),
    CONSTRAINT `chk_agent_message_role`
        CHECK (`role` IN ('USER', 'ASSISTANT')),
    CONSTRAINT `chk_agent_message_type`
        CHECK (`message_type` IN ('TEXT', 'QUESTION', 'MOVIE_CARD', 'PLAN_CARD', 'PROGRESS', 'ERROR')),
    CONSTRAINT `chk_agent_message_role_type`
        CHECK (
            (`role` = 'USER' AND `message_type` = 'TEXT')
            OR (`role` = 'ASSISTANT' AND `message_type` IN ('TEXT', 'QUESTION', 'MOVIE_CARD', 'PLAN_CARD', 'PROGRESS', 'ERROR'))
        ),
    CONSTRAINT `chk_agent_message_status`
        CHECK (`status` = 'COMPLETED')
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `agent_run_step` (
    `id` BIGINT NOT NULL,
    `run_id` BIGINT NOT NULL,
    `plan_version` INT NOT NULL,
    `node_id` VARCHAR(64) NOT NULL,
    `node_type` VARCHAR(24) NOT NULL,
    `depends_on_json` JSON NOT NULL,
    `input_refs_json` JSON NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    `failure_policy` VARCHAR(24) NOT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `retry_count` INT NOT NULL DEFAULT 0,
    `recovery_pending` TINYINT NOT NULL DEFAULT 0,
    `auto_skipped` TINYINT NOT NULL DEFAULT 0,
    `skip_reason` VARCHAR(32) NULL,
    `skip_source_node_id` VARCHAR(64) NULL,
    `slot_snapshot_json` JSON NOT NULL,
    `started_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `expire_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_run_step_run_plan_node` (`run_id`, `plan_version`, `node_id`),
    KEY `idx_agent_run_step_run_status` (`run_id`, `status`),
    KEY `idx_agent_run_step_expire_at` (`expire_at`),
    CONSTRAINT `chk_agent_run_step_positive_values`
        CHECK (`id` > 0 AND `run_id` > 0 AND `plan_version` >= 1 AND `attempt_count` >= 0 AND `retry_count` >= 0 AND `version` >= 0),
    CONSTRAINT `chk_agent_run_step_node_type`
        CHECK (`node_type` IN ('ASK_USER', 'CALL_TOOL', 'COMPUTE', 'VALIDATE', 'RENDER_RESULT')),
    CONSTRAINT `chk_agent_run_step_status`
        CHECK (`status` IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT `chk_agent_run_step_failure_policy`
        CHECK (`failure_policy` IN ('RETRY_ONCE', 'REPLAN', 'ASK_USER', 'FAIL')),
    CONSTRAINT `chk_agent_run_step_retry_count`
        CHECK (`retry_count` <= `attempt_count` AND `retry_count` <= 1),
    CONSTRAINT `chk_agent_run_step_recovery_pending`
        CHECK (`recovery_pending` IN (0, 1) AND (`recovery_pending` = 0 OR `status` = 'RUNNING')),
    CONSTRAINT `chk_agent_run_step_state_time`
        CHECK (
            (`status` = 'PENDING' AND `started_at` IS NULL AND `finished_at` IS NULL AND `attempt_count` = 0)
            OR (`status` = 'RUNNING' AND `started_at` IS NOT NULL AND `finished_at` IS NULL AND `attempt_count` >= 1)
            OR (`status` IN ('SUCCESS', 'FAILED') AND `started_at` IS NOT NULL AND `finished_at` IS NOT NULL AND `attempt_count` >= 1)
            OR (`status` = 'SKIPPED' AND `started_at` IS NULL AND `finished_at` IS NOT NULL AND `attempt_count` = 0)
        ),
    CONSTRAINT `chk_agent_run_step_skip`
        CHECK (
            (`auto_skipped` = 1 AND `status` = 'SKIPPED' AND `skip_reason` IS NOT NULL AND `skip_reason` = 'UPSTREAM_FAILED' AND `skip_source_node_id` IS NOT NULL)
            OR (`auto_skipped` = 0 AND `skip_reason` IS NULL AND `skip_source_node_id` IS NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
