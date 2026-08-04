# Change: 退款完成后登记订单失效事件

## Why

退款主链已经能原子完成退款记录、订单、电子票和座位状态迁移，但 D 的出行提醒尚未收到订单失效事实。若只保留支付成功事件，已退款订单仍可能保留提醒任务或继续发送通知。

## What Changes

- A 新增稳定的 `OrderInvalidated` 进程内事件，复用 `PaymentSucceededEvent` 的字段并增加 `invalidReason`。
- 仅首次成功退款在交易事务内登记事件；D 的消费者必须绑定 `AFTER_COMMIT`。
- 支付和退款共用事务外的出行事件上下文解析规则，缺失、过期或异常内容摘要不阻塞交易主链。
- 同步事件登记异常与提交后消费者异常不得改变已经完成的退款结果；遗漏事件由后续 REFUNDED 订单对账补偿。

## Scope

### In Scope

- `order` 模块的事件契约、发布端口、Spring 适配器和退款事务编排。
- 支付与退款共享的场次、影院区域、开场时间上下文解析。
- 事件提交时机、字段、幂等、回滚与失败隔离的自动化测试。

### Out of Scope

- D 的 `OrderInvalidated` 消费器、`ensureTaskCancelled`、出行表或提醒状态实现。
- A 的 REFUNDED 订单定时对账任务。
- REST、前端、认证、安全链、数据库迁移或 Flyway SQL。

## Ownership and Confirmation

- A 拥有退款状态、事件生产端和本 change 的实现。
- D 拥有出行消费者与任务数据；`travel-reminder-experience` 已冻结事件字段、`REFUNDED` 原因、退款后版本和 `AFTER_COMMIT` 消费边界。
- 本 change 不访问 D 的 Entity、Mapper、Repository 或表，只读取 D 的公开 `ContentSummaryQueryPort`。

## Acceptance

- 首次成功退款提交后恰好产生一个字段完整的 `OrderInvalidated`。
- 原幂等键重放、新幂等键恢复和并发退款不登记第二个有效事件。
- 退款回滚时不触发提交后消费者。
- 上下文缺失、过期、查询异常、同步登记异常或消费者异常均不伪造交易失败。
- OpenSpec 严格校验、定向集成测试和后端 `mvnw.cmd verify` 通过。

