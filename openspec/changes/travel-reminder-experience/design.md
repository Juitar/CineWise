## Context

见 [proposal.md](proposal.md)。当前 `travel` 只有包骨架；A 已定义但尚未接通 `PaymentSucceededEvent` 的事务后发布，原因是其变更记录仍等待 D 提供公开 `cinemaArea` 摘要。C 的认证、公共邮件端口和路线展示仍在进行。出行详细设计已冻结事件字段、任务状态、通知恢复和位置隐私规则。

## Goals / Non-Goals

**Goals:**

- 建立支付后提醒、天气建议、基础路线和餐饮查询的 D 模块边界与可测试交付顺序。
- 让外部服务不可用、重复事件、重复调度、订单失效和邮件结果不明时都有确定处理。
- 以版本化 Demo Provider 支撑离线演示，不把 Demo 数据显示成实时事实。

**Non-Goals:**

- 不实现 B 的 Agent 运行、SSE、确认门或上下文恢复。
- 不实现订单、支付、退票和 C 的邮件 Provider、地图页面。
- 不提供持续定位、多路线比较、实时路况刷新、餐饮交易、已保存地点或复杂出行画像。

## Decisions

### 1. 用事件去重与订单唯一约束共同保护任务

D 以 `eventId` 记录已处理事件，以 `travel_task.order_id` 建唯一约束并由 `ensureTask(orderId)` 统一创建和补偿。A 的支付提交后事件和五分钟对账都调用这一用例。

选择双重保护，是因为单靠事件去重不能覆盖首次消费失败后的补偿；单靠订单唯一不能保留事件处理审计。监听失败不得回滚支付，补偿也不得直接读写 D 的任务表。

### 2. 任务、建议与通知分表，任务状态不代表邮件状态

`travel_task` 保存订单关联、触发时间、任务状态和版本；`travel_advice_snapshot` 按任务版本保存不含精确位置的建议摘要；`travel_notification_log` 用 `deliveryKey` 保存 EMAIL 投递状态。任务使用 `PENDING → GENERATING → READY → NOTIFIED → COMPLETED`，订单失效可进入 `CANCELLED`；邮件失败或 `UNKNOWN` 不把已生成建议改为失败。

选择分表以使建议可读、投递可恢复，避免将邮箱或精确位置写入任务数据。三张表使用逻辑关联，不对 A 的订单或 C 的用户表建立物理外键。

### 3. 外部能力统一经 Provider 端口和版本化 Demo 回退

天气、餐饮和路线各自提供标准 DTO、输入校验、超时、限流和来源时效封套。天气与餐饮按“真实 Provider、有效缓存、版本化 Demo、明确不可用”回退；路线不缓存精确起点或路线几何，真实高德未配置时仅返回路线不可用。

不在 Controller、Agent Tool 或定时任务中直接调用外部 SDK，以便固定时钟、Demo 数据和失败场景可复现。路线不使用缓存或文字路线兜底，防止泄露位置或把过期路径当作当前路径。

### 4. 位置与地图数据只存在于单次请求

路线 Command 只接受本次的 `originType`、手动地点或设备坐标以及共享说明确认；Application Service 将其传给路线 Provider 后立即丢弃。响应中的路线几何只给 C 的页面内存渲染；快照仅保存方式、距离、耗时和预计出发时间等非敏感摘要。

这比保存常用地点或完整路线更符合 MVP 隐私范围，也让路线失败不会影响任务、提醒和电子票。

### 5. 跨模块调用只经过公开端口

`TravelTaskApplicationService` 使用 A 的事件类型和公开补偿调用，不访问票务持久层；D 用 `CurrentUserAccessor` 校验本人资源；通知只调用 C 的 `EmailDeliveryPort(recipientUserId, deliveryKey, templateCode, variables, traceId)`；B 的工具只调用 D 的只读 Application Service 并返回公共 `ToolResult<T>`。

实现前须由 A 确认 `cinemaArea` 摘要端口和事件实际发布，由 C 确认邮件端口及 Mock 查询语义，并由 A 分配迁移版本号。未确认时可实现 D 的 Domain、Demo Provider 与单元测试，但不接通跨模块适配器或迁移。

## Risks / Trade-offs

- [A 的支付事件尚未实际发布] → 将事件消费和 A 的发布适配拆为独立任务，先用夹具验证 D 的幂等规则。
- [真实天气、餐饮或高德配置未确定] → 默认 Demo Provider，响应明确 `source` 和 `degraded`，不伪造实时数据。
- [邮件 Provider 无法按键查询] → `UNKNOWN` 保留告警且不自动重发；演示 Mock 必须支持按 `deliveryKey` 查询。
- [多实例定时任务重复执行] → 任务版本条件更新、通知唯一键与数据库约束共同防重，并做并发测试。
- [位置泄露] → 路线数据不写持久化、缓存或日志，并在成功、失败和超时测试中扫描敏感字段。

## Migration Plan

1. A 先分配新的 Flyway 版本并确认三张 D 表的字段、唯一键、索引、保留期和兼容方案。
2. 在独立 `cinewise_migration_check` MySQL 8 库执行新迁移；已发布迁移不修改。
3. 部署后先启用 Demo Provider 和 Mock 邮件 Provider，验证支付事件、任务创建、建议和恢复；真实 Provider 只在密钥、配额和域名白名单就绪后启用。
4. 回退应用版本时停止新的调度与真实 Provider 调用，保留任务和通知日志只读；后续结构调整必须以新的前向迁移处理。

## Open Questions

- A 尚未确认 `ContentSummaryQueryPort` 中 `cinemaArea` 的最终方法名和支付事件发布适配接通时间；该问题不改变字段和职责，但会决定跨模块联调任务的开始时间。
- C 的真实邮件 Provider 是否支持以 `deliveryKey` 查询；不支持时按既定 `UNKNOWN` 不自动重发规则运行。
