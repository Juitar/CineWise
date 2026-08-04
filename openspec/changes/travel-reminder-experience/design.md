## Context

见 [proposal.md](proposal.md)。当前 `travel` 只有包骨架；A 已通过 D 的 `ContentSummaryQueryPort.CinemaSummary.area()` 接通 `PaymentSucceededEvent` 的事务后发布。C 已确认 `EmailDeliveryPort` 的契约，但端口、Mock Provider 和查询恢复实现尚未交付。出行详细设计已冻结事件字段、任务状态、通知恢复和位置隐私规则。

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

D 以 `eventId` 记录已处理事件，以 `travel_task.order_id` 建唯一约束并由 `ensureTask(PaymentSucceededEvent)` 统一创建和补偿。A 每五分钟分别扫描最近 24 小时的 `PAID`、`REFUNDED` 订单，每批最多 100 条、逐条调用且单条失败不回滚整批；A 只对仍为 `PAID` 的订单调用前者，退款完成后由 `ensureTaskCancelled(OrderInvalidated)` 按版本取消任务，不能再用 PAID 扫描补偿。

选择双重保护，是因为单靠事件去重不能覆盖首次消费失败后的补偿；单靠订单唯一不能保留事件处理审计。监听失败不得回滚支付，补偿也不得直接读写 D 的任务表。A 在交易事务内登记事件，D 只在 `AFTER_COMMIT` 消费；`OrderInvalidated` 使用支付事件全部字段并增加固定值 `invalidReason=REFUNDED`，其 `orderVersion` 取退款完成后的版本。退款事件先到时，D 必须写入 `CANCELLED` 墓碑任务或保留等价的最高订单版本；`ensureTask` 对 `CANCELLED`、`COMPLETED` 只返回原任务摘要，绝不重新打开任务。

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

`TravelTaskApplicationService` 提供 `ensureTask(PaymentSucceededEvent)` 与 `ensureTaskCancelled(OrderInvalidated)` 供 A 对账调用，并返回仅含 `taskId`、`status`、`orderVersion` 的最小任务摘要；实现不访问票务持久层。D 用 `CurrentUserAccessor` 校验本人资源；通知只调用 C 的 `EmailDeliveryPort(recipientUserId, deliveryKey, templateCode, variables, traceId)`；B 的工具只调用 D 的只读 Application Service 并返回公共 `ToolResult<T>`。

已确认：A 已接通 `cinemaArea` 摘要和支付事件登记方式；C 已确认邮件端口及 Mock 查询语义；B、C 已确认只读工具和卡片边界。仍需 A 复核退款失效补偿入口，并在 D 提供完整迁移申请后分配迁移版本。C 的邮件端口代码交付前，D 不接通邮件适配器或邮件联调。

### 6. 迁移申请的表契约

本节是 D 提交给 A 的迁移申请内容，不是 Flyway 执行授权。三张表均使用 `BIGINT` 雪花主键、`DATETIME(3)` 时间字段、`utf8mb4_0900_ai_ci`，不建立到订单、场次或用户表的物理外键；具体 Flyway 版本由 A 在全局复核后分配。

