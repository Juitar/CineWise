## Context

`profile` 当前只有包骨架。设计依据为《妙语购票_用户画像设计》第 11 章和《妙语购票_数据设计与Agent基础设计》第 11.2 节。用户画像是 D 的独立模块，不并入内容、推荐或出行 change。

## Goals / Non-Goals

**Goals:**

- 让用户管理可解释的长期偏好，并随时关闭个性化。
- 在不暴露原始行为和对话的前提下，向 B、推荐模块提供最小化摘要。
- 对写入、重放、并发和缓存失效给出可验证规则。

**Non-Goals:**

- 不由 D 实现身份认证、Agent 运行或前端页面。
- 不让行为采集失败影响订单、支付、电子票或推荐主流程。
- 不把用户当前一次性要求写成长期偏好，除非 B 已取得明确保存确认。

## Decisions

### 1. 数据分为开关、标签和原始行为摘要

`user_preference` 每用户一行，保存 `personalization_enabled` 和乐观锁版本；`user_profile_tag` 保存可解释的长期偏好；`user_behavior_event` 保存最小化、可去重的行为证据。用户 ID 仅复用 C 从认证上下文提供的 `sys_user.id`，D 不生成也不接收请求体中的 userId。

标签来源为 `MANUAL`、`CONVERSATION`、`BEHAVIOR`。冲突时采用最新 `MANUAL`、最新 `CONVERSATION`、聚合 `BEHAVIOR` 的顺序；相同来源取较新的 `updatedAt`。显式标签权重范围为 0.10 到 1.00，不自动衰减；行为标签最高 0.80，30 天乘以 0.85，90 天无新事件转为 `EXPIRED`。

本 change 统一采用 `MOVIE_GENRE`、`TIME`、`CINEMA`、`HALL`、`PRICE`、`SEAT` 六种 `tag_type`；旧文档和夹具中的 `GENRE` 一律视为 `MOVIE_GENRE`，不得两者并存。`polarity` 只允许 `LIKE`、`DISLIKE`；`source` 只允许 `MANUAL`、`CONVERSATION`、`BEHAVIOR`，旧 `DIALOG` 映射为 `CONVERSATION`，旧 `ORDER` 不再作为标签来源，支付行为产生的标签统一为 `BEHAVIOR`。`status` 只允许 `ACTIVE`、`DISABLED`、`EXPIRED`、`DELETED`；只有 `DELETED` 必须有 `deleted_at`，其他状态必须没有 `deleted_at`。

首次读取或首次写入某个用户时，服务以事务方式创建默认 `personalization_enabled=true`、`version=0` 的设置；并发首次访问只能保留一条 `user_preference`。标签管理查询按页返回未软删除的 `ACTIVE`、`DISABLED`、`EXPIRED` 标签及总数，默认不返回 `DELETED`；用户可将单个标签从 `ACTIVE` 置为 `DISABLED`，或恢复为 `ACTIVE`，两种状态均不改变标签来源。

### 2. 摘要先检查开关，再读取有效标签

`ProfileSummary` 只包含 `enabled`、`version`、`generatedAt` 和有效标签的类型、值、极性、权重、置信度、来源及更新时间。开关关闭时必须只返回 `enabled=false`，不得查询或返回任何长期标签。摘要不含原始行为、完整对话、邮箱、精确位置或内部持久化 ID。

Redis 缓存键为 `profile:{userId}:v:{version}`。标签、开关、软删除和过期任务成功写入后删除该用户所有 `profile:{userId}:v:*` 键；Redis 异常视为缓存未命中，必须读取 MySQL，不得阻断用户操作。

### 3. 本人写入使用版本和幂等键

新增、更新、删除标签和更新开关均从 `CurrentUserAccessor` 取得用户。写请求必须携带 `If-Match: <version>` 和 `Idempotency-Key`；版本不符返回 HTTP 409 / `202002`，并携带最新资源。相同幂等键重放返回首次的 200 或 201 响应，不重复修改版本、标签或缓存。

