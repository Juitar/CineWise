# V020 Agent 会话槽位快照共享库迁移记录

## 执行范围

- 目标迁移：`V020__add_agent_session_slot_snapshot.sql`
- OpenSpec：`openspec/changes/agent-conversation-state-and-card-contracts/`
- 领域 Owner：B / agent；Flyway 发布负责人：A
- 执行日期：2026-08-07
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`D5C78ECBCA1CE7EEB6F168BD2955F46818DA01EDD86CBA61EE810E2515608906`
- Flyway V020 checksum：`-1711334481`

## 发布前检查

- A 专用验证库已完成首次 migrate、validate、重复 migrate、默认值、JSON CHECK、排序规则及正反约束验证。
- 发布前只读确认共享 `cinewise` 位于 V019，V020 为 Pending。
- 使用独立迁移账号 `cinewise_migrator`；未使用日常应用账号，未修改日常 `FLYWAY_ENABLED` 配置。
- 发布使用隔离工作树中的 V001–V019 基线和 B 的 V020 草案，不包含其他未提交改动。
- 已生成发布前逻辑备份：`data/migration-backups/cinewise-before-v020-20260807-155133-tables-data-triggers.sql`。
- 备份大小：3,428,828 bytes；SHA-256：`2AB16DA8C13C7FE5339E9FB56FB0FAC61FB14DFF260A5954D3420B347B2DCEC2`。

## 共享库发布结果

- 使用一次性受控 Flyway 进程将共享 `cinewise` 从 V019 升级到 V020，成功应用 1 个迁移。
- V020 安装时间为 `2026-08-07 15:52:21`，成功标志为 1，checksum 为 `-1711334481`。
- 发布后 `flyway validate` 成功校验 20 个迁移。
- 发布后重复 migrate 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `agent_session.slot_snapshot_json` 实际为 `JSON NOT NULL DEFAULT json_object()`。
- `chk_agent_session_slot_snapshot_object` 实际要求顶层 JSON 值为对象。
- `agent_session` 保持 InnoDB、`utf8mb4_0900_ai_ci`，既有索引未改动。
- 本次只新增兼容列和 CHECK，不写入业务数据。

## 结论

V020 已成功发布至共享 `cinewise`，并完成备份、Flyway 历史、发布后 validate、重复执行、列默认值、CHECK 与排序规则核验。V020 自本次成功执行起冻结；后续结构调整必须新增更高版本的前向迁移。B 的会话 CAS、跨轮恢复、敏感字段剔除和清理行为仍需应用 MySQL 集成测试验证。
