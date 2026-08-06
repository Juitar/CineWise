## Purpose

定义 Agent 已确认方案的服务端标识和确认后画像反馈规则。

## Requirements

### Requirement: 服务端方案标识
系统 SHALL 在有效方案进入执行和持久化前生成 `UUID.randomUUID().toString()` 格式的小写 UUID `planId`，不得采用模型提供的 ID。同一已展示方案的运行、PLAN_CARD、确认动作和反馈 MUST 使用同一个 `planId`。

#### Scenario: 模型候选 ID 被替换
- **WHEN** 模型返回通过校验的候选方案
- **THEN** 运行持久化的 planId 是服务器生成的 36 位小写 UUID

### Requirement: 确认后画像反馈
系统 SHALL 仅在确认动作 CAS 实际保存最终 `REJECTED` 后调用 `recordPlanRejected`，或在 `createOrder` 成功且 CAS 实际保存 `SUCCEEDED` 后调用 `recordPlanAccepted`。调用使用 `actionId` 作为 eventId、动作保存的 planId 和 UTC `occurredAt`。重复请求读取到其他请求结果时不得调用。

#### Scenario: 订单成功后接受方案
- **WHEN** createOrder 成功且本请求成功保存 SUCCEEDED
- **THEN** 系统调用一次 accepted，参数复用 actionId、planId 和 UTC 时间

#### Scenario: 拒绝方案
- **WHEN** 本请求成功保存 REJECTED
- **THEN** 系统调用一次 rejected，参数复用 actionId、planId 和 UTC 时间

### Requirement: 不记录无效或不确定确认
系统 MUST NOT 在仅展示、取消、过期、无权限、参数校验失败、确认条件变化、订单失败、订单结果未知或 CAS 未成功时调用画像写入。画像写入异常 MUST NOT 回滚订单或确认动作，且系统不得自动重试或补发。

#### Scenario: 画像写入失败
- **WHEN** accepted 或 rejected 调用抛出异常
- **THEN** 系统保留已保存的确认和订单结果且不再次调用