标签删除使用软删除并立即从摘要排除。标签不存在或不属于当前用户统一返回 HTTP 404 / `202003`，不暴露其归属。创建同一用户、同一标签类型、值和来源的有效标签违反唯一约束时返回 HTTP 409 / `202001`。

为兑现“相同 `Idempotency-Key` 返回首次 200/201 响应”的接口要求，新增 `profile_write_request` 记录：包含内部 ID、`user_id`、操作类型、幂等键、请求摘要哈希、固定为 `COMPLETED` 的结果状态、首次 HTTP 状态、首次公开响应 JSON、完成时间、创建和过期时间，唯一键为 `(user_id, operation, idempotency_key)`。同键但摘要哈希不同返回 HTTP 409 / `202004 PROFILE_IDEMPOTENCY_KEY_CONFLICT`，不能将不同请求误认为重放。该表保留 30 天并定向清理；它不作为通用审计表，不保存完整请求体、Cookie、JWT、原始行为或其他接口未公开的数据。

同一 MySQL 本地事务必须同时完成幂等记录插入、画像写入、版本递增、缓存失效和原始公开响应记录；事务成功后结果即为 `COMPLETED`。同键并发由唯一键和事务串行化处理：已提交的同键同摘要请求读取并返回原 200/201 结果；事务失败则整体回滚，不留下可见的处理中记录。当前不持久化 `PROCESSING`，不新增 `202005`；若未来出现真正异步或跨事务写入，必须另建 change 定义租约、CAS 接管、崩溃恢复和客户端重试规则。公开响应 JSON 只保存首次接口实际返回的 `PreferenceResponse`、`TagResponse` 或删除结果字段，以支持恢复；不得保存原始请求、行为 payload、认证信息或额外隐私字段。

用户可直接修改 `MANUAL` 标签的极性、权重和状态，不得把来源改写为其他来源；对 `CONVERSATION`、`BEHAVIOR` 标签，用户可停用或删除，但其来源、置信度和行为计算结果只能由受控服务更新。标签重新启用时仍需满足未过期条件。

### 4. 行为事件只记录最小信息并独立于主流程

内部入口 `POST /internal/profile/events` 只接受已由调用方鉴权后的 `eventId`、事件类型、目标类型、目标 ID、可选会话 ID 和发生时间。`eventId` 是全局幂等键；同一用户与同一目标的同类事件 24 小时最多计一次。允许的事件为 `CLICK`、`FAVORITE`、`ACCEPT_PLAN`、`REJECT_PLAN`、`PAID_ORDER`、`NOT_INTERESTED`。

事件仅在累计绝对权重达到 0.30 时创建或更新 `BEHAVIOR` 标签；单次行为和无效事件不会形成强偏好。`BEHAVIOR` 标签权重不得超过 0.800，且必须有 `expires_at`；`MANUAL` 标签权重必须在 0.100 到 1.000 之间。应用服务在写入前校验这两条来源条件，V010 同时以 CHECK 防止绕过应用层的直接写入。调用失败只记录可脱敏诊断，不回滚或阻塞上游业务。

内部行为入口不是公网接口。A 的 `PAID_ORDER`、B 的已确认方案选择及 C 的已鉴权用户动作只能通过类型化 Application API 或已确认的内部事件调用，调用方必须提供经过自身权限校验的用户与目标事实；D 不查询 A/B/C 的 Entity、Mapper、Repository 或 Controller 来补齐数据。`CLICK`、`FAVORITE`、`NOT_INTERESTED` 仅作用于 `MOVIE`，`ACCEPT_PLAN`、`REJECT_PLAN` 仅作用于 `PLAN`，`PAID_ORDER` 仅作用于 `SHOW`。其中 `PLAN` 的 `target_id` 必须是 B 提供的稳定 `planId`，不能是模型文本或临时槽位；B 未提供前不得写入这两类事件。

`PAID_ORDER` 仅消费 A 已确认且已提交的 `PaymentSucceededEvent`，最小字段映射为 `event_id=eventId`、`user_id=userId`、`target_type=SHOW`、`target_id=showId`、`order_id=orderId`、`order_version=orderVersion`、`occurred_at=occurredAt`。D 以 `eventId` 去重并归一化写入行为事件，不持久化 `cinemaArea`、`startAt` 等出行字段，也不得仅凭 `showId` 推导影片类型、影院等标签。

