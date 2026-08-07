# 实现设计

## 调用与数据边界

调用方向为：`D ExternalShowtimeQueryPort -> A ExternalShowtimeSandboxImportService -> A ticketing persistence`。

D 的 `durationMinutes` 和 `auditoriumText` 都是外部展示参考。A 只在本地沙箱模型中使用它们：`durationMinutes` 计算本地预计结束时间，`auditoriumText` 仅作本地沙箱影厅展示名的一部分。它们不表示真实散场时间、稳定外部影厅或真实库存。

本地交易权威如下：

- `movie_show.base_price`：A 配置的本地沙箱交易价格，绝不使用 `listedPrice` 覆盖。
- `movie_show.end_time`：A 按明确的本地预计结束时间规则计算并标记为本地沙箱事实。
- `auditorium`、`show_seat`、余座、锁座和订单：全部由 A 创建和维护。
- A 不读取 D 的快照表，不保存完整第三方响应，也不调用本应用 HTTP Controller。

## 候选准入

D 已合入公开 DTO；A 的适配器只消费其“本地沙箱参考可用”候选，并要求稳定外部三元键、已映射的
本地 `movieId/cinemaId`、未开场的 `startTime`、正整数 `durationMinutes`、可空 `auditoriumText`、
`source/dataAt/expiresAt` 与降级状态。

A 仅在以下条件同时满足时导入：

1. D 标记该候选可用于本地沙箱参考；
2. 候选未过期、未降级且没有回退来源；
3. 请求结果没有成功候选截断；
4. `durationMinutes` 为正数，开场时间仍在未来；
5. 影片和影院本地业务 ID 均为正数。

任一条件不满足时，A 返回可观测的跳过结果并且不写入票务表。A 不从 `rejectedSnapshots` 导入。

## A 导入分层

A 以内部 `ExternalShowtimeSandboxReference` 隔离 D 的公开 DTO。适配器只负责显式映射，不能把 D 的
Provider 类型或持久化类型泄漏到 A 的票务领域；导入应用服务先在事务外读取候选，再委派逐条短事务落库。

该核心必须用注入的 `Clock` 判断候选时效，按业务时区将带偏移开场时间转换为本地 `LocalDateTime`，并返回“就绪/跳过原因”的不可变结果。核心不连接数据库、不创建影厅和座位，也不依赖尚未确认的本地价格、座位布局或影厅命名规则。

## 本地沙箱模型与幂等

需要新增 A 拥有的映射表（版本待 A 按全局顺序分配）：

`ticketing_external_showtime_mapping`

- 主键：A 生成的雪花 `BIGINT`；
- 外部幂等键：`provider + external_cinema_id + external_show_id`，唯一；
- 本地关联：`show_id`，唯一逻辑关联 `movie_show.id`，不建物理外键；
- 快照审计：最后成功导入的 `source/data_at/expires_at` 与 `create_time/update_time`；
- `data_at` 必须早于 `expires_at`；映射承担跨导入批次的幂等身份，不能按普通快照 TTL
  自动删除。历史交易关联的映射永久保留，后续如需清理无交易的孤立映射，必须新增向前迁移并
  同步删除策略。
- 表引擎、字符集、索引、CHECK 和兼容方案在 D 的 DTO 确认后写入迁移申请。

首次导入在 A 的最小事务中创建映射、本地沙箱影厅、场次及固定座位布局。影厅名称必须明确包含“本地沙箱”，不得冒充外部真实影厅。重复导入先按外部三元键读取映射：

- 本地场次与候选关键事实一致：返回既有结果，不重复创建；
- 候选关键事实变更且本地场次没有锁座、订单或已售座位：后续版本再依据已确认更新策略处理；本期先安全跳过并记录原因；
- 已产生锁座、订单、支付、电子票或退款：绝不更新或删除场次、价格、影厅和座位。

