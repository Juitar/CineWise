## Why

PR #44 审查发现运行中的 `PROCESSING`、并发重复请求、Unicode emoji 和旧运行快照都有错误或缺失的保护。它们会让只读运行错误终态、返回 500、拒绝正常文本或丢失历史消息，必须在合并前修正。

## What Changes

- 修正 `PROCESSING` 运行的状态判定，确保下游 `PENDING` 节点不会使运行提前失败。
- 将唯一键冲突后的重复请求查询放在独立事务中，并补充 MySQL 并发同幂等键测试。
- 支持合法 Unicode 代理对，继续拒绝未配对代理字符。
- 按 `run_id + user_id` 读取消息快照，避免历史重复请求返回空消息。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `agent-session-run-persistence`: 修正运行状态、幂等恢复、请求摘要字符处理和运行消息读取的可观察行为。

## Impact

- 受影响代码：`backend/src/main/java/com/miaoyu/ticket/agent/**`、Agent 单元和 MySQL 集成测试。
- 不改 Controller、SSE、D 工具、共享数据库或 V008 SQL；不新增跨模块依赖。
