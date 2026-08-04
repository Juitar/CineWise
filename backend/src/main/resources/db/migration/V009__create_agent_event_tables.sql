-- OpenSpec: agent-interaction-runtime
-- Status: B SQL draft for A static review. V009 is allocated; do not execute without A authorization.
-- The tables use logical references only. No physical foreign keys are created.

CREATE TABLE `agent_event` (
    `event_id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` VARCHAR(36) NOT NULL,
    `run_id` VARCHAR(36) NOT NULL,
    `event_type` VARCHAR(32) NOT NULL,
    `payload_json` JSON NOT NULL,
    `expire_at` DATETIME(3) NOT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`event_id`),
    KEY `idx_agent_event_stream` (`session_id`, `event_id`),
    KEY `idx_agent_event_run_event` (`run_id`, `event_id`),
    KEY `idx_agent_event_expire` (`expire_at`, `event_id`),
    CONSTRAINT `chk_agent_event_type`
        CHECK (`event_type` IN (
            'message.start', 'message.delta', 'plan.created', 'plan.replanned',
            'step.start', 'step.complete', 'step.failed', 'tool.start',
            'tool.result', 'card', 'message.complete', 'message.error', 'run.complete'
        )),
    CONSTRAINT `chk_agent_event_payload_object`
        CHECK (REGEXP_LIKE(CAST(`payload_json` AS CHAR), '^[{].+[}]$')),
    CONSTRAINT `chk_agent_event_expire_time`
        CHECK (`expire_at` >= `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `agent_event_stream_cursor` (
    `session_id` VARCHAR(36) NOT NULL,
    `last_committed_event_id` BIGINT NOT NULL DEFAULT 0,
    `first_retained_event_id` BIGINT NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `expire_at` DATETIME(3) NOT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`session_id`),
    CONSTRAINT `chk_agent_event_cursor_event_ids`
        CHECK (
            `last_committed_event_id` >= 0
            AND (
                `first_retained_event_id` IS NULL
                OR (
                    `first_retained_event_id` > 0
                    AND `first_retained_event_id` <= `last_committed_event_id`
                )
            )
        ),
    CONSTRAINT `chk_agent_event_cursor_version`
        CHECK (`version` >= 0),
    CONSTRAINT `chk_agent_event_cursor_expire_time`
        CHECK (`expire_at` >= `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
