# Design: REFUNDED 订单出行任务取消补偿

## Context

A 已在首次成功退款事务内登记 `OrderInvalidated`，D 使用 `AFTER_COMMIT` 监听器调用 `TravelTaskApplicationService.ensureTaskCancelled`。进程内事件没有持久投递保证，因此退款已经完成而任务尚未取消是允许出现并必须由对账恢复的状态。D 已按 `invalidation_event_id`、`order_id` 和 `order_version` 实现幂等取消、乱序保护及退款先到的 `CANCELLED` 墓碑。

## Decisions

### 1. 以 refunded_time 和冻结窗口扫描退款终态

在 `OrderRepository` 增加只读投影 `RefundedTravelReconciliationCandidate` 和键集分页查询。查询固定过滤 `status=REFUNDED`、`refunded_time IS NOT NULL`，并限制 `refunded_time` 位于任务开始时冻结的 `[windowStart, windowEnd]`；排序使用 `refunded_time ASC, id ASC`。

每页最多一百条，一轮继续读取到冻结上界。键集分页避免相同毫秒退款在分页边界重复或遗漏。现有订单状态索引可使用 `status` 前缀缩小范围，MVP 数据量不为该恢复查询新增迁移；若指标显示扫描变慢，再由 A 单独申请 `(status, refunded_time, id)` 向前索引迁移。

### 2. 候选进入跨模块调用前重读权威订单

`RefundedTravelTaskReconciliationService` 对每条候选按主键调用 A 的 `OrderRepository.findById`。只有订单仍为 `REFUNDED` 且版本等于候选版本时继续；否则计为跳过。扫描服务不启动 A 写事务，也不持有订单锁调用 D，避免扩大本地事务边界。

`TravelEventContextResolver` 继续组合 A 的场次事实与 D 的公开影院摘要。场次、影院区域缺失、过期或查询异常时本轮跳过，下轮重新尝试；A 不访问 D 私有持久层，也不猜测区域。

### 3. 重建 OrderInvalidated 保留退款发生时间

每次补偿生成新的 UUID `eventId`，以便 D 审计本次投递。`orderId/showId/userId` 使用十进制字符串，`orderVersion` 取当前权威退款版本，`startAt` 来自场次，`occurredAt` 使用订单原始 `refunded_time`，时间统一转换为 Asia/Shanghai 对应的 `OffsetDateTime`，`invalidReason` 固定为 `REFUNDED`。

事件不携带金额、座位、电子票、邮箱、支付凭据、精确位置或路线几何。

### 4. A 逐条调用，D 负责最终取消幂等

A 不保存第二份任务取消标记，也不读取 `travel_task` 判断当前状态。所有合格候选均调用 D 的 `ensureTaskCancelled`：已有任务按版本取消或返回终态；任务尚不存在时创建 `CANCELLED` 墓碑；重复和多实例竞争由 D 的唯一约束及条件更新裁决。

每条候选单独捕获运行时异常并继续，报告聚合批次数、扫描数、取消确保数、跳过数和失败数。D 的公开服务使用独立事务；D 失败不会回滚 A 已完成的退款。

### 5. 两类补偿共同形成退款竞态闭环

关键竞态为：PAID 补偿已经重读到 `PAID`，随后退款完成，PAID 补偿仍使用旧支付事实调用 `ensureTask`。测试必须真实经过 A 的 PAID 服务、退款应用服务和 D 的两个公开入口，验证无论取消先到还是支付补偿迟到，最终任务为 `CANCELLED` 且版本不低于退款完成版本。

新增 `RefundedTravelTaskReconciliationJob`，与 PAID Job 分别每五分钟执行。测试配置继续关闭自动调度，避免上下文测试产生后台竞争；生产默认值仅在上述竞态、重复和 MySQL 键集分页验证通过后改为启用。两个 Job 只管理 traceId、调用所属 Application Service 并记录汇总。

## Compatibility and Impact

- 不新增或修改数据库表、索引和 Flyway 文件。
- 不修改 REST、OpenAPI、权限、前端或事件字段。
- 新查询只读取 A 拥有的 `ticket_order`；跨模块只调用 D 的公开 Application Service。
- 新配置位于 `cinewise.transaction.refunded-travel-reconciliation`；PAID 原配置名称保持兼容。

## Verification

- 单元测试覆盖多页键集扫描、空窗口、状态/版本变化、上下文缺失、D 调用失败隔离和事件字段。
- H2 集成测试覆盖退款监听遗漏后的取消补偿、重复补偿、窗口过滤及 PAID 重读后发生退款的竞态。
- MySQL 8.4 集成测试覆盖相同毫秒退款、批次大小为一、重复取消与 `CANCELLED` 唯一任务。
- Job 测试覆盖服务调用、MDC 清理和配置开关。
- 执行两个相关 OpenSpec 严格校验、定向测试、`mvnw.cmd verify`、有效注释审查和 `git diff --check`。