24 小时归一化由 D 的应用层短事务完成，不依赖无法表达滚动 24 小时窗口的数据库唯一键：先锁定当前用户的 `user_preference` 行，再按 `(user_id, event_type, target_type, target_id)` 查询近 24 小时事件；每个新的 `eventId` 都保存最小行为摘要，窗口内已有同类目标时不再累计权重或更新行为标签。同一 `eventId` 的唯一键冲突必须读取并返回原处理结果；事件摘要、行为标签更新和去重结果在同一事务内提交，失败时整体回滚，不留下半条记录。测试必须覆盖两个不同 `eventId` 并发提交同一归一化键时只累计一次、同一 `eventId` 重放返回原结果，以及事务失败后重试不留下事件或权重残留。

### 5. B 和推荐只能通过公开摘要能力使用画像

`GetProfileSummaryTool` 只能调用 D 的 Application Service，接收不含 userId 的 `ToolContext` 和固定用途 `RECOMMENDATION`；D 在 `execute(...)` 内通过 C 提供的 `CurrentUserAccessor.requireCurrentUserId()` 取得当前认证用户。当前工具只允许在 HTTP 认证线程同步执行；后续若改为异步执行，必须先由 B、C 提供认证上下文的安全传递方式。B 负责注册只读工具并确认“长期保存”的对话意图；D 不发布 SSE、不保存 Agent 运行数据。实际确认后的 `CONVERSATION` 写工具、其注册和联调不属于当前 B 的 `agent-interaction-runtime` change，必须由 B、D、C 后续单独建跨模块 change。推荐只读取 `ProfileSummary`，并且仍以用户本轮明确要求优先。

推荐结果必须记录本次 `profileApplied` 及实际采用的标签证据，便于向用户解释“本次参考了什么”；关闭开关、没有有效标签或画像读取失败时该值为 false。推荐记录不复制原始行为和完整画像，且画像服务不可用只降级为不使用长期特征，不阻断候选筛选与排序。

### 6. 迁移申请与生命周期

本节是 A 已分配 V010 后由 D 生成 SQL 草案的依据；所有时间使用 UTC `datetime(3)`，由 Java 经注入 `Clock` 写入，SQL 不得依赖数据库服务器默认时区或 `CURRENT_TIMESTAMP` 默认值；D 自有主键使用 BIGINT 雪花 ID；跨模块只逻辑关联，不建立到 `sys_user`、订单或 Agent 表的物理外键。

