# V020 Agent 会话槽位快照迁移验证记录

## 基本信息

- 迁移：`V020__add_agent_session_slot_snapshot.sql`
- OpenSpec：`agent-conversation-state-and-card-contracts`
- 领域 Owner：B / agent
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-07
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`D5C78ECBCA1CE7EEB6F168BD2955F46818DA01EDD86CBA61EE810E2515608906`
- Flyway V020 checksum：`-1711334481`

## 范围与环境守卫

- V020 仅向 B 自有 `agent_session` 新增 `slot_snapshot_json JSON NOT NULL DEFAULT (JSON_OBJECT())` 和顶层对象 CHECK。
- 不修改 V008，不回填历史数据，不新增外键、索引、坐标、位置上下文、Cookie、模型原文或其他模块数据。
- 验证使用隔离工作树中的 V001–V019 基线与 B 的 V020 草案；目标数据库和账号已核对为 `cinewise_migration_check` / `cinewise_migrator`。
- 未连接共享 `cinewise`，也未修改日常环境配置。

## Flyway 验证

- 执行前 V001–V019 均为 Success，V020 为 Pending。
- 首次 migrate 成功将验证库从 V019 升级到 V020；V020 安装时间为 `2026-08-07 15:48:58`。
- `flyway validate` 成功校验 20 个迁移。
- 重复 migrate 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。

## 实际结构与约束

- `slot_snapshot_json` 实际为 `JSON NOT NULL DEFAULT json_object()`，默认值由 MySQL 生成。
- `chk_agent_session_slot_snapshot_object` 实际要求 `JSON_TYPE(slot_snapshot_json) = 'OBJECT'`。
- `agent_session` 保持 InnoDB、`utf8mb4_0900_ai_ci`；字符串列未发现非预期排序规则。
- 既有字段和索引未改动。

## 正反约束用例

- 省略该列的旧会话写入成功，读取到空 JSON 对象 `{}`。
- 合法 `{version, values}` JSON 对象写入成功。
- JSON 数组、JSON 字符串和 JSON `null` 均被 CHECK 拒绝。
- 测试事务回滚后，测试会话无残留。

## 结论

V020 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、默认值、CHECK、排序规则及正反约束验证，结果通过。本记录不构成共享库发布授权；B 的会话 CAS、跨轮恢复、敏感字段剔除和清理行为仍需应用集成测试验证。
