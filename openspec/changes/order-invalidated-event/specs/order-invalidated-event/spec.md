# Order Invalidated Event Specification

## ADDED Requirements

### Requirement: 首次退款完成必须登记冻结的订单失效事件

系统 SHALL 仅在订单、退款记录、电子票和座位已成功迁移到退款终态的首次退款事务内登记 `OrderInvalidated`。事件 SHALL 包含 `eventId`、`orderId`、`showId`、`userId`、`cinemaArea`、`startAt`、`orderVersion`、`occurredAt` 和 `invalidReason`；`invalidReason` 在 MVP 固定为 `REFUNDED`，`orderVersion` 必须等于退款完成后的权威订单版本。

#### Scenario: 首次退款成功

- **GIVEN** 本人订单处于 `PAID`，电子票有效且全部订单座位为 `SOLD`
- **AND** A 能通过公开 Application API 取得完整、未过期的出行事件上下文
- **WHEN** 退款事务成功提交
- **THEN** D 的 `AFTER_COMMIT` 消费者收到恰好一个 `OrderInvalidated`
- **AND** 事件业务 ID 为十进制字符串、时间使用 Asia/Shanghai 对应偏移、原因固定为 `REFUNDED`

#### Scenario: 退款事务回滚

- **GIVEN** 退款状态迁移、电子票失效或座位释放中的任一步失败
- **WHEN** 退款事务回滚
- **THEN** `AFTER_COMMIT` 消费者不收到 `OrderInvalidated`

### Requirement: 重放和并发退款不得重复登记失效事件

系统 SHALL 以既有退款记录和数据库并发保护作为事件登记边界。原幂等键重放、同订单新幂等键恢复以及并发失败请求 SHALL 返回或查询同一退款结果，不得生成新的 `eventId` 或登记第二个有效事件。

#### Scenario: 成功退款后重复请求

- **GIVEN** 订单已有唯一成功退款并已登记失效事件
- **WHEN** 调用方使用原幂等键重放或使用新幂等键恢复
- **THEN** 系统返回同一退款结果
- **AND** 提交后消费者累计只收到首次退款的一个事件

#### Scenario: 两个请求并发退款

- **GIVEN** 两个请求同时退款同一已支付订单
- **WHEN** 数据库行锁和唯一约束决定唯一退款
- **THEN** 两个调用恢复到同一退款结果
- **AND** 仅创建唯一事件标识并触发一次提交后消费

### Requirement: 非关键事件链路不得破坏退款权威终态

系统 SHALL 在退款事务外解析 A 的场次事实和 D 的影院公开摘要。上下文缺失、过期或查询异常时 SHALL 跳过实时事件登记且不伪造字段；同步登记异常和 `AFTER_COMMIT` 消费异常 SHALL 被隔离，订单仍保持 `REFUNDED` 并可通过原订单号查询恢复。遗漏取消由后续 REFUNDED 订单对账补偿。

#### Scenario: 出行上下文不可用

- **GIVEN** 场次上下文缺失，或影院摘要缺失、过期、区域为空或查询异常
- **WHEN** 用户完成退款
- **THEN** 退款主链成功并可查询到 `REFUNDED`
- **AND** 系统不发布包含猜测区域或时间的失效事件

#### Scenario: 同步事件登记失败

- **GIVEN** Spring 事件登记期间同步监听器抛出异常
- **WHEN** 退款完成权威状态迁移
- **THEN** 退款事务仍提交成功且结果可查询恢复

#### Scenario: 提交后消费者失败

- **GIVEN** `AFTER_COMMIT` 消费者处理 `OrderInvalidated` 时抛出异常
- **WHEN** 退款事务已经提交
- **THEN** 订单继续保持 `REFUNDED`
- **AND** 后续对账可以按退款完成后的版本补偿取消任务

### Requirement: 事件必须遵守模块与隐私边界

系统 SHALL 只通过 A 的场次公开服务与 D 的 `ContentSummaryQueryPort` 组合事件上下文，不得访问 D 的 Entity、Mapper、Repository 或表。事件 MUST NOT 包含邮箱、密码、模拟支付凭据、座位明细、二维码、精确位置或路线几何。

#### Scenario: 审查事件契约和调用依赖

- **WHEN** 审查生产代码和事件字段
- **THEN** 跨模块依赖仅指向公开 Application API
- **AND** 事件只包含冻结的九个最小字段