| 表 | 字段、类型、可空性、默认值与约束 | 生命周期 |
| --- | --- | --- |
| `user_preference` | `user_id BIGINT NOT NULL` 主键；`personalization_enabled BOOLEAN NOT NULL DEFAULT TRUE`；`version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)`；`create_time DATETIME(3) NOT NULL`；`update_time DATETIME(3) NOT NULL`；`deleted_at DATETIME(3) NULL`。不建立到 `sys_user` 的物理外键。 | 收到账户删除通知后立即关闭，待 C 确认事件后进入 30 天清理队列。 |
| `user_profile_tag` | `id BIGINT NOT NULL` 主键；`user_id BIGINT NOT NULL`；`tag_type VARCHAR(32) NOT NULL`，限六种已确认类型；`tag_value VARCHAR(128) NOT NULL`；`polarity VARCHAR(16) NOT NULL`，限 `LIKE/DISLIKE`；`weight DECIMAL(4,3) NOT NULL CHECK (weight >= 0 AND weight <= 1)`；`source VARCHAR(32) NOT NULL`，限 `MANUAL/CONVERSATION/BEHAVIOR`，其中 `MANUAL` 为 0.100–1.000、`BEHAVIOR` 不超过 0.800 且 `expires_at` 非空；`confidence DECIMAL(4,3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1)`；`status VARCHAR(16) NOT NULL`，限四种已确认状态；`expires_at DATETIME(3) NULL`；`version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)`；`deleted_at DATETIME(3) NULL`；`active_flag TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED`；`create_time DATETIME(3) NOT NULL`；`update_time DATETIME(3) NOT NULL`。唯一键 `(user_id, tag_type, tag_value, source, active_flag)`；索引 `(user_id, status, expires_at)`、`(deleted_at)`。 | 软删除后 30 天清理；过期标签不参与摘要。 |
| `user_behavior_event` | `id BIGINT NOT NULL` 主键；`event_id VARCHAR(64) NOT NULL`；`user_id BIGINT NOT NULL`；`event_type VARCHAR(32) NOT NULL`，限六种已确认行为；`target_type VARCHAR(32) NOT NULL`，限 `MOVIE/PLAN/SHOW`；`target_id VARCHAR(64) NOT NULL`；`order_id BIGINT NULL`、`order_version BIGINT NULL` 仅用于 `PAID_ORDER` 且 `order_id > 0`；`session_id VARCHAR(64) NULL`；`payload_json JSON NULL`，只允许已确认的最小白名单字段；`occurred_at DATETIME(3) NOT NULL`；`create_time DATETIME(3) NOT NULL`。唯一键 `(event_id)`；索引 `(user_id, occurred_at)`、`(occurred_at)`。 | 原始摘要保留 90 天；聚合标签按自己的时效保留。 |
| `profile_write_request` | `id BIGINT NOT NULL` 主键；`user_id BIGINT NOT NULL`；`operation VARCHAR(32) NOT NULL`；`idempotency_key VARCHAR(128) NOT NULL`；`request_hash CHAR(64) NOT NULL`；`status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED' CHECK (status = 'COMPLETED')`；`http_status SMALLINT NOT NULL CHECK (http_status IN (200, 201))`；`response_json JSON NOT NULL`，仅保存首次公开响应；`completed_at DATETIME(3) NOT NULL`；`expires_at DATETIME(3) NOT NULL`；`create_time DATETIME(3) NOT NULL`；`update_time DATETIME(3) NOT NULL`。唯一键 `(user_id, operation, idempotency_key)`；定向清理索引 `(expires_at)`。 | 仅用于同一画像写操作的响应恢复；保留 30 天定向清理，清理后不影响标签和开关真实状态。 |

迁移必须检查权重/置信度范围、非负版本、已确认的枚举值、幂等记录状态一致性和时间合法性；A 已正式分配 V010，D 创建 `V010__create_profile_tables.sql` 草案。A 完成静态审查并明确授权后，才可在 `cinewise_migration_check` MySQL 8 环境验证；D 在此之前不得执行 Flyway、连接迁移验证库或发布共享库。全部业务时间以 UTC 写入 `DATETIME(3)`，Java 通过注入的 `Clock` 获取时间，禁止依赖数据库服务器默认时区；REST 一律输出带时区的 ISO 8601。

### 7. 账户删除和来源枚举必须先定清楚

C 的账户删除流程需要通过一个类型化 Application API 或事件通知 D；登录退出不触发清理。C 尚未实现账户删除，因此通知名称、字段、重试和 30/90 天清理触发时机仍待 C 确认。确认后，D 收到通知立即关闭个性化、删除画像缓存、将标签停止用于摘要，并按已确认隐私策略清理；D 不读取 `sys_user` 表，也不自行判断账户是否已删除。

当前后端总设计第 7 章写有 `MOVIE_GENRE/TIME/CINEMA/HALL/PRICE/SEAT` 和旧来源 `MANUAL/DIALOG/ORDER/BEHAVIOR`，画像详细设计第 11 章写有 `MANUAL/CONVERSATION/BEHAVIOR` 与 `PAID_ORDER` 事件。本 change 已由 D 确认统一映射：`DIALOG -> CONVERSATION`、`ORDER -> BEHAVIOR`、`GENRE -> MOVIE_GENRE`，并按第 1、4 节创建数据库枚举 CHECK 和 Java 枚举。B 仍需在实际接入前提供稳定 `planId`，否则 `ACCEPT_PLAN/REJECT_PLAN` 不得写入。

