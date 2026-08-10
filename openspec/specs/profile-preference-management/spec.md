# profile-preference-management Specification

## Purpose
TBD - created by archiving change user-profile-management. Update Purpose after archive.
## Requirements
### Requirement: 用户只能管理自己的画像开关和标签

系统 SHALL 从 `CurrentUserAccessor` 取得当前用户，不得信任 REST、工具或内部 Command 中的 userId。系统 SHALL 提供 `GET /api/v1/profile/me/tags`、`POST /api/v1/profile/me/tags`、`PUT /api/v1/profile/me/tags/{tagId}`、`DELETE /api/v1/profile/me/tags/{tagId}` 和 `PUT /api/v1/profile/me/personalization`。`/api/v1/profile/me` 的账户昵称、头像等基础资料由 C 负责，本 change 不读取或修改这些字段。

#### Scenario: 查看本人有效标签
- **GIVEN** 当前用户已登录且存在有效标签
- **WHEN** 用户查询自己的画像标签
- **THEN** 系统返回个性化开关、版本和该用户未删除、未过期的标签
- **AND** 每个标签包含来源、极性、权重、置信度、状态、过期时间和更新时间

#### Scenario: 首次查看画像
- **GIVEN** 当前登录用户已取得有效的画像保存同意，且从未保存过画像设置
- **WHEN** 用户查询画像标签或首次修改个性化开关
- **THEN** 系统原子创建默认 `enabled=true`、`version=0` 的偏好设置
- **AND** 并发首次请求也只保留一条 `user_preference`

#### Scenario: 分页查看已停用和已过期标签
- **GIVEN** 当前用户同时存在 `ACTIVE`、`DISABLED`、`EXPIRED` 和软删除标签
- **WHEN** 用户按页查询标签
- **THEN** 系统返回前三种状态及分页总数
- **AND** 默认不返回 `DELETED` 标签

#### Scenario: 跨用户修改标签
- **GIVEN** 当前用户不是标签所有者
- **WHEN** 用户用该标签 ID 发起更新或删除
- **THEN** 系统返回 HTTP 404 和 `202003`
- **AND** 不泄露标签是否存在或属于谁

### Requirement: 标签写入必须校验来源、范围和重复项

系统 SHALL 仅保存 `MANUAL`、已确认的 `CONVERSATION` 或满足聚合规则的 `BEHAVIOR` 标签。`tagType` 仅允许 `MOVIE_GENRE`、`TIME`、`CINEMA`、`HALL`、`PRICE`、`SEAT`；`polarity` 仅允许 `LIKE`、`DISLIKE`；`status` 仅允许 `ACTIVE`、`DISABLED`、`EXPIRED`、`DELETED`。`MANUAL` 权重必须在 0.100 到 1.000 之间；`BEHAVIOR` 权重不得超过 0.800 且必须带 `expiresAt`。非法类型、值、极性、来源条件或权重返回 HTTP 400 / `102001`。同一用户的相同标签类型、值和来源存在有效记录时，创建必须返回 HTTP 409 / `202001`。

#### Scenario: 用户新增手工排斥标签
- **GIVEN** 当前用户没有同来源的有效“恐怖/不喜欢”标签
- **WHEN** 用户新增符合范围的 `MANUAL` 标签
- **THEN** 系统创建标签并返回 HTTP 201
- **AND** 下一次画像摘要可以读取该标签

#### Scenario: 用户删除标签
- **GIVEN** 当前用户拥有一个有效标签
- **WHEN** 用户删除该标签
- **THEN** 系统软删除标签并立即将其从后续摘要排除
- **AND** 已生成的历史推荐不被重新计算或改写

#### Scenario: 用户停用或恢复单个标签
- **GIVEN** 当前用户拥有一个未删除标签
- **WHEN** 用户将标签状态更新为 `DISABLED` 或从 `DISABLED` 恢复为 `ACTIVE`
- **THEN** 系统使用版本条件更新标签并使缓存失效
- **AND** 停用标签不进入摘要，恢复后仅在未过期时重新进入摘要

#### Scenario: 行为标签缺少有效期或超过权重上限
- **WHEN** 受控服务尝试写入 `BEHAVIOR` 标签且没有 `expiresAt` 或权重大于 0.800
- **THEN** 系统拒绝该写入
- **AND** 不产生或更新画像标签

### Requirement: 写入必须防止并发覆盖和重复提交

