## Why

普通完整推荐已合入，但真实 NEAREST 推荐需要在同一个 Agent run 内等待浏览器定位并随后恢复原推荐节点。现有 `agent_run` 只有 RUNNING 和终态，无法安全表示等待、超时回退或防止陈旧恢复器误处理。

## What Changes

- 为 Agent run 新增持久化非终态 `WAITING_LOCATION`，保留 session 的 `active_run_id`，并以 run 的 `update_time` 作为五分钟等待期限。
- 提供四个 B REST：等待运行初始化、按 `clientRequestId` 查询、距离上下文创建和位置结果回传；上传成功在同一 `runId` 恢复 NEAREST 推荐，拒绝、取消、上传失败和超时清理后降级为普通推荐。
- A 已发布冻结的 V018 Flyway，前向扩展 `agent_run` 状态和完成时间 CHECK；B 不修改该迁移。
- 明确坐标、地址、定位来源和 distanceContextId 的内存边界，及进程重启后依赖 D 的 300 秒 TTL 的清理限制。

## Capabilities

### New Capabilities

- `agent-distance-recommendation-resume`: 同一 Agent run 的位置等待、距离上下文 REST 编排、超时回退与 NEAREST 推荐恢复。
- `agent-run-waiting-location-persistence`: `WAITING_LOCATION` 状态、CAS、陈旧恢复隔离及 V018 数据库兼容契约。

### Modified Capabilities

- `agent-rank-movie-plan-execution`: 在不改变完整推荐 Command 白名单的前提下，以可信 ToolContext 恢复同一推荐节点。

## Impact

- B：Agent API、应用服务、状态机、持久化 CAS、SSE 事件和测试。
- A：分配并静态审查 V018；通过后负责 MySQL 8.4 空库与兼容验证。
- C：消费两个 B REST 接口，坐标仅直传 D；不把距离上下文写入 SSE 或本地持久化。
- D：提供已合入的 `DistanceContextService.createForRun/cleanup` 与推荐入口消费；仍需确认位置上传 URL 的访问日志脱敏。
