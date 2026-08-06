## ADDED Requirements

### Requirement: 服务端创建一次性确认动作
系统 SHALL 仅在已校验的运行计划包含受支持创建订单节点时创建确认动作。动作 MUST 绑定当前登录用户、sessionId、runId、planId、planVersion、节点/工具名、服务端构造的 Command、服务端计算的参数摘要、摘要版本、有效期、状态、版本和审计时间；模型、前端和确认请求均不得提供 userId、ticketCount、金额、订单状态、参数摘要、确认凭证或最终业务状态。

#### Scenario: 合法计划生成确认卡
- **WHEN** 服务端已校验当前运行、计划版本和创建订单 Command
- **THEN** 系统创建只属于当前用户且初始为 `PENDING_CONFIRMATION` 的 action
- **AND** 仅发布含 actionId、planVersion、expireAt 和安全展示信息的确认卡

### Requirement: 参数摘要必须保护已校验 Command
系统 SHALL 使用 `hash_version=v1` 加 64 位小写 SHA-256 十六进制值覆盖工具名、场次、排序后的座位和其他经服务端校验的写参数。确认前 MUST 重新从已保存 Command 计算摘要，并校验运行、计划版本、节点和相关业务候选仍有效。

#### Scenario: 参数或版本已变化
- **WHEN** 保存 action 后 Command 摘要不一致，或 action 的 planVersion 不再是当前运行计划版本
- **THEN** 系统拒绝确认且分别返回 `206004` 或固定的计划版本变化结果
- **AND** 不调用 A、不生成新 action 或新幂等键

### Requirement: action 归属、有效期和拒绝必须受控
系统 SHALL 仅允许 action 所属当前用户在有效期内确认或拒绝。不存在和非本人 action MUST 返回相同安全资源结果；已过期 action 返回 `206003`。用户提交 `confirmed=false` 时，系统 MUST 将可拒绝 action 标为 `REJECTED`，不得调用写工具或创建稳定键。

#### Scenario: 非本人或过期动作
- **WHEN** 当前用户访问他人的 action，或确认时间不早于 expireAt
- **THEN** 系统不返回 Command、摘要或订单信息，并拒绝动作
- **AND** 不发生任何 A 写调用

### Requirement: 同一 action 的确认只能由一个请求取得执行权
系统 SHALL 以版本 CAS 或等价条件更新把 `PENDING_CONFIRMATION` 原子推进为 `EXECUTING`。已经 `EXECUTING`、`RESULT_UNKNOWN` 或已完成的 action 重复确认 MUST 返回既有安全结果或 `206006`，不得再次进入写工具；终态和结果未知状态不得被旧请求覆盖。

#### Scenario: 并发确认同一动作
- **WHEN** 两个请求并发确认相同 actionId
- **THEN** 至多一个请求从 `PENDING_CONFIRMATION` 成功推进为 `EXECUTING` 并调用一次写工具
- **AND** 另一请求读取已经提交的状态或结果，不再调用写工具