| 表 | 字段、可空性与默认值 | 约束、索引与清理 |
| --- | --- | --- |
| `travel_task` | `id BIGINT NOT NULL`；`task_id VARCHAR(64) NOT NULL`，对外字符串任务号；`payment_event_id/invalidation_event_id VARCHAR(64) NULL`；`user_id/order_id/show_id BIGINT NOT NULL`；`cinema_area VARCHAR(128) NOT NULL`；`start_at/trigger_at DATETIME(3) NOT NULL`；`order_version/version BIGINT NOT NULL DEFAULT 0`；`status VARCHAR(16) NOT NULL DEFAULT 'PENDING'`；`retry_count INT NOT NULL DEFAULT 0`；`closed_at DATETIME(3) NULL`；`created_at/updated_at DATETIME(3) NOT NULL` | PK(`id`)；UNIQUE(`task_id`)；UNIQUE(`order_id`)；UNIQUE(`payment_event_id`)；UNIQUE(`invalidation_event_id`)；INDEX(`status`,`trigger_at`)；INDEX(`closed_at`)；CHECK(`order_version >= 0`)；CHECK(`version >= 0`)；CHECK(`retry_count >= 0`)；`status` 仅允许 `PENDING/GENERATING/READY/NOTIFIED/COMPLETED/CANCELLED/FAILED`；CHECK：`COMPLETED`、`CANCELLED`、`FAILED` 必须有 `closed_at`，其余状态必须为 `closed_at IS NULL`。支付事件创建的任务写 `payment_event_id`；退款事件先到的墓碑只写 `invalidation_event_id`，随后支付事件不得覆盖终态。`id` 仅供库内关联，`task_id` 仅供 REST、页面和邮件相对路径。任务从 `closed_at` 起保留 30 天后硬删除。没有 `deleted_at`，业务取消是状态变化，不使用软删除。 |
| `travel_advice_snapshot` | `id BIGINT NOT NULL`；`travel_task_id BIGINT NOT NULL`，指向 `travel_task.id`；`task_version BIGINT NOT NULL`；`weather_json/route_json/food_json JSON NULL`；`advice_json JSON NOT NULL`；`source VARCHAR(32) NOT NULL`；`data_time/expires_at DATETIME(3) NOT NULL`；`is_expired/degraded TINYINT NOT NULL DEFAULT 0`；`fallback_type VARCHAR(32) NULL`；`created_at DATETIME(3) NOT NULL` | PK(`id`)；UNIQUE(`travel_task_id`,`task_version`)；INDEX(`expires_at`)；CHECK(`is_expired IN (0,1)`)；CHECK(`degraded IN (0,1)`)；无物理外键。`route_json` 不得存精确起点、路线折线或途经点。随所属任务的 `closed_at` 起算保留 30 天，在删除任务前先硬删除快照；任务未关闭时，过期快照只读但不提前清理。 |
| `travel_notification_log` | `id BIGINT NOT NULL`；`travel_task_id BIGINT NOT NULL`，指向 `travel_task.id`；`trigger_type VARCHAR(32) NOT NULL`；`task_version BIGINT NOT NULL`；`channel VARCHAR(16) NOT NULL DEFAULT 'EMAIL'`；`template_code VARCHAR(32) NOT NULL DEFAULT 'VIEWING_REMINDER'`；`delivery_key VARCHAR(160) NOT NULL`；`status VARCHAR(16) NOT NULL DEFAULT 'PENDING'`；`attempt_count INT NOT NULL DEFAULT 0`；`next_retry_at/lease_until/sent_at/resolved_at DATETIME(3) NULL`；`provider_message_id VARCHAR(128) NULL`；`error_code INT NULL`；`scheduled_at/created_at/updated_at DATETIME(3) NOT NULL` | PK(`id`)；UNIQUE(`delivery_key`)；INDEX(`status`,`next_retry_at`)；INDEX(`travel_task_id`,`created_at`)；INDEX(`resolved_at`)；CHECK(`task_version >= 0`)；CHECK(`attempt_count >= 0`)；`status` 仅允许 `PENDING/SENDING/SENT/FAILED/UNKNOWN`，`channel` 仅允许 `EMAIL`，`template_code` 仅允许 `VIEWING_REMINDER`；CHECK：`SENT`、`FAILED` 必须有 `resolved_at`，`PENDING`、`SENDING`、`UNKNOWN` 必须为 `resolved_at IS NULL`。从 `resolved_at` 起保留 90 天后硬删除；`UNKNOWN` 未经查询恢复不得清理或重发。任务在 30 天后硬删除时，尚在 90 天保留期内的通知日志允许成为逻辑孤儿，只能按 `delivery_key` 或内部 `travel_task_id` 审计查询，不参与用户任务查询或任务关联写入。 |

其中 `travel_task.task_id` 与子表的 `travel_task_id` 不是同一字段：前者是对外字符串，后者是内部 `BIGINT` 逻辑关联，避免关联语义和字段类型混淆。三表不使用软删除；任务与快照按 30 天硬删除，通知日志按 90 天硬删除，保留期差异产生的逻辑孤儿是受控审计数据而不是可恢复任务。

## Risks / Trade-offs

- [A 的支付事件尚未实际发布] → 将事件消费和 A 的发布适配拆为独立任务，先用夹具验证 D 的幂等规则。
- [真实天气、餐饮或高德配置未确定] → 默认 Demo Provider，响应明确 `source` 和 `degraded`，不伪造实时数据。
- [邮件 Provider 无法按键查询] → `UNKNOWN` 保留告警且不自动重发；演示 Mock 必须支持按 `deliveryKey` 查询。
- [多实例定时任务重复执行] → 任务版本条件更新、通知唯一键与数据库约束共同防重，并做并发测试。
- [位置泄露] → 路线数据不写持久化、缓存或日志，并在成功、失败和超时测试中扫描敏感字段。

## Migration Plan

1. D 向 A 提交本设计第 6 节的完整迁移申请；A 完成全局版本复核后分配正式 Flyway 版本，并确认三张 D 表的字段、唯一键、索引、保留期和兼容方案。
2. 在独立 `cinewise_migration_check` MySQL 8 库执行新迁移；已发布迁移不修改。
3. 部署后先启用 Demo Provider 和 Mock 邮件 Provider，验证支付事件、任务创建、建议和恢复；真实 Provider 只在密钥、配额和域名白名单就绪后启用。
4. 回退应用版本时停止新的调度与真实 Provider 调用，保留任务和通知日志只读；后续结构调整必须以新的前向迁移处理。

## Open Questions

- A 已确认 `ensureTask(PaymentSucceededEvent)` 与 `ensureTaskCancelled(OrderInvalidated)` 的调用方向；本 change 已补齐乱序与终态保护规则，待 A 从实际 change 再次复核。
- A 尚未分配正式 Flyway 版本；当前 `V007` 仅为候选，不能用于迁移文件名或执行。
- C 的真实邮件 Provider 是否支持以 `deliveryKey` 查询；不支持时按既定 `UNKNOWN` 不自动重发规则运行。