系统 SHALL 要求标签和开关写请求携带 `If-Match: <version>` 与 `Idempotency-Key`。系统在完成当前用户身份和操作范围校验并取得有效画像保存同意后，MUST 先按 `(userId, operation, idempotencyKey)` 查询已提交幂等记录：同键同请求摘要直接返回首次 200/201 响应，同键不同摘要返回 HTTP 409 / `202005 IDEMPOTENCY_PARAMETER_MISMATCH`，不得执行新请求。前端收到 `202005` 不得自动重试，应提示“幂等键已用于其他请求内容，请重新操作”，并要求用户刷新当前画像状态后重新确认操作。仅在不存在记录时才校验 `If-Match`。版本不匹配时返回 HTTP 409 / `202002` 和服务端最新资源；相同幂等键重放时不重复增加版本或写入记录。

#### Scenario: 两个页面同时修改开关
- **GIVEN** 两个客户端读到同一画像版本
- **WHEN** 第一客户端成功更新开关，第二客户端使用旧版本提交
- **THEN** 第二客户端返回 `202002` 和最新开关状态
- **AND** 系统保留第一客户端的写入

#### Scenario: 网络超时后的新增标签重放
- **GIVEN** 首次新增标签已提交但客户端未收到响应
- **WHEN** 客户端带同一 `Idempotency-Key` 重试
- **THEN** 系统返回首次创建的标签响应
- **AND** 数据库中没有第二条标签或额外版本更新

#### Scenario: 后续写入推进版本后重放首次请求
- **GIVEN** 用户使用版本 3 和某个 `Idempotency-Key` 成功创建标签，并保存了 201 响应
- **AND** 后续其他写入已将画像版本推进到 4
- **WHEN** 原客户端使用同一幂等键、同一请求摘要和旧 `If-Match: 3` 重放
- **THEN** 系统返回首次 201 响应
- **AND** 不返回 `202002`，不再次修改标签、版本或缓存

#### Scenario: 同一幂等键提交不同内容
- **GIVEN** 当前用户已使用一个 `Idempotency-Key` 成功提交标签写入
- **WHEN** 用户以同一键提交不同的请求摘要
- **THEN** 系统返回 HTTP 409 / `202005 IDEMPOTENCY_PARAMETER_MISMATCH`，且不执行第二次写入
- **AND** 前端不自动重试，提示用户刷新当前画像状态后重新确认操作

### Requirement: 行为事件必须去重且不干扰上游业务

系统 SHALL 仅通过模块内类型化 Application API 或已确认内部事件接收 `CLICK`、`FAVORITE`、`ACCEPT_PLAN`、`REJECT_PLAN`、`PAID_ORDER` 和 `NOT_INTERESTED`，不得提供 `POST /internal/profile/events` 或其他本机 HTTP 行为入口。A 只能用已提交的 `PaymentSucceededEvent` 写入 `PAID_ORDER`；B 只能用 D 的 `ProfileBehaviorRecorder` 写入已确认方案；C 的本人页面行为只能在已认证线程内调用 D 的类型化 API，用户身份由 D 的 `CurrentUserAccessor` 获取。`CLICK`、`FAVORITE`、`NOT_INTERESTED` 的目标必须为 `MOVIE`；`ACCEPT_PLAN`、`REJECT_PLAN` 的目标必须为 B 提供稳定 `planId` 的 `PLAN`；`PAID_ORDER` 的目标必须为 A 的 `showId` 对应的 `SHOW`，并只使用 `PaymentSucceededEvent` 的 `eventId`、`userId`、`orderId`、`showId`、`orderVersion`、`occurredAt`。`eventId` 是全局幂等键；同一用户、事件类型、目标类型和目标 ID 在 24 小时内最多计一次。D 必须在短事务内锁定该用户的画像设置、查询 24 小时窗口并记录最小事件摘要，窗口内事件不得再次累计权重。无效事件返回 HTTP 400 / `102002`，处理失败不得回滚订单、支付或推荐主流程。未经身份校验的 userId、完整对话、支付明细和位置数据不得进入行为调用。

#### Scenario: 重复行为事件
- **GIVEN** 某个 `eventId` 已被成功接收
- **WHEN** 上游重放相同事件
- **THEN** 系统返回原处理结果
- **AND** 不重复写入行为事件或累计标签权重

