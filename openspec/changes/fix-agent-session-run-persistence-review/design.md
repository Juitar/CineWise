## Context

PR #44 的审查指出四个实现缺口。它们都在 B 的 Agent 持久化模块内，不涉及 V008 表结构或跨模块接口。

## Goals / Non-Goals

**Goals:**

- 保证含 `RUNNING` 步骤的运行不会因其他 `PENDING` 步骤提前终态。
- 在 MySQL `REPEATABLE READ` 下让并发相同请求标识稳定读取胜者。
- 让请求摘要接受合法 Unicode 码点并完整返回指定运行的消息。

**Non-Goals:**

- 不修改 V008 SQL、Controller、SSE、确认动作或 D 的工具。

## Decisions

- `nextRunStatus` 先检查是否存在 `RUNNING` 步骤，再检查 `PENDING`；只读 `PROCESSING` 保持运行和会话占用。
- 唯一键冲突不在初始写事务内重新查询。初始事务抛出携带请求摘要的内部异常并回滚；外层调用独立的只读事务查询胜者，比较摘要后返回已有运行。
- 请求摘要按 Unicode 码点追加 JSON 内容；合法代理对作为一个码点保留，只有未配对代理字符被拒绝。
- Repository 新增按 `run_id + user_id` 读取消息的方法，快照不再受会话最近消息数量限制。

## Risks / Trade-offs

- [胜者尚未提交] → 唯一键冲突只会在胜者提交后发生；独立事务读取不到记录时保留原始异常，不伪造成功。
- [消息读取新增索引需求] → 使用现有 `idx_agent_message_run`，不改 V008。

## Migration Plan

1. 修改 B 的事务、摘要和消息读取实现并补单元/真实 MySQL 并发测试。
2. 运行定向测试、完整 `verify`、OpenSpec 严格校验和 CI。
3. 作为 PR #44 的后续提交推送，不执行数据库迁移。

## Open Questions

- 无。
