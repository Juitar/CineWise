# frontend-agent-message-type-completion Specification

## Purpose
TBD - created by archiving change frontend-agent-message-type-completion. Update Purpose after archive.
## Requirements
### Requirement: 前端消息类型必须以 B 正式协议为准

系统 SHALL 只把 B 后端白名单、固定 JSON 夹具和实际 SSE 映射同时支持的 payload 加入 `AgentCardPayloadType`。旧 PRD 名称或其他模块 DTO 不得单独作为新增类型和字段的依据。

#### Scenario: 旧 PRD 名称没有后端夹具

- **WHEN** PRD 列出一个消息名称，但 B 没有正式类型、固定夹具或 SSE 映射
- **THEN** C 不增加前端枚举、校验、投影或组件
- **AND** OpenSpec 将该项标记为“等 B 提供正式协议”

#### Scenario: 固定选项问题不提供自由输入配置

- **WHEN** B 发送 `allowFreeText=false` 且不包含 `input` 的正式 `QUESTION`
- **THEN** C 接受并展示该问题的固定选项
- **AND** 只有 `allowFreeText=true` 时才要求 `input.name` 和 `input.type`

### Requirement: 建单确认必须复用统一确认卡

系统 SHALL 将 `PLAN_CARD + actionId + actionType=CREATE_ORDER` 视为建单确认的正式协议，MUST NOT 再增加 `ORDER_CONFIRM`。

#### Scenario: 收到正式建单确认夹具

- **WHEN** B 发送固定夹具中的建单确认 `PLAN_CARD`
- **THEN** C 使用现有脱敏确认展示和确认/拒绝回调
- **AND** 请求只提交 URL 中的 `actionId` 和请求体 `{confirmed}`

### Requirement: 缺少正式退票动作时不得推断退票确认

系统 SHALL 在 B 未提供正式退票 `actionType`、固定卡片夹具、action 创建/执行和 SSE 映射时，不实现 `REFUND_CONFIRM` 或统一退票确认卡。

#### Scenario: 通用确认解析器能够读取 actionId

- **WHEN** 前端通用确认分支技术上能读取一个字符串 `actionId`，但 B 只有 `CREATE_ORDER`
- **THEN** 系统不得据此声明退票确认已支持
- **AND** 不新增退票确认展示或写操作

### Requirement: 出行消息只能展示安全摘要

系统 SHALL 在 B 提供正式 `TRAVEL_ADVICE_CARD` 或 `ROUTE_CARD` 协议后，才允许实现对应前端类型。组件 MUST 只消费安全投影，不得接收原始 payload。

#### Scenario: B 提供路线正式夹具

- **WHEN** 正式路线夹具包含允许的路线安全摘要和时效信息
- **THEN** C 可将允许字段投影到类型化组件
- **AND** 精确起点、坐标、路线折线和途经点不得进入投影、页面或前端持久状态

### Requirement: 非法已知类型不得推进事件游标

系统 SHALL 对缺字段、未知字段或字段类型错误的已知 payload 拒绝投影，且 MUST NOT 推进 `lastEventId`；未知类型只显示固定安全占位。

#### Scenario: 已知出行类型缺少必填时效字段

- **WHEN** 未来正式出行类型缺少协议要求的必填字段
- **THEN** 前端拒绝该事件并保留原游标
- **AND** 不显示原始 payload 或猜测默认值

### Requirement: 确认恢复不得重发写请求

系统 SHALL 保持现有确认互斥和恢复规则。提交中、执行中、`RESULT_UNKNOWN` 和终态不得再次提交，网络超时和 SSE 重连只能查询原结果。

#### Scenario: 统一退票确认未来返回 RESULT_UNKNOWN

- **WHEN** B 后续正式退票确认返回 `RESULT_UNKNOWN`
- **THEN** C 使用原 `actionId` 对应的历史消息和运行快照恢复
- **AND** 不重发确认 POST 或生成新 actionId

#### Scenario: 建单确认 POST 返回结果未知

- **WHEN** 当前正式建单确认 POST 超时或返回 5xx
- **THEN** C 重新查询会话历史并从原确认卡消息读取顶层 `runId`
- **AND** 只调用 `GET /api/v1/agent/runs/{runId}` 恢复服务端状态
- **AND** 确认 POST 始终只发送一次

### Requirement: 待补消息类型不得阻塞当前主流程

系统 SHALL 在 `TRAVEL_ADVICE_CARD`、`ROUTE_CARD` 和退票确认协议尚未定义时，继续验证现有“多轮问答、推荐、建单确认、结果恢复”流程，MUST NOT 为通过联调提前增加兼容类型或字段。

#### Scenario: 当前主流程不包含出行卡

- **WHEN** B 使用现有正式类型完成推荐和建单确认
- **THEN** C 按现有协议完成展示、确认和结果恢复
- **AND** 不因缺少出行、路线或退票卡阻止当前联调

### Requirement: 最新推荐卡必须按正式 DTO 严格投影

系统 SHALL 按 PR #160 的固定 `PLAN_CARD` DTO 校验普通推荐卡。正式必填字段缺失、字段类型错误或出现未声明字段时 MUST 拒绝事件且不推进游标；确认卡继续使用独立的现有确认校验分支。

#### Scenario: 收到最新合法推荐卡夹具

- **WHEN** B 发送包含正式方案项、来源和时效字段的固定 `PLAN_CARD`
- **THEN** C 展示影片名、影院名、价格与币种、开场时间、推荐理由、来源和状态
- **AND** 不展示排序分、内部证据、工具参数或模型推理

#### Scenario: 推荐卡出现未声明字段

- **WHEN** 普通 `PLAN_CARD` 顶层或方案项包含正式 DTO 未声明的字段
- **THEN** C 拒绝该事件并保留原游标
- **AND** 不把该字段传给安全投影或组件

