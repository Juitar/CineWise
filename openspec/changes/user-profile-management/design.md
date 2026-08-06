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

### 0. 当前实现基线

当前代码已存在 C 提供的 `CurrentUserAccessor.requireCurrentUserId()`，D 的 REST 和应用服务只能通过它取得用户 ID；`ToolContext` 不携带用户 ID，画像工具仍须在认证线程内通过该访问器取身份；`ToolResult<T>` 已提供统一结果包装。当前没有 `profile` 生产实现，只有四层包骨架；也没有 C 的 `ProfileDataConsentQuery` 或撤回可靠通知实现。D 本轮只创建不依赖这些接口的领域类型、错误码和分层测试，不创建默认设置、不开放写入入口，也不提供任何“默认同意”的替代实现。

### 1. 数据分为开关、标签和原始行为摘要

`user_preference` 每用户一行，保存 `personalization_enabled` 和乐观锁版本；`user_profile_tag` 保存可解释的长期偏好；`user_behavior_event` 保存最小化、可去重的行为证据。用户 ID 仅复用 C 从认证上下文提供的 `sys_user.id`，D 不生成也不接收请求体中的 userId。

标签来源为 `MANUAL`、`CONVERSATION`、`BEHAVIOR`。冲突时采用最新 `MANUAL`、最新 `CONVERSATION`、聚合 `BEHAVIOR` 的顺序；相同来源取较新的 `updatedAt`。显式标签权重范围为 0.10 到 1.00，不自动衰减；行为标签最高 0.80，30 天乘以 0.85，90 天无新事件转为 `EXPIRED`。行为标签创建或新行为聚合时把最后行为时间加 90 天写入 `expires_at`；衰减任务只更新权重和 `update_time`，绝不改 `expires_at`，以免将终态延后。

本 change 统一采用 `MOVIE_GENRE`、`TIME`、`CINEMA`、`HALL`、`PRICE`、`SEAT` 六种 `tag_type`；旧文档和夹具中的 `GENRE` 一律视为 `MOVIE_GENRE`，不得两者并存。`polarity` 只允许 `LIKE`、`DISLIKE`；`source` 只允许 `MANUAL`、`CONVERSATION`、`BEHAVIOR`，旧 `DIALOG` 映射为 `CONVERSATION`，旧 `ORDER` 不再作为标签来源，支付行为产生的标签统一为 `BEHAVIOR`。`status` 只允许 `ACTIVE`、`DISABLED`、`EXPIRED`、`DELETED`；只有 `DELETED` 必须有 `deleted_at`，其他状态必须没有 `deleted_at`。

首次读取或首次写入某个用户时，服务以事务方式创建默认 `personalization_enabled=true`、`version=0` 的设置；并发首次访问只能保留一条 `user_preference`。标签管理查询按页返回未软删除的 `ACTIVE`、`DISABLED`、`EXPIRED` 标签及总数，默认不返回 `DELETED`；用户可将单个标签从 `ACTIVE` 置为 `DISABLED`，或恢复为 `ACTIVE`，两种状态均不改变标签来源。

### 2. 摘要先检查开关，再读取有效标签

`ProfileSummary` 只包含 `enabled`、`version`、`generatedAt` 和有效标签的类型、值、极性、权重、置信度、来源及更新时间。开关关闭时必须只返回 `enabled=false`，不得查询或返回任何长期标签。摘要不含原始行为、完整对话、邮箱、精确位置或内部持久化 ID。

Redis 缓存键为 `profile:{userId}:v:{version}`，并设置有限 TTL。标签、开关、软删除和过期任务在同一 MySQL 事务中成功写入后必须推进 `user_preference.version`，使旧版本键立即不可达；随后删除该用户所有 `profile:{userId}:v:*` 键仅用于回收。Redis 删除失败不得影响正确性：后续查询按新版本键读取 MySQL 或新缓存，不得继续命中旧摘要。

### 3. 本人写入使用版本和幂等键

