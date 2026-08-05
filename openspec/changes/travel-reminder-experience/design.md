## Context

见 [proposal.md](proposal.md)。A 已通过 D 的 `ContentSummaryQueryPort.CinemaSummary.area()` 接通支付事件的事务后发布，并确认在 `PaymentSucceededEvent`、`OrderInvalidated` 同步增加 `String cinemaId`。V007 已发布，A 已正式分配 V013 在 `travel_task` 增加逻辑关联的 `cinema_id`。C 已确认浏览器一次性定位和本站静态图片流展示规则；邮件端口、Mock Provider 和查询恢复实现尚未交付。

## Goals / Non-Goals

### MVP 范围调整（后文路线和餐饮描述以本节为准）

本次 MVP 保留影院区域天气、天气相关出发建议、支付后任务和邮件提醒，并增加用户主动发起的步行、骑行和公交路线查询。用户可首次跳过、后续再请求；每次请求均由 C 的页面展示位置共享说明并取得浏览器本次定位。定位成功后页面只展示三张方式卡，不自动请求路线或地图；用户选择其中一种后才查看该路线摘要、静态地图和分段指引。周边餐饮、驾车路线、持续定位、常用地点、路线比较、实时刷新及 Agent 对话收集位置不交付。

**Goals:**

- 建立支付后提醒、天气建议、步行/骑行/公交路线和静态地图的 D 模块边界与可测试交付顺序。
- 让外部服务不可用、重复事件、重复调度、订单失效和邮件结果不明时都有确定处理。
- 以版本化 Demo Provider 支撑离线演示，不把 Demo 数据显示成实时事实。

**Non-Goals:**

- 不实现 B 的 Agent 运行、SSE、确认门或上下文恢复。
- 不实现订单、支付、退票和 C 的邮件 Provider、地图页面。
- 不提供持续定位、多路线优劣比较、实时路线刷新、餐饮交易、已保存地点或复杂出行画像。

## Decisions

### 1. 用事件去重与订单唯一约束共同保护任务

D 以 `eventId` 记录已处理事件，以 `travel_task.order_id` 建唯一约束并由 `ensureTask(PaymentSucceededEvent)` 统一创建和补偿。A 每五分钟分别扫描最近 24 小时的 `PAID`、`REFUNDED` 订单，每批最多 100 条、逐条调用且单条失败不回滚整批；A 只对仍为 `PAID` 的订单调用前者，退款完成后由 `ensureTaskCancelled(OrderInvalidated)` 按版本取消任务，不能再用 PAID 扫描补偿。两个事件使用相同的 `String cinemaId` 字段；支付创建任务时只接受可解析为正 `BIGINT` 的影院业务 ID，退款先到的取消墓碑则允许非法值对应的 `cinema_id=NULL`。

选择双重保护，是因为单靠事件去重不能覆盖首次消费失败后的补偿；单靠订单唯一不能保留事件处理审计。监听失败不得回滚支付，补偿也不得直接读写 D 的任务表。A 在交易事务内登记事件，D 只在 `AFTER_COMMIT` 消费；`OrderInvalidated` 使用支付事件全部字段并增加固定值 `invalidReason=REFUNDED`，其 `orderVersion` 取退款完成后的版本。退款事件先到时，D 必须创建或保留 `CANCELLED` 墓碑任务和等价的最高订单版本；即使 `cinemaId` 缺失或非法，也要以 `cinema_id=NULL` 写入墓碑，防止迟到支付事件重开任务。支付事件的 `cinemaId` 缺失或非法时，D 不创建任务，只输出包含 `eventId` 的受控错误；A 的下一次 PAID 补偿必须携带修正后的合法值，仍须遵守既有墓碑不得重开规则。支付或退款事务本身不回滚。

