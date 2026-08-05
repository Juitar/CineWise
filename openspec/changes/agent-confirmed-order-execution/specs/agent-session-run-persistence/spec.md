## ADDED Requirements

### Requirement: 确认动作持久化必须由 MySQL 最终保证
系统 SHALL 为 B 自有 `agent_action` 设计 `id BIGINT` 雪花主键、全局唯一 `action_id VARCHAR(36)`、`user_id BIGINT`、`agent_session_id BIGINT`、`agent_run_id BIGINT` 和同一运行的外部 `run_id VARCHAR(36)`；其中前两个内部 ID 分别逻辑关联 V008 的 `agent_session.id`、`agent_run.id`，不得写成语义含混的 `run_id BIGINT`，也不得建立物理外键。表 MUST 另含 `plan_id VARCHAR(36)`、`plan_version INT`、`node_id VARCHAR(128)`、`tool_name VARCHAR(128)`、`command_snapshot JSON`、`parameter_hash_version VARCHAR(16)`、`parameter_hash CHAR(64)`、`expire_at DATETIME(3)`、`status VARCHAR(32)`、`client_request_id VARCHAR(64)`、`idempotency_key VARCHAR(128)`、`result_reference VARCHAR(128)`、`recovery_hint VARCHAR(256)`、`result_unknown_at DATETIME(3)`、`recovery_until DATETIME(3)`、`version BIGINT DEFAULT 0`、`create_time DATETIME(3)` 和 `update_time DATETIME(3)`。类型、可空性、默认值和 CHECK 必须遵守 design 的字段表。唯一键 MUST 覆盖 `action_id`、`(user_id, client_request_id)`、`(user_id, idempotency_key)` 和 action 创建去重键 `(user_id, agent_run_id, plan_id, plan_version, node_id, tool_name, parameter_hash)`；索引 MUST 覆盖 `(user_id, action_id)`、`(agent_run_id, plan_id, plan_version, node_id)`、`(status, expire_at)`、`(status, recovery_until)`。迁移版本、SQL 和共享库执行 MUST 由 A 分配和授权，B 不得自行执行。

#### Scenario: 未分配迁移版本
- **WHEN** V012 草稿尚未由 A 静态审查和明确授权执行
- **THEN** B 不创建 SQL、不连接共享数据库，也不把内存 Mock 视为持久化完成
- **AND** 迁移相关任务保持未勾选

### Requirement: 结果未知动作必须保留并限制恢复方式
系统 SHALL 只支持 `PENDING_CONFIRMATION`、`EXECUTING`、`RESULT_UNKNOWN`、`SUCCEEDED`、`FAILED`、`REJECTED`、`EXPIRED`、`INVALIDATED` 八个状态。只有 `RESULT_UNKNOWN` 可保存原稳定键之外的 `recovery_hint`、`result_unknown_at`、`recovery_until=result_unknown_at+30天`；其他状态三者 MUST 均为空。在恢复窗口内只允许使用原键查询恢复，不得重新执行建单。明确结果保存后必须清除三者；窗口届满后仍未明确的记录只能由清理任务删除，不能改为成功或失败。

#### Scenario: 30 天内恢复结果未知
- **WHEN** action 处于 `RESULT_UNKNOWN` 且仍在 30 天保留期内
- **THEN** 系统只调用 A 的原键查询接口并保存明确结果
- **AND** 不创建新 action、不生成新键、不重发建单

### Requirement: MySQL 集成测试必须验证确认并发和恢复
只要 change 包含 action 持久化、CAS、唯一约束、并发确认、事务边界或结果未知恢复，系统 SHALL 在仓库现有 GitHub Actions MySQL 8.4 一次性 `cinewise_agent_it` 中验证空库 Flyway、重复启动、action 查询/过期、重复与并发确认、CAS 冲突、结果未知恢复和事务回滚。H2 结果 MUST NOT 被表述为 MySQL 验证通过。

#### Scenario: CI 不可触发或不可读取
- **WHEN** B 当前无法触发或读取 GitHub Actions MySQL 作业
- **THEN** 系统保留本地单元/H2 结果并明确标记 MySQL CI 未验证
- **AND** 不声称已完成 MySQL 并发或迁移验证
