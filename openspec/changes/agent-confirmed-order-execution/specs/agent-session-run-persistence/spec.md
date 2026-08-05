## ADDED Requirements

### Requirement: 确认动作持久化必须由 MySQL 最终保证
系统 SHALL 为 B 自有 `agent_action` 设计 BIGINT 雪花 ID、全局唯一 `action_id VARCHAR(36)`、用户、与 V008 一致的 BIGINT session/run 逻辑关联、计划版本、节点、工具、JSON Command 快照、hash version、`parameter_hash CHAR(64)`、有效期、状态、稳定键、结果引用、恢复提示和版本字段；不得建立物理外键。唯一键 MUST 覆盖 `action_id`、`(user_id, client_request_id)`、`(user_id, idempotency_key)`；索引 MUST 覆盖 `(user_id, action_id)`、`(run_id, plan_id, plan_version, node_id)`、`(status, expire_at)`。迁移版本、SQL 和共享库执行 MUST 由 A 分配和授权，B 不得自行执行。

#### Scenario: 未分配迁移版本
- **WHEN** 完整 OpenSpec 尚未进入远端，或 V013 尚未由 A 正式分配和静态审查
- **THEN** B 不创建 SQL、不连接共享数据库，也不把内存 Mock 视为持久化完成
- **AND** 迁移相关任务保持未勾选

### Requirement: 结果未知动作必须保留并限制恢复方式
系统 SHALL 至少支持 `PENDING_CONFIRMATION`、`EXECUTING`、`SUCCEEDED`、`FAILED`、`RESULT_UNKNOWN`、`EXPIRED` 状态，并记录拒绝或确认条件变化的终态。`RESULT_UNKNOWN` MUST 保存原稳定键并保留 30 天；清理前只允许使用原键查询恢复，不得重新执行建单。

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