#### Scenario: 单次点击
- **GIVEN** 用户首次产生 `CLICK` 行为
- **WHEN** 系统归一化该事件
- **THEN** 系统保存最小事件摘要
- **AND** 不因单次点击创建强偏好标签

#### Scenario: 不同事件并发命中同一归一化窗口
- **GIVEN** 两个不同 `eventId` 同时提交同一用户、事件类型、目标类型和目标 ID
- **WHEN** 两个事件都在同一个 24 小时窗口内
- **THEN** 系统通过短事务只累计一次行为权重
- **AND** 两条最小事件摘要均不泄露支付、会话或位置内容

#### Scenario: 行为事务失败后重试
- **GIVEN** 行为事件写入或标签累计所在事务失败
- **WHEN** 上游使用同一 `eventId` 重试
- **THEN** 失败事务不留下事件记录或权重残留
- **AND** 重试按首次有效请求处理

#### Scenario: 未受信任来源提交已支付行为
- **WHEN** 非 A 的受信任支付事实来源尝试写入 `PAID_ORDER`
- **THEN** 系统拒绝该事件且不保存行为或更新标签
- **AND** D 不通过访问订单表自行补齐或验证该事实

### Requirement: 画像缓存必须在写入后立即失效

系统 SHALL 将摘要缓存按用户版本隔离，并在开关、标签、软删除或过期处理成功后删除该用户的缓存。Redis 不可用时系统 MUST 回退到 MySQL 查询，不得返回其他用户的缓存或阻断画像写入。

#### Scenario: 用户关闭个性化
- **GIVEN** 当前用户已有缓存的启用摘要
- **WHEN** 用户成功关闭个性化
- **THEN** 系统删除该用户摘要缓存
- **AND** 下一次读取不会返回旧标签

### Requirement: 仅在有效同意状态下保存画像数据

系统 SHALL 在创建默认设置、更新开关、标签及行为摘要前调用 C 的 `ProfileDataConsentQuery.query(long userId)`，并只在返回 `granted=true` 时写入。没有同意记录、已撤回同意或 C 的查询接口尚未实现时，系统 MUST 按未同意处理，返回 HTTP 403 / `202004 PROFILE_DATA_CONSENT_REQUIRED`，且不创建默认设置、标签、行为或缓存。D 不读取认证表，也不得将注册隐私同意、登录状态、个性化开关和画像保存同意视为同一个状态。

#### Scenario: 用户未同意保存个性化数据
- **GIVEN** C 返回当前用户没有有效同意状态
- **WHEN** 用户提交标签或受信任调用方提交行为事件
- **THEN** 系统拒绝保存且不创建画像缓存
- **AND** 返回 HTTP 403 / `202004 PROFILE_DATA_CONSENT_REQUIRED`

#### Scenario: 用户撤回同意
- **GIVEN** 用户已有启用的画像与缓存
- **WHEN** C 在撤回事务提交后发送 `ProfileDataConsentWithdrawnEvent(eventId, userId, consentVersion, consentRecordVersion, occurredAt, traceId)`
- **THEN** 系统立即关闭个性化并删除缓存
- **AND** 后续摘要只返回 `enabled=false`，后续标签、偏好和行为写入均被拒绝

#### Scenario: 重复撤回通知
- **GIVEN** D 已按某个 `eventId` 处理同意撤回
- **WHEN** C 重放该通知或人工恢复发送
- **THEN** 系统保持关闭和清理状态
- **AND** 不重复创建清理任务或延长清理时间

#### Scenario: 个性化已关闭但同意仍有效
- **GIVEN** 用户的画像保存同意仍为 `granted=true`，但个性化开关为关闭
- **WHEN** C 或其他受信任调用方提交画像行为
- **THEN** 系统不采集、不写入、不补采也不回放该行为
- **AND** 用户重新开启后仍须重新查询并取得 `granted=true` 才能采集后续行为

### Requirement: 画像审计和日志必须最小化

系统 SHALL 对画像写入、摘要读取、同意撤回、行为拒绝和清理结果记录最小审计信息。日志与指标不得记录标签完整值、原始 payload、会话正文、Cookie、JWT、邮箱、支付明细、精确位置或路线数据。

#### Scenario: 行为事件被拒绝
- **WHEN** 行为事件因来源、字段或同意状态被拒绝
- **THEN** 审计仅记录内部用户 ID、事件类型、拒绝原因码和 traceId
- **AND** 不记录原始事件 payload 或用户隐私内容