新增、更新、删除标签和更新开关均从 `CurrentUserAccessor` 取得用户。写请求必须携带 `If-Match: <version>` 和 `Idempotency-Key`；版本不符返回 HTTP 409 / `202002`，并携带最新资源。相同幂等键重放返回首次的 200 或 201 响应，不重复修改版本、标签或缓存。

标签删除使用软删除并立即从摘要排除。标签不存在或不属于当前用户统一返回 HTTP 404 / `202003`，不暴露其归属。创建同一用户、同一标签类型、值和来源的有效标签违反唯一约束时返回 HTTP 409 / `202001`。

为兑现“相同 `Idempotency-Key` 返回首次 200/201 响应”的接口要求，新增 `profile_write_request` 记录：包含内部 ID、`user_id`、操作类型、幂等键、请求摘要哈希、固定为 `COMPLETED` 的结果状态、首次 HTTP 状态、首次公开响应 JSON、完成时间、创建和过期时间，唯一键为 `(user_id, operation, idempotency_key)`。同键但摘要哈希不同返回 HTTP 409 / `202005 IDEMPOTENCY_PARAMETER_MISMATCH`，提示“幂等键已用于其他请求内容，请重新操作”，不得执行新请求。该表保留 30 天并定向清理；它不作为通用审计表，不保存完整请求体、Cookie、JWT、原始行为或其他接口未公开的数据。

写入顺序固定为：先完成当前用户身份和操作范围校验，再查询 C 的画像保存同意；未同意直接返回 HTTP 403 / `202004 PROFILE_DATA_CONSENT_REQUIRED`，不创建默认设置、标签、行为或缓存。取得有效同意后，计算请求摘要哈希，并按 `(userId, operation, idempotencyKey)` 查询已提交的 `profile_write_request`；同键同摘要直接返回首次 200/201 响应，同键不同摘要返回 HTTP 409 / `202005 IDEMPOTENCY_PARAMETER_MISMATCH`，且不得执行新请求。只有不存在已提交记录时，才校验 `If-Match` 并执行业务写入。这样首次请求成功后即使其他写入已推进版本，携带旧 `If-Match` 的原请求重放仍能恢复首次响应，而不是错误返回 `202002`。

同一 MySQL 本地事务必须同时完成幂等记录插入、画像写入、版本递增、缓存失效和原始公开响应记录；事务成功后结果即为 `COMPLETED`。同键并发由唯一键和事务串行化处理：唯一键冲突后重新读取已提交记录，同摘要返回原结果、不同摘要返回 HTTP 409 / `202005 IDEMPOTENCY_PARAMETER_MISMATCH`；事务失败则整体回滚，不留下可见的处理中记录。当前不持久化 `PROCESSING`；若未来出现真正异步或跨事务写入，必须另建 change 定义租约、CAS 接管、崩溃恢复和客户端重试规则。公开响应 JSON 只保存首次接口实际返回的 `PreferenceResponse`、`TagResponse` 或删除结果字段，以支持恢复；不得保存原始请求、行为 payload、认证信息或额外隐私字段。

用户可直接修改 `MANUAL` 标签的极性、权重和状态，不得把来源改写为其他来源；对 `CONVERSATION`、`BEHAVIOR` 标签，用户可停用或删除，但其来源、置信度和行为计算结果只能由受控服务更新。标签重新启用时仍需满足未过期条件。

### 4. 行为事件只记录最小信息并独立于主流程

行为写入不提供 `POST /internal/profile/events` 或其他本机 HTTP 入口，只允许模块内的类型化 Application API 或已确认的内部事件调用。`eventId` 是全局幂等键；同一用户与同一目标的同类事件 24 小时最多计一次。允许的事件为 `CLICK`、`FAVORITE`、`ACCEPT_PLAN`、`REJECT_PLAN`、`PAID_ORDER`、`NOT_INTERESTED`。

