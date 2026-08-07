-- OpenSpec: agent-distance-recommendation-resume
-- Status: B SQL draft for A static review only. Do not execute, commit, push, or enable Flyway until A approves V018 and MySQL 8.4 validation is authorized.
-- Compatibility: retain all V008 statuses; no historical backfill, column change, or physical foreign key.

ALTER TABLE `agent_run`
    DROP CONSTRAINT `chk_agent_run_status`,
    DROP CONSTRAINT `chk_agent_run_completion`,
    ADD CONSTRAINT `chk_agent_run_status_v018`
        CHECK (`status` IN ('RUNNING', 'WAITING_LOCATION', 'COMPLETED', 'FAILED', 'CANCELLED')),
    ADD CONSTRAINT `chk_agent_run_completion_v018`
        CHECK (
            (`status` IN ('RUNNING', 'WAITING_LOCATION') AND `finished_at` IS NULL)
            OR (`status` IN ('COMPLETED', 'FAILED', 'CANCELLED') AND `finished_at` IS NOT NULL)
        );
