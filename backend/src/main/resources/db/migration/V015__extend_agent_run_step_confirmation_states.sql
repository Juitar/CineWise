-- OpenSpec: agent-multi-tool-orchestration-and-replanning
-- Owner: B / agent; V015 allocated by A for static review.
-- Draft only: do not execute before A completes static review and authorizes MySQL 8.4 validation.
-- Compatibility: V008 is immutable. Existing node/status values remain valid and need no data rewrite.

ALTER TABLE `agent_run_step`
    DROP CONSTRAINT `chk_agent_run_step_node_type`,
    DROP CONSTRAINT `chk_agent_run_step_status`,
    DROP CONSTRAINT `chk_agent_run_step_state_time`,
    ADD CONSTRAINT `chk_agent_run_step_node_type_v015`
        CHECK (`node_type` IN (
            'ASK_USER', 'CALL_TOOL', 'COMPUTE', 'VALIDATE', 'RENDER_RESULT', 'CONFIRM_ACTION'
        )),
    ADD CONSTRAINT `chk_agent_run_step_status_v015`
        CHECK (`status` IN (
            'PENDING', 'WAITING_CONFIRMATION', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED'
        )),
    ADD CONSTRAINT `chk_agent_run_step_confirmation_status_v015`
        CHECK (`status` <> 'WAITING_CONFIRMATION' OR `node_type` = 'CONFIRM_ACTION'),
    ADD CONSTRAINT `chk_agent_run_step_state_time_v015`
        CHECK (
            (`status` = 'PENDING' AND `started_at` IS NULL AND `finished_at` IS NULL AND `attempt_count` = 0)
            OR (`status` = 'WAITING_CONFIRMATION'
                AND `started_at` IS NULL AND `finished_at` IS NULL
                AND `attempt_count` = 0 AND `retry_count` = 0)
            OR (`status` = 'RUNNING' AND `started_at` IS NOT NULL AND `finished_at` IS NULL
                AND `attempt_count` >= 1)
            OR (`status` IN ('SUCCESS', 'FAILED')
                AND `started_at` IS NOT NULL AND `finished_at` IS NOT NULL AND `attempt_count` >= 1)
            OR (`status` = 'SKIPPED' AND `started_at` IS NULL AND `finished_at` IS NOT NULL
                AND `attempt_count` = 0)
        );