事件仅在累计绝对权重达到 0.30 时创建或更新 `BEHAVIOR` 标签；单次行为和无效事件不会形成强偏好。`BEHAVIOR` 标签权重不得超过 0.800，且必须有 `expires_at`；`MANUAL` 标签权重必须在 0.100 到 1.000 之间。应用服务在写入前校验这两条来源条件，V010 同时以 CHECK 防止绕过应用层的直接写入。调用失败只记录可脱敏诊断，不回滚或阻塞上游业务。

行为调用方必须提供已由自身权限和业务规则校验的事实，D 不查询 A/B/C 的 Entity、Mapper、Repository 或 Controller 来补齐数据。A 只能通过已提交的 `PaymentSucceededEvent` 写入 `PAID_ORDER`；B 只能通过 D 的 `ProfileBehaviorRecorder` 写入已确认方案的 `ACCEPT_PLAN/REJECT_PLAN`；C 的本人页面行为只能通过其已认证线程调用 D 的类型化 Application API，并由 D 从 `CurrentUserAccessor` 取得用户。`CLICK`、`FAVORITE`、`NOT_INTERESTED` 仅作用于 `MOVIE`，`ACCEPT_PLAN`、`REJECT_PLAN` 仅作用于 `PLAN`，`PAID_ORDER` 仅作用于 `SHOW`。其中 `PLAN` 的 `target_id` 必须是 B 提供的稳定 `planId`，不能是模型文本或临时槽位；B 未提供前不得写入这两类事件。

`PAID_ORDER` 仅消费 A 已确认且已提交的 `PaymentSucceededEvent`，最小字段映射为 `event_id=eventId`、`user_id=userId`、`target_type=SHOW`、`target_id=showId`、`order_id=orderId`、`order_version=orderVersion`、`occurred_at=occurredAt`。D 以 `eventId` 去重并归一化写入行为事件，不持久化 `cinemaArea`、`startAt` 等出行字段，也不得仅凭 `showId` 推导影片类型、影院等标签。

24 小时归一化由 D 的应用层短事务完成，不依赖无法表达滚动 24 小时窗口的数据库唯一键：先锁定当前用户的 `user_preference` 行，再按 `(user_id, event_type, target_type, target_id)` 查询近 24 小时事件；每个新的 `eventId` 都保存最小行为摘要，窗口内已有同类目标时不再累计权重或更新行为标签。同一 `eventId` 的唯一键冲突必须读取并返回原处理结果；事件摘要、行为标签更新和去重结果在同一事务内提交，失败时整体回滚，不留下半条记录。测试必须覆盖两个不同 `eventId` 并发提交同一归一化键时只累计一次、同一 `eventId` 重放返回原结果，以及事务失败后重试不留下事件或权重残留。

### 5. B 和推荐只能通过公开摘要能力使用画像

`GetProfileSummaryTool` 只能调用 D 的 Application Service，接收不含 userId 的 `ToolContext` 和固定用途 `RECOMMENDATION`；D 在 `execute(...)` 内通过 C 提供的 `CurrentUserAccessor.requireCurrentUserId()` 取得当前认证用户。当前工具只允许在 HTTP 认证线程同步执行；后续若改为异步执行，必须先由 B、C 提供认证上下文的安全传递方式。B 负责注册只读工具并确认“长期保存”的对话意图；D 不发布 SSE、不保存 Agent 运行数据。

B 已确认方案反馈通过 D 在 `profile/application` 定义并实现的 `ProfileBehaviorRecorder` 调用：`recordPlanAccepted` 固定映射 `ACCEPT_PLAN/PLAN/planId`，`recordPlanRejected` 固定映射 `REJECT_PLAN/PLAN/planId`。`planId` 由 B 在服务端完成计划校验、准备持久化用户可见方案时生成，为 36 位小写 UUID；同一已保存方案及同一最终决定重试时复用原 `planId`、`eventId` 和 `occurredAt`。未确认、取消、过期或无效方案不调用 D；调用失败不改变 B 已保存的用户决定，且无认证后台线程不自动补发。实际确认后的 `CONVERSATION` 写工具、其注册和联调不属于当前 B 的 `agent-interaction-runtime` change，必须由 B、D、C 后续单独建跨模块 change。推荐只读取 `ProfileSummary`，并且仍以用户本轮明确要求优先。

