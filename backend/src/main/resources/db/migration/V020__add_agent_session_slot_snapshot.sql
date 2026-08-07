-- OpenSpec: agent-conversation-state-and-card-contracts
-- Status: A allocated V020; pending A static SQL review. Do not execute before approval.
ALTER TABLE `agent_session`
    ADD COLUMN `slot_snapshot_json` JSON NOT NULL DEFAULT (JSON_OBJECT()) AFTER `summary`,
    ADD CONSTRAINT `chk_agent_session_slot_snapshot_object`
        CHECK (JSON_TYPE(`slot_snapshot_json`) = 'OBJECT');