| A→D 调用 | `cinemaId` 契约 | D 的处理 |
| --- | --- | --- |
| `PaymentSucceededEvent` / `ensureTask` | `String`，十进制正整数字符串，解析后属于 `1` 至 `Long.MAX_VALUE`；不携带坐标、位置或路线数据 | 创建任务时写入解析后的 `cinema_id`；缺失、空白、非数字、溢出、`0` 或负数时跳过创建并仅输出 `eventId` 错误。 |
| `OrderInvalidated` / `ensureTaskCancelled` | 与支付事件字段类型一致；正数可写入影院 ID，缺失、空白、非数字、溢出、`0` 或负数视为无可用终点 | 已有任务仅取消并保留原 `cinema_id`；退款先到时，合法值写入新建 `CANCELLED` 墓碑，非法值仍创建/保留墓碑并写入 `cinema_id=NULL`。 |

该校验同时位于 D 的应用层和 V013 的数据库 CHECK：应用层要求新支付任务写入正数，并在退款先到的非法值场景保留 `NULL` 墓碑；数据库防止任何调用者写入 `0` 或负数。历史任务和非法退款墓碑的 `NULL` 都只用于兼容/防重，路线查询不得伪造终点。

### 2. 任务、建议与通知分表，任务状态不代表邮件状态

`travel_task` 保存订单关联、触发时间、任务状态和版本；`travel_advice_snapshot` 按任务版本保存不含精确位置的建议摘要；`travel_notification_log` 用 `deliveryKey` 保存 EMAIL 投递状态。任务使用 `PENDING → GENERATING → READY → NOTIFIED → COMPLETED`，订单失效可进入 `CANCELLED`；邮件失败或 `UNKNOWN` 不把已生成建议改为失败。

选择分表以使建议可读、投递可恢复，避免将邮箱或精确位置写入任务数据。三张表使用逻辑关联，不对 A 的订单或 C 的用户表建立物理外键。

### 3. 外部能力统一经 Provider 端口和版本化 Demo 回退

天气和路线各自提供标准 DTO、输入校验、超时、限流和来源时效封套。天气按“真实 Provider、有效缓存、版本化 Demo、明确不可用”回退；路线不缓存精确起点或路线几何，真实高德未配置时仅返回路线不可用。

不在 Controller、Agent Tool 或定时任务中直接调用外部 SDK，以便固定时钟、Demo 数据和失败场景可复现。路线不使用缓存或文字路线兜底，防止泄露位置或把过期路径当作当前路径。

真实 Provider 接入复用 `WeatherProvider`、`BasicRouteProvider` 和静态地图端口，不新增 Controller 直连高德。每个 Provider 启用前必须记录服务商、授权方式、Key 的配置名、配额、连接/读取超时、允许域名和停用开关；Key 只由部署环境注入，不进仓库、日志、异常、快照或测试夹具。高德路线和静态地图使用同一 Web 服务 Key；调用失败时天气按“有效缓存 → Demo → 明确不可用”回退，路线和地图只返回不可用，不缓存或伪造文字路线。

### 4. 位置与地图数据只存在于单次请求

路线 Command 只接受本次浏览器设备坐标、所选 `travelMode`、任务版本及位置共享确认；本 MVP 不接收手动地点，以避免新增地理编码和地址留存范围。定位成功后，页面不调用高德；只有用户选择某一种方式时，Application Service 才校验任务版本并将坐标传给对应路线 Provider，调用结束后立即丢弃。响应中的路线几何不返回 C；后端仅用其在本次调用内生成静态地图图片流并直接转给页面。快照不保存路线结果。

每次页面请求都重新取得浏览器定位，且不生成高德图片 URL 给前端，避免暴露 Web 服务 Key 或让带精确位置的 URL 留在浏览器历史、日志和第三方资源记录中。这让路线失败不会影响任务、提醒和电子票。

### 5. 跨模块调用只经过公开端口

