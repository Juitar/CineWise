# Design: PAID 订单出行任务补偿

## Context

A 已在支付事务内登记 `PaymentSucceededEvent`，D 使用 `AFTER_COMMIT` 监听器调用 `TravelTaskApplicationService.ensureTask`。进程内事件没有持久投递保证，因此支付正确提交而任务缺失是允许出现并必须恢复的状态。D 的公开入口已经按 `eventId` 和 `orderId` 双重幂等，并在独立事务中使用任务表唯一约束裁决并发。

## Decisions

### 1. A 以 paid_time 和冻结窗口扫描权威订单

在 `OrderRepository` 增加专用只读投影 `PaidTravelReconciliationCandidate` 和键集分页查询。查询条件固定为 `status=PAID`、`paid_time IS NOT NULL`、`paid_time` 位于 `[windowStart, windowEnd]`；排序为 `paid_time ASC, id ASC`。任务开始时只读取一次 `Clock`，随后所有分页使用同一窗口，避免长任务执行期间不断纳入新订单。

每页最多一百条，但一轮会继续读取后续页面直到窗口末尾。选择键集分页而不是 OFFSET，是为了避免相同支付时间、并发新增和大偏移导致重复或遗漏。现有 `idx_order_status_expire` 可使用 `status` 前缀缩小候选范围；MVP 订单量有限，本 change 不为该恢复查询新增迁移。若生产指标显示扫描变慢，再由 A 通过独立迁移申请增加 `(status, paid_time, id)` 索引。

### 2. 候选不是最终事实，调用 D 前必须重读

`PaidTravelTaskReconciliationService` 对每个候选使用 `findById` 重读订单。只有状态仍为 `PAID` 且版本等于候选版本时才解析上下文，降低扫描后发生退款或状态变化时创建迟到任务的概率。跨模块调用不放入 A 数据库事务；支付与退款事件最终由 D 的订单版本和后续取消能力收敛极短竞争窗口。当前 D 尚未交付取消入口，因此不能把这次重读当作退款竞态的最终保护。

`TravelEventContextResolver` 继续组合 A 的场次事实与 D 的公开影院摘要。空、过期或异常上下文均按本轮跳过处理，不读取 D 私有数据，也不猜测区域。

### 3. 重建事件保留原支付发生时间

补偿使用新 UUID 作为 `eventId`，以便 D 审计本次投递；`orderId/showId/userId` 为十进制字符串，`orderVersion` 取当前权威 PAID 版本，`startAt` 来自场次，`occurredAt` 使用候选的原始 `paid_time`，两者转换为 Asia/Shanghai 对应的 `OffsetDateTime`。补偿时间不冒充支付发生时间。

### 4. A 逐条调用，D 决定任务幂等

应用服务不加本地完成标记，也不访问 `travel_task` 判断缺失。它对所有候选调用 D 的 `ensureTask`：已有任务返回原摘要，缺失任务创建唯一记录，多实例竞争由 D 的唯一约束解决。A 不使用 JVM 锁或 Redis 锁作为正确性依据。

每条候选单独捕获运行时异常并继续，报告聚合批次数、扫描、成功、跳过和失败数量。D 的调用使用其现有 `REQUIRES_NEW` 事务；A 的扫描服务不持有订单事务或数据库锁。

### 5. Job 只负责调度和 trace

新增 `PaidTravelTaskReconciliationJob`，默认关闭；只有部署显式设置 `PAID_TRAVEL_RECONCILIATION_ENABLED=true` 才会注册。D 的 `ensureTaskCancelled(OrderInvalidated)` 合入、退款事件消费与“重读后退款再 ensure”竞态联调通过前，运维不得开启该开关。启用后每五分钟固定延迟执行，启动后同样等待五分钟，避免应用启动阶段与迁移、种子和其他恢复任务争抢资源。Job 只创建 traceId、调用应用服务并记录汇总，不访问 Repository 或 D 服务。

配置位于 `cinewise.transaction`：启用开关、延迟、窗口小时数和批次大小。窗口默认二十四小时、批次默认一百，校验范围防止无界扫描。

## Compatibility and Impact

- 不新增或修改数据库表、索引和 Flyway 文件。
- 不修改 REST、OpenAPI、权限、前端或事件字段。
- 新查询只读取 `ticket_order`，新跨模块调用只依赖 D 的公开 Application Service。
- `REFUNDED` 取消补偿不在本 change；D 完成 `ensureTaskCancelled` 后独立实现。在该前置条件满足前，PAID 补偿 Job 保持默认关闭。

## Verification

- 单元测试覆盖多页键集扫描、空窗口、状态/版本变化、上下文缺失、D 调用失败隔离和事件字段。
- Spring 集成测试覆盖真实订单查询、支付后删除测试任务再补建、重复补偿仍唯一以及窗口过滤。
- MySQL 8.4 集成测试覆盖相同毫秒支付、批次大小为一的键集分页和任务唯一约束联调。
- Job 测试覆盖服务调用、MDC 清理和缺少显式开关时不注册 Job。
- 执行 `openspec validate paid-travel-task-reconciliation --strict`、定向测试、`mvnw.cmd verify`、有效注释审查和 `git diff --check`。
