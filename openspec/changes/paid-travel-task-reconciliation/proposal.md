# Change: 补偿已支付订单缺失的出行任务

## Why

支付成功事件是进程内事务事件。支付事务已经提交后，D 的监听器仍可能因进程退出、内容暂不可用或消费异常而没有创建出行任务。订单保持 `PAID` 是正确结果，但用户会缺少后续出行建议和提醒，因此需要从 A 的权威订单状态恢复遗漏任务。

## What Changes

- A 每五分钟扫描最近二十四小时仍为 `PAID` 的订单，以 `paid_time` 作为窗口事实，每批最多一百条。
- A 从订单和场次权威数据重建 `PaymentSucceededEvent`，影院区域仅通过 D 的公开 `ContentSummaryQueryPort` 获取。
- A 逐条调用 D 已公开的 `TravelTaskApplicationService.ensureTask`；D 继续以 `orderId` 和数据库唯一约束保证重复补偿不创建第二个任务。
- 单条上下文缺失或调用失败不回滚其他订单，也不改变订单、支付、座位或电子票状态；下轮扫描继续重试。
- 新增有界配置、默认关闭的调度入口、批处理报告和正常、重复、分页、失败隔离测试。

## Scope

### In Scope

- A 的 `order` 查询端口、持久化查询、补偿应用服务和 `job` 调度入口。
- 最近二十四小时、每批最多一百条、逐条隔离和多实例安全规则。
- 与 D `ensureTask(PaymentSucceededEvent)` 的公开 Application API 联调测试。
- 在 D 交付 `ensureTaskCancelled(OrderInvalidated)` 前，调度入口默认关闭；仅在退款取消补偿和竞态联调完成后，才可由运维显式开启。

### Out of Scope

- D 的 `travel_task`、建议、通知、Mapper、Repository、状态规则或数据库结构。
- `REFUNDED` 订单和 `ensureTaskCancelled(OrderInvalidated)`；D 完成取消入口后另建独立 change。
- REST、前端、认证、安全链、Agent、Flyway SQL 或任何数据库结构变更。

## Ownership and Confirmation

- A 拥有订单扫描、事件重建、批处理与调度实现。
- D 拥有 `TravelTaskApplicationService.ensureTask` 和任务幂等；`travel-reminder-experience` 已确认该 API 专供事件监听器和 A 的 PAID 对账共同调用。
- A 不查询、更新或 JOIN D 的表，也不以 HTTP 调用本应用 Controller。

## Acceptance

- 最近二十四小时的 PAID 订单按 `paid_time/id` 稳定分页，每个数据库查询批次不超过一百条。
- 同一订单重复扫描或多实例同时扫描只保留一个 D 出行任务。
- 非 PAID、窗口外、上下文缺失和扫描前已退款订单不会被 A 伪造成有效支付事件；退款取消补偿未就绪时，生产默认不启动该 Job。
- 单条失败不终止当前批次或后续批次，下轮执行可安全重试。
- 不修改交易状态、数据库结构、REST 或前端契约；完整后端质量门通过。