`TravelTaskApplicationService` 提供 `ensureTask(PaymentSucceededEvent)` 与 `ensureTaskCancelled(OrderInvalidated)` 供 A 对账调用，并返回仅含 `taskId`、`status`、`orderVersion` 的最小任务摘要；实现不访问票务持久层。A 已确认两个事件均携带静态 `cinemaId`，D 在 V013 完成后写入 `travel_task.cinema_id`，并通过内容模块公开的 `CinemaRouteDestinationQueryPort` 查询影院坐标，绝不由路线模块读取内容 Entity、Mapper 或表。历史任务允许 `cinema_id=NULL`，但路线查询必须返回明确不可用。D 用 `CurrentUserAccessor` 校验本人资源；通知只调用 C 的 `EmailDeliveryPort(recipientUserId, deliveryKey, templateCode, variables, traceId)`；B 的工具只调用 D 的只读 Application Service 并返回公共 `ToolResult<T>`，本 MVP 不注册路线工具。

已确认：A 已接通 `cinemaArea` 摘要和支付事件登记方式，并正式分配 V007；C 已确认邮件端口及 Mock 查询语义；B、C 已确认只读工具和卡片边界。D 负责提交本节字段、约束和生命周期申请供 A 静态复核；最终 V007 前向迁移 SQL 由 A 创建、决定是否授权验证并以隔离迁移提交进入 `dev`。C 的邮件端口代码交付前，D 不接通邮件适配器或邮件联调。

### 6. 路线接口、静态地图和失败处理

页面在定位成功后只显示步行、骑行和公交三张方式卡。用户选择后，以 `taskId`、本次 `longitude`、`latitude`、`travelMode`、`taskVersion` 与位置共享确认调用本站接口；D 先校验任务归属、未取消状态和任务版本，再以影院静态坐标作为终点。高德步行、骑行和公交路线分别调用 `/v3/direction/walking`、`/v4/direction/bicycling`、`/v3/direction/transit/integrated`；公交请求的 `city` 使用影院 `cityCode`，并使用场次日期时间筛选方案。接口仅计算用户选择的方式，返回其距离、预计时长、换乘摘要和有限条文字指引；页面再以同一请求数据取得该方式静态地图图片流。不同方式互不比较，某一方式失败不影响用户选择其他方式重试。

用户选择方式后才调用静态地图接口。静态地图使用 `/v3/staticmap` 的起终点标记和所选路线折线，后端返回图片字节流而不返回高德 URL。距离测量接口不参与三种路线距离计算：其不提供骑行和公交路线距离，路线接口自身已返回距离；仅在未来明确展示直线距离时单独增加。

路线结果标记 `source=AMAP`、`dataTime`、`expiresAt`、`isExpired`、`degraded` 和 `fallbackType`；`expiresAt` 为 `dataTime` 后五分钟。服务端限制每位用户每分钟最多三次路线选项请求；Provider 超时、无 Key、无影院坐标、无可达方案或静态地图失败时返回稳定错误码和不含坐标的提示。路线请求不得自动重试，以免重复传输本次位置。

### 7. 迁移申请的表契约

本节是 D 提交给 A 的迁移申请内容，不是 Flyway 执行授权。三张表均使用 `BIGINT` 雪花主键、`DATETIME(3)` 时间字段、`utf8mb4_0900_ai_ci`，不建立到订单、场次或用户表的物理外键；具体 Flyway 版本由 A 在全局复核后分配。

