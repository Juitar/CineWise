## Purpose

在支付成功后可靠创建用户自己的出行提醒任务，生成可追溯的建议并安全投递 EMAIL；订单变化、重复事件和投递结果不明时均可恢复。

## ADDED Requirements

### Requirement: 支付成功必须幂等创建出行任务

系统 SHALL 仅在 A 的支付事务成功提交后消费 `PaymentSucceededEvent`，并使用 `eventId` 去重、`travel_task.order_id` 唯一约束创建任务。事件必须包含 `eventId`、`orderId`、`showId`、`userId`、`cinemaArea`、`String cinemaId`、`startAt`、`orderVersion` 和 `occurredAt`；`cinemaId` 必须是可解析为正 `BIGINT` 的十进制字符串。D 不得读取 A 的 Entity、Mapper、Repository 或 Controller 补齐字段。

#### Scenario: 重复支付事件
- **GIVEN** 同一 `orderId` 已存在出行任务
- **WHEN** D 收到相同或不同 `eventId` 的重复支付成功事件
- **THEN** 系统返回或保留原任务且数据库中仅有一个该订单的任务
- **AND** 不创建 Agent 运行、SSE 事件或通知

#### Scenario: 支付事务回滚
- **GIVEN** A 的支付事务未提交
- **WHEN** 支付流程结束
- **THEN** D 不创建出行任务

#### Scenario: 合法影院业务 ID 创建任务
- **GIVEN** 支付成功事件的 `cinemaId` 可解析为正 `BIGINT`
- **WHEN** D 在事务提交后创建任务
- **THEN** 系统将该值写入 `travel_task.cinema_id`
- **AND** 任务不保存影院坐标、用户位置或路线数据

#### Scenario: 支付事件影院业务 ID 非法
- **GIVEN** 支付成功事件的 `cinemaId` 缺失、为空、超出 `BIGINT` 或解析后不大于零
- **WHEN** D 在事务提交后处理该事件或 A 调用 `ensureTask`
- **THEN** D 跳过本次任务创建并输出仅含 `eventId` 的受控错误
- **AND** 不写入 `cinema_id=NULL` 的新任务，且不回滚已经提交的支付事务

### Requirement: 订单失效必须按版本取消任务

系统 SHALL 消费 A 在退款完成后登记的 `OrderInvalidated`；该事件包含 `PaymentSucceededEvent` 的全部字段，包括同类型的 `String cinemaId`，另含 `invalidReason`，MVP 固定为 `REFUNDED`。正数 `cinemaId` 可写入新建墓碑；缺失、空白、非数字、溢出、`0` 或负数只表示路线终点不可用，不得阻止退款取消。D 仅当事件的 `orderVersion` 不小于任务的订单版本时取消未结束任务，并将既有建议标记为只读过期。较旧事件 MUST 被忽略。

#### Scenario: 新版本订单失效事件
- **GIVEN** 存在未结束的出行任务
- **WHEN** 系统收到订单版本不小于任务版本的失效事件
- **THEN** 任务变为 `CANCELLED` 且后续不再生成或发送提醒
- **AND** 已有建议只能以过期快照展示

#### Scenario: 旧失效事件
- **GIVEN** 任务已记录更高的订单版本
- **WHEN** 系统收到较旧的订单失效事件
- **THEN** 系统不改变任务状态和建议

#### Scenario: 已有任务收到退款事件
- **GIVEN** 支付成功事件已创建任务且 `cinema_id` 非空
- **WHEN** 系统收到合法退款失效事件
- **THEN** 系统只更新任务取消状态和订单版本，保留原 `cinema_id`
- **AND** 不以退款事件覆盖已写入的影院业务 ID

### Requirement: 支付与订单失效的补偿必须分别按最终订单状态执行

系统 SHALL 公开 `ensureTask(PaymentSucceededEvent)` 和 `ensureTaskCancelled(OrderInvalidated)` 两个 D Application API，供 A 的对账调用；二者均返回仅含 `taskId`、`status`、`orderVersion` 的任务摘要。A 每五分钟分别扫描最近 24 小时的 `PAID`、`REFUNDED` 订单，每批最多 100 条并逐条调用：仍为 `PAID` 的订单调用前者，退款已完成且 `invalidReason=REFUNDED` 的订单调用后者。单条失败不得回滚整批。两个调用都不得访问 D 的 Entity、Mapper、Repository 或表，且重复调用不得创建重复任务、通知或状态回退。

