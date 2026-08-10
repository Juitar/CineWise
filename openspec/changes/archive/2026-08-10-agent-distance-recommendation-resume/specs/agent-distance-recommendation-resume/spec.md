## ADDED Requirements

### Requirement: B 必须以四个受控 REST 接口编排距离推荐

系统 SHALL 提供 `POST /api/v1/agent/sessions/{sessionId}/distance-recommendation-runs`、`GET /api/v1/agent/sessions/{sessionId}/runs/by-client-request/{clientRequestId}`、`POST /api/v1/agent/sessions/{sessionId}/runs/{runId}/distance-context`（空请求体）和 `POST /api/v1/agent/runs/{runId}/distance-recommendation`。初始化接口 MUST 原子创建 USER message 和无 plan 的 `WAITING_LOCATION` run，不调用模型、推荐工具、D 距离上下文或位置上传接口。相同 `clientRequestId` 与同内容必须返回原 run，不刷新等待期限；同 ID 不同内容返回请求摘要冲突；有其它 active run 返回 409。查询接口仅用于初始化写请求结果未知时读取原结果，当前用户、session 或请求键不存在均返回 404。创建接口 MUST 仅对当前用户所属的等待 run 调用 D 的 `createForRun(runId)`，并以统一 `Result` 返回 `distanceContextId`、ISO-8601 `expiresAt` 和固定 `distancePreference="NEAREST"`。结果接口 MUST 只接受 UUID `distanceContextId`、固定枚举 `distancePreference="NEAREST"` 与 `locationResult=UPLOADED|DENIED|CANCELLED|FAILED`；成功响应 MUST 仅返回 `runId`、当前 `status` 和字符串 `lastEventId`，不得回传 distanceContextId。

#### Scenario: 初始化等待运行
- **WHEN** 当前用户以有效 `clientRequestId` 和普通推荐输入请求距离推荐
- **THEN** B 原子创建 user message 和 `WAITING_LOCATION` run，返回字符串 `lastEventId`
- **AND** run 不含 plan，且 B 不调用模型、推荐工具或 D 的位置能力

#### Scenario: 创建位置上下文
- **WHEN** 当前用户为同一等待 run 请求位置上下文
- **THEN** B 以可信 runId 调用 D 并返回不含坐标的上下文 DTO
- **AND** B 不将 distanceContextId 写入 run、消息、事件、Redis、日志或轨迹

#### Scenario: 上传成功恢复同一推荐节点
- **WHEN** 当前用户提交 `UPLOADED`、有效 UUID 与 `NEAREST`
- **THEN** B 仅在当前调用中将 ID 放入 ToolContext，并在同一 `runId + planVersion` 继续原 `rankMoviePlan`
- **AND** B 不创建新 run、不再次等待位置，也不在 SSE 返回 ID 或位置资料

### Requirement: 非上传成功结果必须清理并继续普通推荐

系统 SHALL 在 `DENIED`、`FAILED` 或等待超时时，对内存中仍可取得的 distanceContextId 调用 D `cleanup(distanceContextId, runId)`，并将 `NOT_FOUND` 视为清理成功。随后系统 MUST 在同一 `runId + planVersion` 继续普通推荐，ToolContext 的两个距离字段均为 null，且不得再次触发位置等待。主动取消 MUST 清理后进入 `CANCELLED`；运行失败 MUST 清理后进入 `FAILED`；两者不得继续推荐。

#### Scenario: 定位拒绝回退普通推荐
- **WHEN** 等待 run 收到 `DENIED`
- **THEN** B 至多一次清理内存中的上下文，并从 WAITING_LOCATION 恢复同一计划版本的普通推荐
- **AND** 结果、SSE 和持久化记录不包含上下文 ID、坐标、地址或定位来源

#### Scenario: 重启后的超时清理边界
- **WHEN** 进程重启后等待 run 超时且 B 不再拥有上下文 ID
- **THEN** B 恢复同一 run 的普通推荐而不伪造 cleanup 调用
- **AND** 未消费上下文由 D 的 300 秒 TTL 过期，直到 D 提供可信 runId 清理 API