| 表 | 字段、可空性与默认值 | 约束、索引与清理 |
| --- | --- | --- |
| `travel_task` | `id BIGINT NOT NULL`；`task_id VARCHAR(64) NOT NULL`，对外字符串任务号；`payment_event_id/invalidation_event_id VARCHAR(64) NULL`；`user_id/order_id/show_id BIGINT NOT NULL`；`cinema_area VARCHAR(128) NOT NULL`；`start_at/trigger_at DATETIME(3) NOT NULL`；`order_version/version BIGINT NOT NULL DEFAULT 0`；`status VARCHAR(16) NOT NULL DEFAULT 'PENDING'`；`retry_count INT NOT NULL DEFAULT 0`；`closed_at DATETIME(3) NULL`；`create_time/update_time DATETIME(3) NOT NULL` | PK(`id`)；UNIQUE(`task_id`)；UNIQUE(`order_id`)；UNIQUE(`payment_event_id`)；UNIQUE(`invalidation_event_id`)；INDEX(`status`,`trigger_at`)；INDEX(`closed_at`)；CHECK(`order_version >= 0`)；CHECK(`version >= 0`)；CHECK(`retry_count >= 0`)；`status` 仅允许 `PENDING/GENERATING/READY/NOTIFIED/COMPLETED/CANCELLED/FAILED`；CHECK：`COMPLETED`、`CANCELLED`、`FAILED` 必须有 `closed_at`，其余状态必须为 `closed_at IS NULL`。支付事件创建的任务写 `payment_event_id`；退款事件先到的墓碑只写 `invalidation_event_id`，随后支付事件不得覆盖终态。`id` 仅供库内关联，`task_id` 仅供 REST、页面和邮件相对路径。任务从 `closed_at` 起保留 30 天后硬删除。没有 `deleted_at`，业务取消是状态变化，不使用软删除。 |
| `travel_advice_snapshot` | `id BIGINT NOT NULL`；`travel_task_id BIGINT NOT NULL`，指向 `travel_task.id`；`task_version BIGINT NOT NULL`；`weather_json/route_json/food_json JSON NULL`；`advice_json JSON NOT NULL`；`source VARCHAR(32) NOT NULL`；`data_time/expires_at DATETIME(3) NOT NULL`；`is_expired/degraded TINYINT NOT NULL DEFAULT 0`；`fallback_type VARCHAR(32) NULL`；`create_time DATETIME(3) NOT NULL` | PK(`id`)；UNIQUE(`travel_task_id`,`task_version`)；INDEX(`expires_at`)；CHECK(`task_version >= 0`)；CHECK(`is_expired IN (0,1)`)；CHECK(`degraded IN (0,1)`)；无物理外键。快照为追加式不可变记录：刷新先以任务 `version` 条件更新取得新版本，再插入一条新快照，绝不更新旧快照。任务取消时不改写旧快照；查询层因任务终态返回只读且 `isExpired=true`。`route_json` 不得存精确起点、路线折线或途经点。随所属任务的 `closed_at` 起算保留 30 天，在删除任务前先硬删除快照；任务未关闭时，过期快照只读但不提前清理。 |
| `travel_notification_log` | `id BIGINT NOT NULL`；`travel_task_id BIGINT NOT NULL`，指向 `travel_task.id`；`trigger_type VARCHAR(32) NOT NULL`；`task_version BIGINT NOT NULL`；`channel VARCHAR(16) NOT NULL DEFAULT 'EMAIL'`；`template_code VARCHAR(32) NOT NULL DEFAULT 'VIEWING_REMINDER'`；`delivery_key VARCHAR(160) NOT NULL`；`status VARCHAR(16) NOT NULL DEFAULT 'PENDING'`；`attempt_count INT NOT NULL DEFAULT 0`；`next_retry_at/lease_until/sent_at/resolved_at DATETIME(3) NULL`；`provider_message_id VARCHAR(128) NULL`；`error_code INT NULL`；`scheduled_at/create_time/update_time DATETIME(3) NOT NULL` | PK(`id`)；UNIQUE(`delivery_key`)；INDEX(`status`,`next_retry_at`)；INDEX(`travel_task_id`,`create_time`)；INDEX(`resolved_at`)；CHECK(`task_version >= 0`)；CHECK(`attempt_count >= 0`)；`status` 仅允许 `PENDING/SENDING/SENT/FAILED/UNKNOWN`，`channel` 仅允许 `EMAIL`，`template_code` 仅允许 `VIEWING_REMINDER`；CHECK：`SENT`、`FAILED` 必须有 `resolved_at`，`PENDING`、`SENDING`、`UNKNOWN` 必须为 `resolved_at IS NULL`。从 `resolved_at` 起保留 90 天后硬删除；`UNKNOWN` 未经查询恢复不得清理或重发。任务在 30 天后硬删除时，尚在 90 天保留期内的通知日志允许成为逻辑孤儿，只能按 `delivery_key` 或内部 `travel_task_id` 审计查询，不参与用户任务查询或任务关联写入。 |

