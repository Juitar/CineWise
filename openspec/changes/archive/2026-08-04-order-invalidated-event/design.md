# Design: 退款完成后登记订单失效事件

## Context

现有退款事务以数据库行锁、每订单唯一退款和条件状态更新保证 `PAID -> REFUNDING -> REFUNDED`，并同步完成电子票失效和已售座位释放。现有支付事件已经证明了“事务外解析内容上下文、事务内登记、D 在 AFTER_COMMIT 消费”的边界，本 change 将同一规则用于退款失效事件。

## Decisions

### 1. 支付与退款共用出行事件上下文解析器

将内部 `PaymentEventContextResolver` 重命名为 `TravelEventContextResolver`。解析器先从 A 的 `ShowContextQueryService` 获取 `showId/cinemaId/startTime`，再通过 D 的公开 `ContentSummaryQueryPort` 获取未过期且区域非空的影院摘要，最后形成不可变 `TravelEventContext`。

解析发生在支付或退款事务外。内容摘要缺失、过期或异常只使本次实时事件缺席，不能扩大交易事务，也不能由 A 猜测或保存第二份影院区域事实。

### 2. 事件只在首次退款事务内登记

`RefundApplicationService` 在输入规范化和身份解析后先读取本人订单并尝试解析事件上下文，再调用 `RefundTransaction`。事务加锁后重新读取权威订单并核对上下文 `showId`，只有不存在既有退款且全部退款状态迁移完成后才创建 `OrderInvalidated`。

既有退款分支直接返回权威终态，不调用发布端口。这样原键重放、新键恢复和并发串行后的第二个请求都不会生成第二个事件。退款完成后的 `RefundView.stateVersion` 作为事件 `orderVersion`，不使用内存推算值。

### 3. Spring 事务事件负责提交门禁

新增 `OrderInvalidatedPublisher` 和 `SpringOrderInvalidatedPublisher`。A 在事务仍活动时调用 Spring `ApplicationEventPublisher`；D 只能使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 消费。因此退款回滚不会触发 D，消费者失败也发生在 A 事务提交之后。

发布端口同步抛出的运行时异常在事务内被捕获并记录最小日志，不能把已完成的退款标记回滚。MVP 不引入消息队列或事件表；遗漏事件由后续 REFUNDED 订单对账调用 D 的 `ensureTaskCancelled` 补偿。

### 4. 事件字段与隐私

`OrderInvalidated` 使用随机 UUID 作为 `eventId`；业务 ID 以十进制字符串输出；`startAt` 和 `occurredAt` 以 `Asia/Shanghai` 转换为带偏移时间；`invalidReason` 固定为 `REFUNDED`。事件不包含订单座位、金额、邮箱、支付凭据、二维码、精确位置或路线。

## Compatibility and Impact

- 不修改 REST、OpenAPI、数据库表、迁移、权限或前端契约。
- `PaymentEventContextResolver` 是 A 模块内部 Spring Bean，重命名时同步所有生产与测试引用，不改变支付事件字段或行为。
- D 可在其 change 中消费新事件；消费者尚未合入时 Spring 发布无监听器仍安全，补偿入口由 D 后续实现。

## Verification

- H2/Spring 集成测试覆盖首次退款、原键/新键重放、并发唯一事件、事务回滚、同步登记异常、提交后消费异常和上下文降级。
- 字段测试固定 `Clock`，断言十进制 ID、`+08:00`、固定原因和退款后版本。
- 执行 `openspec validate order-invalidated-event --strict`、定向 Maven 测试、`mvnw.cmd verify`、注释率检查和 `git diff --check`。