#### Scenario: 支付成功事件消费失败后的补偿
- **GIVEN** 支付已提交，但 D 未能完成事件消费
- **WHEN** A 扫描仍为 `PAID` 的订单并调用 `ensureTask`
- **THEN** D 按原 `orderId` 创建或返回唯一任务
- **AND** 不因补偿调用创建第二条任务或通知

#### Scenario: 退款失效事件消费失败后的补偿
- **GIVEN** 退款已完成，但 D 未能完成 `OrderInvalidated` 消费
- **WHEN** A 扫描退款完成订单并调用 `ensureTaskCancelled`
- **THEN** D 按退款完成后的 `orderVersion` 取消对应未结束任务并使已有建议只读过期
- **AND** 系统不将该订单当作 `PAID` 再调用 `ensureTask`

#### Scenario: 退款事件先于支付成功事件到达
- **GIVEN** D 尚未为该 `orderId` 创建出行任务
- **WHEN** D 先收到或由 A 对账调用 `ensureTaskCancelled` 的退款完成事件
- **THEN** D 创建或保留 `CANCELLED` 墓碑任务，写入合法 `cinema_id` 并记录不低于该事件的 `orderVersion`
- **AND** 随后到达的支付成功事件不得创建或重新打开该任务

#### Scenario: 退款先到但影院业务 ID 非法
- **GIVEN** D 尚未为该 `orderId` 创建任务且退款失效事件的 `cinemaId` 非法
- **WHEN** D 处理该事件或 A 调用 `ensureTaskCancelled`
- **THEN** D 创建或保留 `CANCELLED` 墓碑，写入 `cinema_id=NULL` 并记录该订单版本
- **AND** 随后到达的支付成功事件不得重开任务，路线查询返回明确不可用

#### Scenario: 终态任务收到迟到的支付成功事件
- **GIVEN** 任务状态为 `CANCELLED` 或 `COMPLETED`
- **WHEN** A 重放事件或调用 `ensureTask`
- **THEN** 系统返回原任务的最小摘要
- **AND** 不将任务变为 `PENDING`、不生成建议或通知

### Requirement: 出行迁移必须约束计数与终态清理时间

A 分配 Flyway 版本后，迁移 MUST 为 `travel_task.order_version`、`travel_task.version`、`travel_task.retry_count`、`travel_notification_log.task_version` 和 `travel_notification_log.attempt_count` 设置非负 CHECK。V013 MUST 增加可空的 `travel_task.cinema_id BIGINT`，不建物理外键、默认值或回填，并以 CHECK 仅允许 `NULL` 或正数。迁移 MUST 保证 `travel_task` 的 `COMPLETED`、`CANCELLED`、`FAILED` 状态有 `closed_at`，其他状态没有 `closed_at`；`travel_notification_log` 的 `SENT`、`FAILED` 状态有 `resolved_at`，`PENDING`、`SENDING`、`UNKNOWN` 状态没有 `resolved_at`。

#### Scenario: 负计数或版本写入
- **WHEN** MySQL 插入或更新任一负数订单版本、任务版本、重试次数或投递次数
- **THEN** 对应 CHECK 拒绝写入
- **AND** 不产生可供调度或清理的异常记录

#### Scenario: 非法影院 ID 写入
- **WHEN** MySQL 向 `travel_task.cinema_id` 写入 `0` 或负数
- **THEN** V013 的 CHECK 拒绝写入
- **AND** 既有历史任务的 `cinema_id=NULL` 仍可读取，但路线查询返回明确不可用

#### Scenario: 任务终态与关闭时间不一致
- **WHEN** MySQL 写入 `COMPLETED`、`CANCELLED`、`FAILED` 但 `closed_at` 为空的任务
- **THEN** 对应 CHECK 拒绝写入
- **WHEN** MySQL 写入非终态但 `closed_at` 非空的任务
- **THEN** 对应 CHECK 拒绝写入

#### Scenario: 通知终态与解决时间不一致
- **WHEN** MySQL 写入 `SENT`、`FAILED` 但 `resolved_at` 为空的通知
- **THEN** 对应 CHECK 拒绝写入
- **WHEN** MySQL 写入 `PENDING`、`SENDING`、`UNKNOWN` 且 `resolved_at` 非空的通知
- **THEN** 对应 CHECK 拒绝写入

### Requirement: 提醒任务必须生成可降级的建议快照

