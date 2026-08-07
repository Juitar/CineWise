# V018 Agent 等待定位状态迁移验证记录

## 基本信息

- 迁移：`V018__add_agent_run_waiting_location_status.sql`
- OpenSpec：`agent-distance-recommendation-resume`
- 领域 Owner：B / agent
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-07
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`97F219D8FCB6DFF9F7B3B877EFC64C02E599D6C3A9A1C01D88CB958EB5870685`
- Flyway V018 checksum：`77476108`

## 范围与环境守卫

- V018 仅替换 `agent_run` 的 `chk_agent_run_status` 和 `chk_agent_run_completion`，新增 `WAITING_LOCATION`。
- `RUNNING/WAITING_LOCATION` 要求 `finished_at IS NULL`；`COMPLETED/FAILED/CANCELLED` 要求 `finished_at IS NOT NULL`。
- 不修改 V008，不回填历史数据，不改变列长度，不建立外键，不保存坐标或距离上下文 ID。
- 验证使用临时 Flyway SQL 目录，包含 V001–V017 和 B 的 V018 草案，排除了 A 侧尚未验证的 V019 草案。
- 目标数据库和账号已核对为 `cinewise_migration_check` / `cinewise_migrator`；未连接共享库。

## Flyway 验证

- 执行前 V001–V017 均为 Success，V018 为 Pending。
- 首次 migrate 成功将验证库从 V017 升级到 V018；V018 安装时间为 `2026-08-07 13:46:00`。
- `flyway validate` 成功校验 18 个迁移。
- 重复 migrate 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。

## 实际结构与约束

- `agent_run` 和 `agent_session` 均为 InnoDB、`utf8mb4_0900_ai_ci`。
- `chk_agent_run_status_v018` 实际允许 `RUNNING`、`WAITING_LOCATION`、`COMPLETED`、`FAILED`、`CANCELLED`。
- `chk_agent_run_completion_v018` 实际区分未完成等待状态和三个终态的完成时间。
- 既有 `idx_agent_run_session_status_create_time(session_id, status, create_time)` 保持不变。

## 正反约束用例

- `WAITING_LOCATION`、旧 `RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED` 均可在事务中写入。
- `agent_session.active_run_id` 在 WAITING_LOCATION 场景可保持指向同一 run。
- WAITING_LOCATION 携带 `finished_at`、终态缺少 `finished_at`、未知状态均被 CHECK 拒绝。
- 测试事务回滚后，测试 run 和 session 均无残留。

## 结论

V018 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、旧状态兼容、结构、索引、排序规则及正反 CHECK 验证，结果通过。本记录不构成共享库发布授权；CAS、等待超时、距离上下文清理和旧计划事件拒绝仍需由 B 的应用集成测试完成。
