# 任务

- [x] B：实现会话槽位快照领域类型、JSON 编解码和本人会话的条件更新；验证单元测试。
- [x] B：在消息提交流程中识别上一轮 QUESTION、校验并保存城市/日期/票数，复用有效快照；验证多轮、非法、清空/过期测试。
- [x] B：实现正式卡片 DTO 与既有 JSON 的受控映射，保持 SSE 外层字段；验证 DTO/映射测试。
- [x] B：更新 C 联调夹具，覆盖 QUESTION、PLAN_CARD、BUSINESS_INTENT、确认卡、确认结果和历史 UUID runId。
- [x] A：已分配并审查 V020 及 B 自有 `agent_session.slot_snapshot_json` 的 JSON 顶层对象约束；B 不执行共享数据库迁移。
- [x] B：执行相关 Agent 测试、`openspec validate agent-conversation-state-and-card-contracts --strict`、`backend/mvnw.cmd verify`、`git diff --check`；只勾选已通过项。
