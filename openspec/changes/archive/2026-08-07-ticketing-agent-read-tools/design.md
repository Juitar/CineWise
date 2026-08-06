## Design

### Boundary

两个 Adapter 实现 B 提供的 `ReadOnlyToolExecutionAdapter`，只读取已校验节点的 `SlotSnapshot` 和输入引用，构造不含用户身份、写键或座位数据的 `ToolContext`。Adapter 只调用 A 的公开 Tool API；不调用 Controller、Mapper、Repository、Entity、Supervisor 或 SSE 组件。

### Tool contracts

| Tool | Required inputs | Optional inputs | Successful data | Errors |
| --- | --- | --- | --- | --- |
| `queryAvailableDates` | `movieId`, `cinemaId` | 无 | `dates[{date,showCount}]` | `100001`, `306003` |
| `queryShows` | `movieId`, `cinemaId`, `businessDate` | `timeFrom`, `timeTo` | 当前公开场次摘要列表 | `100001`, `306003` |

业务 ID 是正十进制 `String`，日期和时间由 Adapter 转为 `LocalDate`、`LocalTime`。无结果是 `SUCCESS`，不生成虚构场次或日期。`availableSeatCount` 仅为读取时快照，不作为锁座承诺；每个场次的 `expiresAt` 仅是该候选的截止时刻。

两个成功 `ToolResult` 都由 A 的业务 `Clock` 生成 freshness 元数据：`dataAt` 是本次 Application 查询完成时刻，默认 `expiresAt=dataAt+5s`。`queryShows` 的公共 `expiresAt` 还不得晚于返回场次中最早的候选 `expiresAt`；失败结果的两个字段保持同时为空。B 的 Adapter 只消费这两个字段，不自行生成时间。

### Execution and failure

Adapter 重新从状态机选择当前可执行节点，先标记 `RUNNING`，再调用业务 Tool，并将唯一 `ToolResult` 交回 `recordToolResult`。调用预算取 `min(5 seconds, remainingDeadlineMs)`；只读 Tool 不生成 `clientRequestId` 或 `idempotencyKey`。

参数转换失败不调用业务 Tool，返回 `FAILED + 100001` 且不可重试。业务 Tool 将已知 `306003` 返回为可重试一次的失败；未知异常不伪装为空结果。`204001`、`204002`、`204003` 不属于两个查询 Tool 的公开错误集合。

### Delivery sequence

先合入只读兼容的 Tool API 与 Adapter，再由 B 的既有执行框架调用。C 收到 `SELECT_SEATS` 后只使用场次摘要跳转购票页；购票页重新查询 A 的权威场次与座位图。
