# V015 扩展 Agent 运行步骤确认状态迁移验证记录

## 基本信息

- 迁移：`V015__extend_agent_run_step_confirmation_states.sql`
- OpenSpec：`agent-multi-tool-orchestration-and-replanning`
- 领域 Owner：B / agent
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-06
- MySQL：8.4.x
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`74E56C4E3B31C40DE2FD7ECF8F3EF4DD9D940C409ECDEB856BAD9183B2C0DBBA`
- Flyway V015 checksum：`1782125149`

## 范围与环境守卫

- V015 仅修改 `agent_run_step` 的 CHECK 约束，新增 `node_type` 为 `CONFIRM_ACTION`，新增 `status` 为 `WAITING_CONFIRMATION`。
- 不修改 V008 历史字段，不建立物理外键、索引，不做数据回填。
- 启动前确认目标数据库精确为 `cinewise_migration_check`、账号精确为 `cinewise_migrator`。

## Flyway 验证

- 执行前 V001 至 V014 均为 Success，V015 为 Pending。
- 首次 `migrate` 成功将验证库升级到 V015；V015 安装时间为 `2026-08-06 14:29:54`。
- `flyway validate` 成功校验 15 个迁移。
- 重复 `migrate` 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。

## 实际结构与约束

- 原 `chk_agent_run_step_node_type` 等已被成功 `DROP CONSTRAINT` 卸载。
- 新增 `_v015` 后缀的 4 个 Check 约束已在实际表生效。
- `agent_run_step` 保持 InnoDB 和 `utf8mb4_0900_ai_ci`。

## 正反约束用例

- `status=WAITING_CONFIRMATION` 但 `node_type=CALL_TOOL` 均被 `chk_agent_run_step_confirmation_status_v015` 拒绝。
- `status=WAITING_CONFIRMATION` 但 `attempt_count=1` 被 `chk_agent_run_step_state_time_v015` 拒绝。
- 完全符合 `WAITING_CONFIRMATION` 或 `SUCCESS` 的正向数据均可成功写入。

## 结论

V015 已在 A 专用 MySQL 8.4 验证库完成首次 migrate、validate、重复 migrate、约束替换、排序规则及正反约束用例的验证，结果通过。该记录不替代共享库发布授权。