系统 SHALL 在到达提醒时间或用户合法刷新时，以任务的影院区域和开场时间生成天气与确定性通用交通建议，并为新的任务版本追加一条带 `source`、`dataTime`、`expiresAt`、`isExpired`、`degraded`、`fallbackType` 的不可变建议快照。任务 `version` 的条件更新与快照 INSERT MUST 在同一本地事务完成；快照 INSERT 失败时 MUST 回滚该次版本更新。天气不可用时 MUST 保留通用建议并明确天气不可用，不得编造天气事实。任务取消时不得更新旧快照；查询层必须将该任务的所有快照作为只读过期数据返回。

#### Scenario: 天气查询成功
- **GIVEN** 任务有效且天气结果未过期
- **WHEN** 系统生成提醒建议
- **THEN** 返回天气风险、通用交通建议和完整来源时效字段
- **AND** 任务进入 `READY`

#### Scenario: 天气及其回退均不可用
- **GIVEN** 天气缓存、Demo 数据和外部 Provider 均不可用
- **WHEN** 系统生成提醒建议
- **THEN** 系统返回通用交通建议和明确的天气不可用信息
- **AND** 任务仍可进入 `READY`，电子票展示不受影响

#### Scenario: 用户刷新建议
- **GIVEN** 任务处于允许刷新状态且达到刷新间隔
- **WHEN** 系统生成新的建议
- **THEN** 系统在条件更新任务版本成功后追加一条新的 `travel_advice_snapshot`
- **AND** 不更新旧快照、旧快照仍可按原版本只读查询

#### Scenario: 并发刷新同一任务
- **GIVEN** 两个刷新请求读取到同一任务版本
- **WHEN** 两个请求并发执行任务版本条件更新和快照 INSERT
- **THEN** 只有一个事务取得新版本并写入对应的一条快照
- **AND** 未取得新版本的请求返回当前任务或在读取新版本后重新计算
- **AND** 重试不覆盖旧快照，也不产生相同 `travel_task_id`、`task_version` 的重复快照

#### Scenario: 快照写入失败
- **GIVEN** 任务版本条件更新已在本地事务中成功
- **WHEN** 同一事务的快照 INSERT 失败
- **THEN** 整个事务回滚，任务版本保持原值
- **AND** 不留下没有对应快照的新任务版本

#### Scenario: 任务取消后的建议查询
- **GIVEN** 任务已变为 `CANCELLED`
- **WHEN** 用户查询既有建议
- **THEN** 系统返回原快照内容并标记 `isExpired=true`
- **AND** 不更新任何快照记录或生成新版本

### Requirement: EMAIL 投递必须可查询恢复且不重复发送

系统 SHALL 使用 `VIEWING_REMINDER:{taskId}:{taskVersion}:{triggerType}` 作为唯一 `deliveryKey` 创建提醒投递记录。D 只向 C 的公共邮件端口传 `recipientUserId`，不得读取邮箱或保存邮箱明文。投递结果不明时 MUST 先按同一 `deliveryKey` 查询结果；只有确认 `SENT` 后任务才能进入 `NOTIFIED`。

#### Scenario: 重复调度同一提醒
- **GIVEN** 同一任务版本和触发类型已有投递记录
- **WHEN** 定时任务重复执行或并发执行
- **THEN** 系统只保留一条 `deliveryKey` 对应的记录
- **AND** Mock 邮件 Provider 最多产生一封邮件

#### Scenario: 投递结果未知
- **GIVEN** 邮件请求超时或发送后进程中断
- **WHEN** 系统无法确认投递结果
- **THEN** 投递记录变为 `UNKNOWN` 并查询 Provider
- **AND** 未查询到明确结果前不得自动重发

### Requirement: 任务和建议只能由本人读取或修改

系统 SHALL 从 `CurrentUserAccessor` 取得当前用户，并校验任务的 `userId`；任务不存在或不属于当前用户时不得返回任务、建议或通知信息。B 的对话调用只能读取已校验任务或建议摘要，不得创建任务、刷新建议或触发提醒。

#### Scenario: 跨用户查询任务
- **GIVEN** 当前用户不是任务所有者
- **WHEN** 用户查询任务、建议、路线或餐饮
- **THEN** 系统拒绝访问且不泄露任务是否存在

#### Scenario: 对话读取建议摘要
- **GIVEN** B 已在当前用户范围内请求任务摘要
- **WHEN** D 返回出行工具结果
- **THEN** 结果只包含任务状态、建议摘要和来源时效信息
- **AND** 不创建任务、不刷新快照、不发送提醒或请求位置
