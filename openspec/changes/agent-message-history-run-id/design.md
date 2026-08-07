# 设计

## 查询与权限

`AgentSessionManagementService` 继续先用 `CurrentUserAccessor` 取得当前用户，并以 `sessionId + userId` 查询活动会话。分页消息仍由 `sessionId + userId` 查询。随后从当前页记录收集 distinct 内部 `runId`，调用 B 自有 `AgentRunRepository` 的批量查询；该查询同时限制当前用户和已校验会话内部 ID。只有查询到的运行才映射为对外 UUID，消息不会直接把内部 `long runId` 传到 HTTP 层。

批量结果按内部运行 ID 建表后再映射每一条消息，因此多消息、多运行和分页都不会错配，也不会产生 N+1 查询。运行缺失或不属于当前用户/会话的消息不会暴露为历史结果，而是作为 B 持久化数据不一致失败处理；正常对外响应中每条消息均有非空 UUID。

## 分层与接口

- `api`：`AgentMessageResponse` 和 `AgentController` 输出新增的 `runId`，不改变分页结构。
- `application`：会话查询结果携带消息到对外 UUID 的受控映射。
- `application/persistence`：`AgentRunRepository` 声明最小的类型化批量读取能力。
- `infrastructure`：B 的 MyBatis Repository 和 Mapper 使用一条带 `user_id`、`session_id`、`id IN (...)` 的查询实现。

不访问 A/C/D 的 Entity、Mapper、Repository 或 Controller。此次只读查询不涉及迁移、锁、CAS 或 MySQL 特有行为。

## 恢复与测试

C 的恢复流程是读取历史消息、从确认卡所属消息获取 `runId`，再调用 `GET /api/v1/agent/runs/{runId}`；B 不新增运行或 SSE。测试覆盖正确 UUID 映射、内部 long 不泄露、跨用户会话仍按原安全响应、确认卡消息可取得 runId，以及多消息多运行和分页的映射正确性。C 夹具复用现有 UUID 格式。
