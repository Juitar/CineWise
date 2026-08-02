# Proposal: ticketing-transaction-flow

## 背景

A负责票务交易的订单、支付、电子票和退票权威状态。今日任务只要求先落地订单相关表，为后续原子建单、Mock支付和模拟退票提供稳定数据库基线；本change保留完整交易范围，但今日不提前实现写接口。

## 目标

- 落地`ticket_order`、`ticket_order_seat`、`mock_payment`、`electronic_ticket`、`refund_request`五张交易表。
- 用数据库唯一约束、CHECK约束和查询索引冻结幂等、防重复支付、防重复退票与金额一致性底线。
- 为后续锁座建单、支付、电子票和退票实现保留明确任务和验证入口。

## 非目标

- 今日不实现订单Controller、交易Application Service或状态迁移。
- 今日不写入待支付、已支付或退票种子记录。
- 今日不执行Flyway迁移；由A审查SQL后手动执行。
- 不建立物理外键，不读取C认证、B Agent或D出行模块的Repository。

## Owner与协作

A拥有五张交易表及其后续状态机。C提供`currentUserId`，B消费公开票务工具，D消费支付成功事件；今日数据库基线不实现这些跨模块行为。