其中 `travel_task.task_id` 与子表的 `travel_task_id` 不是同一字段：前者是对外字符串，后者是内部 `BIGINT` 逻辑关联，避免关联语义和字段类型混淆。三表不使用软删除；任务与快照按 30 天硬删除，通知日志按 90 天硬删除，保留期差异产生的逻辑孤儿是受控审计数据而不是可恢复任务。

V013 在上述已发布 `travel_task` 结构上追加 `cinema_id BIGINT NULL`，字段位于 `show_id` 后。它仅保存 A 的稳定影院业务 ID，不建立物理外键、索引、默认值或回填；`chk_travel_task_cinema_id_positive` 只允许 `NULL` 或正数。新支付任务必须由应用层写入正数；退款先到的 `CANCELLED` 墓碑在影院 ID 非法时允许写入 `NULL`。既有历史任务和该类墓碑的 `NULL` 均使后续路线查询直接返回不可用。

## Risks / Trade-offs

- [A 的支付事件尚未实际发布] → 将事件消费和 A 的发布适配拆为独立任务，先用夹具验证 D 的幂等规则。
- [真实天气或高德配置未确定] → 默认 Demo 天气；路线和静态地图明确不可用，不伪造实时数据。
- [路线终点缺少影院标识] → A、D 确认 `cinemaId` 的事件字段和迁移方案后才启用真实路线；在此之前不拿 `cinemaArea` 伪造终点。
- [真实 Provider 需要授权或配额] → D 先补 Adapter、配置校验、超时和降级测试；只有服务商、Key、配额、域名白名单经 Owner 确认后才开启真实调用。
- [邮件 Provider 无法按键查询] → `UNKNOWN` 保留告警且不自动重发；演示 Mock 必须支持按 `deliveryKey` 查询。
- [多实例定时任务重复执行] → 任务版本条件更新、通知唯一键与数据库约束共同防重，并做并发测试。
- [位置泄露] → 路线数据不写持久化、缓存或日志，并在成功、失败和超时测试中扫描敏感字段。

## Migration Plan

1. V007 已发布且冻结。D 向 A 提交 V013 草案：在 `travel_task` 增加 `cinema_id BIGINT NULL` 和 `chk_travel_task_cinema_id_positive` CHECK（仅允许 `NULL` 或正数）；不建物理外键、不设置默认值、不回填历史数据，也不得修改 V001～V012 或自行执行 Flyway。
2. A 静态复核通过并明确授权后，在独立 `cinewise_migration_check` MySQL 8 库创建、执行并验证最终 V013 迁移；已发布迁移不修改。
3. V013 发布后，A 与 D 同步更新两个事件和消费者；新支付任务的 `cinema_id` 必须非空，历史任务和影院 ID 非法的退款先到墓碑允许 `NULL` 且路线不可用。
4. 部署后先启用 Demo Provider 和 Mock 邮件 Provider，验证支付事件、任务创建、建议和恢复；真实 Provider 只在密钥、配额和域名白名单就绪后启用。
5. 回退应用版本时停止新的调度与真实 Provider 调用，保留任务和通知日志只读；后续结构调整必须以新的前向迁移处理。

## Open Questions

- A 已确认 `ensureTask(PaymentSucceededEvent)` 与 `ensureTaskCancelled(OrderInvalidated)` 的调用方向；本 change 已补齐乱序与终态保护规则。
- A 已于 2026-08-04 正式分配 `V007`，专用于新增 `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 三张表。版本分配不等于 SQL 创建、验证库迁移或共享库发布授权；最终 V007 前向迁移由 A 在静态复核和授权后以隔离提交处理。
- C 的真实邮件 Provider 是否支持以 `deliveryKey` 查询；不支持时按既定 `UNKNOWN` 不自动重发规则运行。
- 高德天气、路线和静态地图的授权主体、Key、配额、超时、启用开关和生产域名白名单待 D、C 确认；在确认前不得把 Demo 或不可用结果标记为真实数据。