推荐结果必须记录本次 `usedProfile` 及实际采用的标签证据，便于向用户解释“本次参考了什么”；关闭开关、没有有效标签或画像读取失败时该值为 false。推荐记录不复制原始行为和完整画像，且画像服务不可用只降级为不使用长期特征，不阻断候选筛选与排序。

### 6. 迁移申请与生命周期

本节是 A 已分配 V010 后由 D 生成 SQL 草案的依据；所有时间使用 UTC `datetime(3)`，由 Java 经注入 `Clock` 写入，SQL 不得依赖数据库服务器默认时区或 `CURRENT_TIMESTAMP` 默认值；D 自有主键使用 BIGINT 雪花 ID；跨模块只逻辑关联，不建立到 `sys_user`、订单或 Agent 表的物理外键。

| 表 | 字段、类型、可空性、默认值与约束 | 生命周期 |
| --- | --- | --- |
| `user_preference` | `user_id BIGINT NOT NULL` 主键；`personalization_enabled BOOLEAN NOT NULL DEFAULT TRUE`；`version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)`；`create_time DATETIME(3) NOT NULL`；`update_time DATETIME(3) NOT NULL`；`deleted_at DATETIME(3) NULL`。不建立到 `sys_user` 的物理外键。 | 个人数据保存同意撤回后立即停止使用；当前版本不处理账户删除。 |
| `user_profile_tag` | `id BIGINT NOT NULL` 主键；`user_id BIGINT NOT NULL`；`tag_type VARCHAR(32) NOT NULL`，限六种已确认类型；`tag_value VARCHAR(128) NOT NULL`；`polarity VARCHAR(16) NOT NULL`，限 `LIKE/DISLIKE`；`weight DECIMAL(4,3) NOT NULL CHECK (weight >= 0 AND weight <= 1)`；`source VARCHAR(32) NOT NULL`，限 `MANUAL/CONVERSATION/BEHAVIOR`，其中 `MANUAL` 为 0.100–1.000、`BEHAVIOR` 不超过 0.800 且 `expires_at` 非空；`confidence DECIMAL(4,3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1)`；`status VARCHAR(16) NOT NULL`，限四种已确认状态；`expires_at DATETIME(3) NULL`；`version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)`；`deleted_at DATETIME(3) NULL`；`active_flag TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED`；`create_time DATETIME(3) NOT NULL`；`update_time DATETIME(3) NOT NULL`。唯一键 `(user_id, tag_type, tag_value, source, active_flag)`；索引 `(user_id, status, expires_at)`、`(deleted_at)`。 | 软删除后 30 天清理；过期标签不参与摘要。 |
| `user_behavior_event` | `id BIGINT NOT NULL` 主键；`event_id VARCHAR(64) NOT NULL`；`user_id BIGINT NOT NULL`；`event_type VARCHAR(32) NOT NULL`，限六种已确认行为；`target_type VARCHAR(32) NOT NULL`，限 `MOVIE/PLAN/SHOW`；`target_id VARCHAR(64) NOT NULL`；`order_id BIGINT NULL`、`order_version BIGINT NULL` 仅用于 `PAID_ORDER` 且 `order_id > 0`；`session_id VARCHAR(64) NULL`；`payload_json JSON NULL`，只允许已确认的最小白名单字段；`occurred_at DATETIME(3) NOT NULL`；`create_time DATETIME(3) NOT NULL`。唯一键 `(event_id)`；索引 `(user_id, occurred_at)`、`(occurred_at)`。 | 原始摘要保留 90 天；聚合标签按自己的时效保留。 |
| `profile_write_request` | `id BIGINT NOT NULL` 主键；`user_id BIGINT NOT NULL`；`operation VARCHAR(32) NOT NULL`；`idempotency_key VARCHAR(128) NOT NULL`；`request_hash CHAR(64) NOT NULL`；`status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED' CHECK (status = 'COMPLETED')`；`http_status SMALLINT NOT NULL CHECK (http_status IN (200, 201))`；`response_json JSON NOT NULL`，仅保存首次公开响应；`completed_at DATETIME(3) NOT NULL`；`expires_at DATETIME(3) NOT NULL`；`create_time DATETIME(3) NOT NULL`；`update_time DATETIME(3) NOT NULL`。唯一键 `(user_id, operation, idempotency_key)`；定向清理索引 `(expires_at)`。 | 仅用于同一画像写操作的响应恢复；保留 30 天定向清理，清理后不影响标签和开关真实状态。 |