保存用户画像前，D 通过 C 的类型化同意状态查询或事件确认用户已同意保存个性化数据；同意被撤回后，D 立即关闭个性化、删除摘要缓存，并停止后续标签写入。当前文档没有明确“关闭个性化但未撤回同意”时是否继续接收原始行为：在产品或 C 确认前，D 不新增行为标签且将该行为记录为不采集，避免关闭后继续积累长期画像。

画像写入、开关变更、摘要读取、账户删除和行为拒绝仅记录用户内部 ID、操作类型、结果、错误码和 traceId 等最小审计信息；日志不得出现标签完整值、原始 payload、会话内容、Cookie、JWT、邮箱、支付数据或位置。监控仅统计开关关闭率、摘要命中率、标签冲突率、过期率、行为拒绝率和清理失败数。

## Risks / Trade-offs

- B 尚未交付长期偏好确认入口：先实现手工标签和受控内部 Command，B 对接作为独立确认任务。
- C 的画像页面可能仍是静态页面：先以 REST/OpenAPI 和 Mock 夹具交付，页面接入由 C 确认后完成。
- 用户行为与推荐记录的实际来源尚未全部接通：先通过类型化内部入口和测试夹具验证，不能伪造生产行为。
- B 尚未提供稳定 `planId`：V010 已允许 `PLAN` 目标类型，但在 B 提供该业务 ID 前，D 不接收 `ACCEPT_PLAN/REJECT_PLAN` 写入。
- 总设计与画像设计的标签类型、来源名称不一致：先确认唯一枚举和兼容规则，再写表约束、DTO 与消费者夹具。
- 总设计要求 Idempotency-Key 重放原响应，但原三表没有保存写请求结果：以 `profile_write_request` 补足；若 A/C 不接受新增表，必须共同给出同等可靠的持久化恢复方案。
- 隐私文档要求同意后才保存个性化数据，但未定义 D 获取同意状态的契约：在 C 确认前不得把“已登录”视为“已同意”。

## Migration Plan

1. D 将本设计中的四张表字段、索引、约束和清理策略提交给 A 审核，并创建 `V010__create_profile_tables.sql` 草案。
2. A 静态审查草案并明确授权后，在隔离 MySQL 8 数据库执行正反约束验证。
3. D 在迁移已进入开发基线后实现 Repository、缓存和业务用例；默认不开启任何自动行为推断。
4. 回退应用时停止写入新的行为标签，保留既有数据只读；后续结构调整只能新增迁移。

## Open Questions

- A：已确认四表范围、正式版本 V010、D 创建 SQL 草案/A 静态审查与授权验证的职责、UTC 时间规则、`profile_write_request` 的唯一键/字段/30 天保留期/无 `PROCESSING` 规则，以及 `PaymentSucceededEvent` 的最小使用字段。
- B：请确认 `GetProfileSummaryTool` 的注册方式、`ToolContext` 当前用户来源，以及对话长期保存确认的结构化输入。
- C：请确认 `CurrentUserAccessor` 可用于画像 REST，及前端处理 202001、202002、202003 的展示方式。
- C：请确认账户删除时调用 D 的类型化 API 或事件名称、字段、幂等/重试和清理触发时机；登录退出不得触发画像清理。
- D：已确认 `DIALOG -> CONVERSATION`、`ORDER -> BEHAVIOR`、`GENRE -> MOVIE_GENRE`，以及六种标签类型、三种来源、四种状态、两种极性、六种行为和三种目标类型；B 需在接入前确认稳定 `planId` 的来源和格式。
- A、C：A 已确认 `profile_write_request` 保留 30 天、幂等键最大 128、唯一键 `(user_id, operation, idempotency_key)`、同键不同请求为 HTTP 409 / `202004`；不采用持久化 `PROCESSING`，不新增 `202005`，同键并发由唯一键和同一本地事务串行化处理。
- C：请提供个人数据保存同意状态的类型化读取或撤回通知；并确认关闭个性化但未撤回同意时的行为采集产品规则。
- A、D：已确认 `DATETIME(3)` 按 UTC 存储，Java 经注入 `Clock` 写入，REST 返回带时区 ISO 8601。
