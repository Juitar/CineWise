# 真实排期候选 Provider Specification

## Purpose

让 D 在学习用途的 NetStart Provider 中取得、标准化并保存可追溯的外部排期候选，再由 A 通过公开 Port 自行导入本地票务沙箱；外部排期不构成本地库存或交易承诺。

## ADDED Requirements

### Requirement: D 只能提供已映射的外部排期候选

系统 SHALL 仅在外部 `externalShowId`、`externalMovieId`、`externalCinemaId` 非空且稳定，并能通过 ACTIVE 内容身份映射唯一得到本地 `movieId/cinemaId` 时，生成 `ExternalShowtimeSnapshot`。系统 MUST NOT 按名称、地址、坐标或时间猜测身份。

#### Scenario: 外部排期身份完整且映射唯一

- **GIVEN** 外部场次具有稳定的影片、影院和场次 ID，且影片/影院各自有唯一 ACTIVE 映射
- **WHEN** D 标准化该场次
- **THEN** D 返回带本地 `movieId/cinemaId`、外部三类 ID、`source/dataAt/expiresAt` 的候选快照
- **AND** 快照不含座位、余座、订单、支付或退款字段

#### Scenario: 身份缺失或无法唯一映射

- **GIVEN** 任一外部 ID 缺失，或影片/影院映射为未找到、歧义或已失效
- **WHEN** D 处理该条排期
- **THEN** D 隔离该条候选并分别使用 `303005`、`303006` 或 `303007` 说明映射问题
- **AND** D 不创建本地场次，也不修改任何 A 的票务数据

### Requirement: 外部排期不是本地票务事实

系统 SHALL 将外部 `listedPrice` 标记为 `REFERENCE_ONLY`，仅作为候选参考；A 必须在自己的 Application Service 决定本地价格、影厅、座位和余座。D MUST NOT 写入 `movie_show`、影厅、座位、订单、支付或退款数据。

#### Scenario: Provider 返回外部票价或余座

- **GIVEN** Provider 同时返回标价、余座或座位信息
- **WHEN** D 构造公开候选 DTO
- **THEN** DTO 只可包含可空 `listedPrice` 和固定 `priceSemantic=REFERENCE_ONLY`
- **AND** D 丢弃外部余座和座位数据，不把它们当作可售库存

### Requirement: 外部场次必须使用三元幂等身份

系统 SHALL 公开 `ExternalShowtimeKey(provider, externalCinemaId, externalShowId)`。NetStart 的 `seqNo` 只被证实在单个 Provider 和影院范围内可用，A MUST NOT 仅用 `seqNo` 导入或更新本地沙箱场次。

#### Scenario: A 重复导入同一外部场次

- **GIVEN** 两次候选具有相同 `provider`、`externalCinemaId` 和 `externalShowId`
- **WHEN** A 决定导入候选
- **THEN** A 使用该三元组进行幂等判断
- **AND** 候选仍必须是 `qualityStatus=ACCEPTED`、`isExpired=false`、`degraded=false`

### Requirement: 候选必须具有统一时区、时效和降级标记

系统 SHALL 将开场和散场时间转换为 `Asia/Shanghai` 的带偏移 ISO 8601 时间。每条候选必须返回 `source`、`dataAt`、`expiresAt`、`isExpired`、`degraded` 和 `fallbackType`。`endTime` 不晚于 `startTime`、时间无法解析或超过已确认未来窗口的记录必须隔离。`rejectedSnapshots` 最多返回 200 条，超出时必须返回 `rejectedTruncated=true`。

#### Scenario: Provider 没有可靠散场时间

- **GIVEN** Provider 只提供开场时间或散场时间不晚于开场时间
- **WHEN** D 标准化该排期
- **THEN** D 将该条返回到 `rejectedSnapshots` 并标记 `qualityStatus=END_TIME_REJECTED`
- **AND** 不把该条放入 `snapshots`，A 无法将其导入本地场次

#### Scenario: 影片或影院身份无法解析

- **GIVEN** 影片或影院缺少 ACTIVE 映射、映射存在歧义或已经失效
- **WHEN** D 标准化该排期
- **THEN** D 将该条返回到 `rejectedSnapshots`，并带 `IDENTITY_REJECTED` 与 `303005`、`303006` 或 `303007`
- **AND** 其他合格候选仍可继续返回

#### Scenario: Provider 不可用但存在未过期快照

- **GIVEN** Provider 因限流、超时、断网或 5xx 不可用，且存在未过期的已映射快照
- **WHEN** A 通过公开 Port 查询候选
- **THEN** D 返回该快照并标记 `degraded=true`、`fallbackType=SNAPSHOT`
- **AND** A 不得将降级候选自动导入为新的本地交易场次

#### Scenario: 不存在未过期快照

- **GIVEN** Provider 不可用且没有未过期快照
- **WHEN** A 查询候选
- **THEN** D 返回 `303004` 和明确失败分类
- **AND** D 不伪造空排期或 Demo 排期

### Requirement: Provider 调用必须受控且不泄漏敏感信息

系统 SHALL 在 Provider 调用前校验参数并实施本地限流、连接/读取超时和仅一次的短重试。只有连接失败或 5xx 可以重试；429、不可重试 4xx、字段不合格和身份错误不得重试。日志、快照和审计不得包含 `ci`、Key、Cookie、完整原始响应或地点原文。

#### Scenario: Provider 返回 429

- **GIVEN** Provider 或本地限流拒绝请求
- **WHEN** D 处理本次查询或刷新
- **THEN** D 不立即重试，并记录 `RATE_LIMITED` 失败分类
- **AND** 最近成功快照保持不变

### Requirement: A 只能通过公开 Port 导入候选

A SHALL 仅通过 D 的 `ExternalShowtimeSnapshot` DTO/Port 读取候选，并在自己的 Application Service 完成校验和本地导入。D 和 A MUST NOT 跨模块访问对方的 Entity、Mapper、Repository 或表。

#### Scenario: A 导入合格候选

- **GIVEN** 候选为 `ACCEPTED`、未过期且未降级
- **WHEN** A 决定导入候选
- **THEN** A 在自己的边界创建或更新本地沙箱场次、影厅、座位和本地价格
- **AND** 后续 `SaleableShowBatchQueryService` 仍只按本地 ON_SALE、未开场和余座大于零返回可售结果
