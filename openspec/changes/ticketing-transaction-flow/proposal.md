# Proposal: ticketing-transaction-flow

## 背景

A负责票务交易的订单、支付、电子票和退票权威状态。交易表基线已经完成MySQL 8.4验证，本change现扩展为完整的票务交易主线，按“原子锁座建单→订单查询/取消/过期→Mock支付→电子票”的顺序实现不依赖Agent的保底购票闭环。

## 目标

- 落地`ticket_order`、`ticket_order_seat`、`mock_payment`、`electronic_ticket`、`refund_request`五张交易表。
- 用数据库唯一约束、CHECK约束和查询索引冻结幂等、防重复支付、防重复退票与金额一致性底线。
- 实现原子锁座与幂等建单，支持按`clientRequestId`恢复丢失响应的订单。
- 实现本人订单查询、取消、过期释放、固定成功Mock支付和唯一电子票。
- 同步OpenAPI、调用方Mock和并发/幂等/恢复验收证据。
- 提供B/C可直接消费且受自动测试保护的票务联调JSON夹具。

## 非目标

- 不生成待支付、已支付或退票的固定交易种子；并发冲突由测试夹具和真实建单制造。
- 不修改已经在共享MySQL执行的V003迁移；如需Schema变更只能新增前向迁移。
- 不实现JWT解析、登录、Agent确认存储、SSE、提醒消费或外部内容数据。
- 不建立物理外键，不读取C认证、B Agent或D出行模块的Repository。

## Owner与协作

A拥有五张交易表、场次座位状态和交易状态机。C继续提供`CurrentUserAccessor`和安全路由；A在测试中替换身份端口，但不伪造JWT。B后续仅通过A的公开Application API/Tool Adapter调用交易能力。D后续消费事务提交后的`PaymentSucceededEvent`，消费失败不得回滚支付。