迁移必须检查权重/置信度范围、非负版本、已确认的枚举值、幂等记录状态一致性和时间合法性；A 已完成 V010 的静态审查、空 MySQL 验证和共享库发布。V010 已冻结，后续结构调整只能新增向前迁移。全部业务时间以 UTC 写入 `DATETIME(3)`，Java 通过注入的 `Clock` 获取时间，禁止依赖数据库服务器默认时区；REST 一律输出带时区的 ISO 8601。

### 7. 同意状态和来源枚举必须先定清楚

当前版本已取消账户注销和账户删除功能。D 不实现账户删除接口、通知、账户删除清理任务或相关类型；登录和退出登录只处理会话，不触发画像停用或清理。

当前后端总设计第 7 章写有 `MOVIE_GENRE/TIME/CINEMA/HALL/PRICE/SEAT` 和旧来源 `MANUAL/DIALOG/ORDER/BEHAVIOR`，画像详细设计第 11 章写有 `MANUAL/CONVERSATION/BEHAVIOR` 与 `PAID_ORDER` 事件。本 change 已由 D 确认统一映射：`DIALOG -> CONVERSATION`、`ORDER -> BEHAVIOR`、`GENRE -> MOVIE_GENRE`，并按第 1、4 节创建数据库枚举 CHECK 和 Java 枚举。B 仍需在实际接入前提供稳定 `planId`，否则 `ACCEPT_PLAN/REJECT_PLAN` 不得写入。

保存用户画像前，D 必须调用 C 的 `ProfileDataConsentQuery.query(long userId)`。其返回 `ProfileDataConsentSnapshot(userId, granted, consentVersion, grantedAt, withdrawnAt, version)`：没有同意记录时 `granted=false`、其余时间/版本字段按 C 的约定为空或 0；已同意时返回同意版本和时间；已撤回时返回撤回时间。只有 `granted=true` 才可创建默认设置、写入标签或行为；否则统一返回 HTTP 403 / `202004 PROFILE_DATA_CONSENT_REQUIRED`。生产环境在 C 尚未实现该接口前一律按未同意处理；测试可注入显式返回 `granted=true` 的实现，不能默认同意。

C 在撤回事务提交后发送 `ProfileDataConsentWithdrawnEvent(eventId, userId, consentVersion, consentRecordVersion, occurredAt, traceId)`，其中 `occurredAt` 为 UTC `Instant`。D 以 `eventId` 去重；收到后立即关闭个性化、删除 Redis 画像缓存、停止后续标签/偏好/行为写入，并按画像数据保留规则清理已有数据。重复通知不得重复创建清理任务或延长清理时间。C 的发送重试为第 1、5、15、60、360 分钟，之后每 6 小时一次，最多 10 次；人工恢复必须复用原 `eventId`。

用户关闭个性化但尚未撤回同意时，C 不提交画像行为；D 仍必须检查开关并拒绝采集、写入、补采或回放关闭期间发生的行为。重新开启后仍须先取得 `granted=true` 才允许后续采集；撤回同意优先于开关状态。