外部调用发生在事务之外；A 的本地创建与映射写入在同一事务中完成。唯一约束是重复导入和并发导入的最终防线。

本期提供受控管理 HTTP 入口 `POST /api/v1/admin/ticketing/external-showtimes/import`。请求体包含一个 ISO
日期和 1 至 100 个十进制字符串影院 ID；应用层去重并拒绝空、非正数、前导零或超出 long 范围的 ID。
Controller 只负责校验与 DTO 转换，管理应用服务先通过 `CurrentUserAccessor` 复核 `RoleCode.ADMIN`，再调用
既有导入 Application API。路径复用 C 已有的 `/api/v1/admin/** -> hasRole("ADMIN")` 安全规则，不创建第二条
安全链，不解析 JWT、Cookie 或 CSRF。

本期创建受控的异步导入任务表和恢复器，但不自动拉取或构造新的导入范围。重复手动触发仍由外部三元键唯一
约束安全吸收；同一候选返回已有 A 本地场次，不重复创建影厅、场次或座位。

## 异步导入任务

一次导入会为每个合格候选建立 80 个本地座位，不能再由 HTTP 请求等待全部完成。V021 新增 A 拥有的
`ticketing_external_showtime_import_task`，使用公开 `task_id` 与内部可选 `client_request_id` 分别表示任务和
客户端幂等意图。影院范围、终态本地场次 ID 均保存为 JSON 数组；A 应用层验证正十进制、去重和稳定排序。
任务保存状态、短租约、计数、截断标记、稳定错误码、版本和审计时间；不保存 D 的原始快照或异常正文。

- `PENDING`：尚未取得租约，计数均为 0，未开始、无错误、无结果；
- `RUNNING`：已有开始时间和成对租约，尚未产生可见结果；
- `SUCCESS`：全部候选已处理（包括截断而零写入的正常结果），错误码为空，结果数组长度等于成功数；
- `PARTIAL`：至少一项成功且至少一项失败，保留稳定错误码及成功的本地场次 ID；
- `FAILED`：没有成功导入，必须有稳定错误码；Provider 在取得候选前失败时允许三个计数均为 0。

Controller 仅创建 PENDING 任务并返回任务 ID。后台 Worker 以条件更新取得 RUNNING 租约后，调用既有导入
Application Service；每个候选仍保持现有独立短事务。Worker 与恢复器必须以 `status + lease_owner + version`
条件更新，避免慢 Worker 覆盖已被接管的任务。应用启动/定时恢复只接管已过期的 RUNNING 或既有 PENDING
任务，绝不凭空创建范围；V019 外部三元键仍是重复写入的最终防线。任务在 `expire_at` 后由 A 的清理 Job
硬删除，默认保留 30 天；查询只返回任务状态、计数、截断和 A 本地场次 ID。

## 时间、价格和展示

`localEstimatedEndTime = startTime + durationMinutes`，仅用于满足 A 本地 `movie_show.end_time` 约束和展示“预计结束”。所有页面/API 需要在联调阶段展示其本地沙箱属性，不能显示为外部真实散场时间。

本地沙箱价格使用 A 配置项；`listedPrice` 只允许用于诊断或可选的“外部参考价”展示，绝不进入建单金额计算。

## 错误、恢复和测试

- D 查询不可用：保留 D 的 `303004` 语义，A 不把它变为空导入成功。
- 管理端越权：Spring Security 与 A 应用服务均返回 403；越权请求不得访问 D Port 或写入 A 的票务表。
- 导入重复或并发：依赖映射唯一约束，重复返回已有本地场次。
- 导入中途失败：本地事务回滚映射、影厅、场次和座位，不保留半成品。
- 结果未知：导入触发方只查询同一外部三元键对应的本地映射，不重新生成一份候选或本地价格。
- 单元、MySQL 集成和端到端票务回归分别验证候选过滤、事务幂等、座位可售性、订单金额和历史场次不可改写。
