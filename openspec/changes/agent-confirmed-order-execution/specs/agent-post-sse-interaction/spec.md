## ADDED Requirements

### Requirement: 确认卡和确认结果必须是服务端受控事件
系统 SHALL 只从已持久化的 Agent action 生成确认卡和结果事件。确认卡必须继续使用既有 `card` 事件、公共 `payload`、顶层 `planVersion` 和 `occurredAt`，不得缩减公共 `AgentEvent` 字段。payload MUST 含 `actionId`、`actionType=CREATE_ORDER`、`expireAt`、公开状态和安全展示字段 `displayTitle?`、`displayLines?`；不得重复 `planVersion`。`displayLines` 只能由服务端生成的纯文本组成，前端不得解析其中的场次、座位、金额或订单参数，也不得作为 HTML 渲染。不得包含原始模型输出、完整 Command、参数摘要、幂等键、认证信息、完整订单或异常堆栈。

#### Scenario: 回放旧确认卡
- **WHEN** SSE 使用游标重连并回放历史确认卡
- **THEN** 系统按当前 action 状态把已过期、计划版本变化或已使用卡片显示为不可确认
- **AND** 回放本身不会触发确认或 A 建单

### Requirement: 确认动作公开状态必须稳定映射
系统 SHALL 在内部和向 C 的确认卡中统一使用 `PENDING_CONFIRMATION`、`EXECUTING`、`RESULT_UNKNOWN`、`SUCCEEDED`、`FAILED`、`EXPIRED`、`REJECTED`、`INVALIDATED` 八种状态，不公开第二套确认状态。所有公开终态卡片 MUST 只读。

#### Scenario: 公开状态决定卡片可操作性
- **WHEN** 确认卡状态为 `EXECUTING`、`RESULT_UNKNOWN` 或任一终态
- **THEN** 系统和前端均不得再次提交确认
- **AND** `EXECUTING`、`RESULT_UNKNOWN` 显示结果确认中，`EXPIRED`、`INVALIDATED` 显示已失效

### Requirement: 确认结果必须先保存再广播
系统 SHALL 在 action 状态和安全结果投影提交后，才写入可回放的 SSE 结果事件。事件重放、重复事件和断线恢复 MUST 只展示已保存事实，不能重新执行写工具。

#### Scenario: 结果保存与 SSE 顺序
- **WHEN** 建单适配器返回明确结果或结果未知
- **THEN** 系统先以 CAS 保存 action 状态和结果引用
- **AND** SSE 随后仅发送该已保存状态的安全投影
