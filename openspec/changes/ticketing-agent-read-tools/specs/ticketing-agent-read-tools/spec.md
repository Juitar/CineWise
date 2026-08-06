## ADDED Requirements

### Requirement: Agent 必须以类型化只读 Tool 查询可选日期

系统 SHALL 将已校验的 `movieId` 与 `cinemaId` 槽位转换为 `queryAvailableDates` Command，并仅通过 A 的公开 Application Service 查询未来可选日期。

#### Scenario: 合法查询返回空日期

- **WHEN** 已校验节点查询没有可选日期
- **THEN** Tool 返回 `SUCCESS` 和 `dates=[]`
- **AND** 不伪造日期、不写入订单或座位状态

#### Scenario: 日期查询参数非法或不可用

- **WHEN** 槽位引用、业务 ID 或 Command 转换非法
- **THEN** Adapter 不调用业务 Tool，返回 `FAILED + 100001` 且不可重试
- **WHEN** A 的票务查询不可用
- **THEN** Tool 返回 `FAILED + 306003`，可重试一次

### Requirement: Agent 必须以类型化只读 Tool 查询场次

系统 SHALL 将已校验的 `movieId`、`cinemaId`、`businessDate` 和可选时间窗口转换为 `queryShows` Command。结果只包含 A 当前公开场次摘要；Tool 不返回座位明细。

#### Scenario: 查询场次后进入选座

- **WHEN** Tool 返回未过期的场次摘要且用户明确选择一个 `showId`
- **THEN** B 可生成仅含场次摘要的 `SELECT_SEATS` 事件
- **AND** C 必须跳转既有购票页重新查询权威场次和座位状态

#### Scenario: 过期场次不得直接建单

- **WHEN** 场次摘要的 `expiresAt` 已到达或超过
- **THEN** 系统不得把该摘要当作建单授权
- **AND** 后续购票交易仍由 A 重新校验场次和座位

### Requirement: 查询 Tool 不得携带身份或写操作参数

两个查询 Tool 的 `ToolContext` SHALL 只携带运行关联、已声明输入来源、预算、traceId 与状态版本。它 MUST NOT 接收或生成 `userId`、`seatIds`、`clientRequestId`、`idempotencyKey`、订单或支付信息。

#### Scenario: 模型尝试注入额外字段

- **WHEN** 节点含有未声明输入引用或非槽位来源
- **THEN** Adapter 返回 `FAILED + 100001`
- **AND** 不调用业务 Tool

### Requirement: 成功查询必须携带受控时效窗口

两个查询 Tool 的成功 `ToolResult` SHALL 使用 A 注入的业务 `Clock` 填充成对的 `dataAt` 和 `expiresAt`。默认窗口为 `dataAt` 起五秒；`queryShows` 的公共 `expiresAt` 不得晚于返回场次中最早的场次候选截止时间。失败结果的两个字段 SHALL 同时为空，B 的 Adapter 不得重写它们。

#### Scenario: 成功查询返回可供 Agent 判断的新鲜度

- **WHEN** `queryAvailableDates` 或 `queryShows` 查询成功
- **THEN** `dataAt` 和 `expiresAt` 均非空
- **AND** `expiresAt` 严格晚于 `dataAt`
- **AND** `expiresAt` 不超过五秒窗口或场次候选的更早截止时间
