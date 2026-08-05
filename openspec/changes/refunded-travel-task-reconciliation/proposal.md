# Change: 补偿已退款订单遗漏的出行任务取消

## Why

退款完成后，A 通过进程内 `AFTER_COMMIT` 事件通知 D 取消出行任务。若应用在提交后退出、监听器异常或 D 暂时失败，订单已经正确进入 `REFUNDED`，但对应出行任务可能仍可用。系统需要从 A 的退款终态恢复遗漏的取消动作，并与已实现的 PAID 补偿共同收敛退款竞态。

## What Changes

- A 每五分钟扫描最近二十四小时仍为 `REFUNDED` 且存在 `refunded_time` 的订单，按 `refunded_time/id` 稳定键集分页，每批最多一百条。
- A 在调用 D 前重读订单，只有状态仍为 `REFUNDED` 且版本与候选一致时，才从 A 场次事实和 D 公开影院摘要重建 `OrderInvalidated`。
- A 逐条调用 D 已公开的 `TravelTaskApplicationService.ensureTaskCancelled`；重复、乱序和多实例竞争由 D 的订单版本、`orderId` 唯一约束及 `CANCELLED` 墓碑收敛。
- 单条上下文缺失或调用失败不影响后续候选，也不修改订单、支付、电子票、座位或退款状态。
- 补充“PAID 补偿重读后发生退款、随后仍调用 ensureTask”的竞态联调；验证最终任务保持 `CANCELLED` 后，再默认启用 PAID 与 REFUNDED 两类补偿 Job。

## Scope

### In Scope

- A 的 `order` 查询端口、REFUNDED 候选持久化查询、补偿应用服务和 `job` 调度入口。
- 最近二十四小时、每批最多一百条、逐条失败隔离、重复执行和多实例安全。
- A 与 D 公开 `ensureTaskCancelled(OrderInvalidated)` 的 H2/MySQL 集成验证。
- 完成 `paid-travel-task-reconciliation` 任务 3.3，并在安全门通过后开启两类补偿默认调度。

### Out of Scope

- D 的 `travel_task`、建议、通知、状态规则、Entity、Mapper、Repository 或数据库结构。
- 修改退款事务、REST、前端、认证、安全链、Agent、Flyway SQL 或任何表结构。
- 延长二十四小时恢复窗口、永久全表补扫或新增消息队列。

## Ownership and Confirmation

- A 拥有订单扫描、事件重建、批处理报告和调度实现。
- D 拥有 `ensureTaskCancelled`、任务版本比较、取消墓碑和最终任务状态；其实现已由 PR #52 合入 `dev`。
- A 只调用 D 的公开 Application Service，不查询、更新或 JOIN D 的表，也不以 HTTP 调用本应用 Controller。

## Acceptance

- 最近二十四小时的 REFUNDED 订单按 `refunded_time/id` 稳定分页，每个数据库查询批次不超过一百条。
- 非 REFUNDED、窗口外、`refunded_time` 为空、状态或版本已变化的订单不调用 D。
- 重复扫描、退款事件先到、迟到 PAID 补偿和多实例竞争均只保留一个 `CANCELLED` 任务，且任务版本不回退。
- 单条失败不终止当前批次或后续批次，下轮可安全重试。
- 不改变交易数据、数据库结构、REST 或前端契约；完整后端质量门通过。