画像写入、开关变更、摘要读取、同意撤回和行为拒绝仅记录用户内部 ID、操作类型、结果、错误码和 traceId 等最小审计信息；日志不得出现标签完整值、原始 payload、会话内容、Cookie、JWT、邮箱、支付数据或位置。监控仅统计开关关闭率、摘要命中率、标签冲突率、过期率、行为拒绝率和清理失败数。

## Risks / Trade-offs

- B 尚未交付长期偏好确认入口：先实现手工标签和受控内部 Command，B 对接作为独立确认任务。
- C 的画像页面可能仍是静态页面：先以 REST/OpenAPI 和 Mock 夹具交付，页面接入由 C 确认后完成。
- 用户行为与推荐记录的实际来源尚未全部接通：先通过类型化内部入口和测试夹具验证，不能伪造生产行为。
- B 已确认稳定 `planId` 和 `ProfileBehaviorRecorder` 调用边界；实际接入仍等待 B 的 `agent-plan-feedback-events` change，D 在此之前不接收 `ACCEPT_PLAN/REJECT_PLAN` 生产写入。
- 总设计与画像设计的标签类型、来源名称不一致：先确认唯一枚举和兼容规则，再写表约束、DTO 与消费者夹具。
- 总设计要求 Idempotency-Key 重放原响应，但原三表没有保存写请求结果：以 `profile_write_request` 补足；若 A/C 不接受新增表，必须共同给出同等可靠的持久化恢复方案。
- C 的同意查询和撤回可靠通知尚未实现；D 的生产代码必须在其缺失时按未同意拒绝写入。

## Migration Plan

1. A 已完成 V010 静态审查、隔离 MySQL 验证和共享库发布；D 不再修改 V010。
2. D 在迁移已进入开发基线后实现 Repository、缓存和业务用例；C 的同意查询未实现前，生产写入默认拒绝。
3. 默认不开启任何自动行为推断；关闭个性化期间的行为不补采、不回放。
4. 回退应用时停止写入新的行为标签，保留既有数据只读；后续结构调整只能新增迁移。

## Open Questions

- A：已确认四表范围、正式版本 V010、D 创建 SQL 草案/A 静态审查与授权验证的职责、UTC 时间规则、`profile_write_request` 的唯一键/字段/30 天保留期/无 `PROCESSING` 规则，以及 `PaymentSucceededEvent` 的最小使用字段。
- B：已确认 `GetProfileSummaryTool` 通过 D 的 `CurrentUserAccessor` 取得认证用户，并确认 `ProfileBehaviorRecorder`、服务端 UUID `planId`、重放和不调用边界；实际接入由 B 的 `agent-plan-feedback-events` change 完成。
- C：已确认 `ProfileDataConsentQuery`、`ProfileDataConsentWithdrawnEvent`、未同意 HTTP 403 / `202004 PROFILE_DATA_CONSENT_REQUIRED`、撤回通知重试和关闭个性化后的不采集规则；接口和可靠通知尚未实现。
- C：当前版本取消账户删除；D 不实现任何账户删除通知或账户删除清理任务。仍请确认 `CurrentUserAccessor` 可用于画像 REST，以及前端对 202001、202002、202003、202004 的展示方式。
- D：已确认 `DIALOG -> CONVERSATION`、`ORDER -> BEHAVIOR`、`GENRE -> MOVIE_GENRE`，以及六种标签类型、三种来源、四种状态、两种极性、六种行为和三种目标类型；B 已确认稳定 UUID `planId` 的来源和格式。
- C：已确认同键不同请求返回 HTTP 409 / `202005 IDEMPOTENCY_PARAMETER_MISMATCH`，提示“幂等键已用于其他请求内容，请重新操作”。前端不得自动重试，应提示用户刷新当前画像状态后重新确认操作。
- A、D：已确认 `DATETIME(3)` 按 UTC 存储，Java 经注入 `Clock` 写入，REST 返回带时区 ISO 8601。
