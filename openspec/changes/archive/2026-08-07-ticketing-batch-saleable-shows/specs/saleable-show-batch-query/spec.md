## ADDED Requirements

### Requirement: 按日期和多影院查询可售场次

系统 SHALL 提供公开 Application API `SaleableShowBatchQueryService.query(SaleableShowBatchQuery)`。

#### Scenario: 查询多影院多影片场次

- **GIVEN** 日期位于当前 Asia/Shanghai 日期至未来第六日，且影院 ID 集合包含多个有效影院
- **WHEN** 调用批量查询 API
- **THEN** 返回所有满足可售条件的场次，并按 `startTime ASC, showId ASC` 排序

输入 SHALL 包含必填 `date`、`cinemaIds` 和 `limit`（limit 可省略时由调用适配器填充默认值 200），并可选成对的 `timeFrom/timeTo`。`date` 和时间按 `Asia/Shanghai` 解释。

- `cinemaIds` 为正整数集合；输入集合为空时返回成功的空结果。
- 调用方可传入重复 ID；服务 SHALL 去重后查询，去重后数量超过 50 才视为超限。空值、非正数、超过 50 个、日期不在当前日期至未来六天窗口、单独传入时间或 `timeFrom >= timeTo`、limit 不在 1..200 均 SHALL 抛 `CommonErrorCode.INVALID_PARAMETER`（100001）。
- 查询时间使用当前注入 `Clock`；场次必须满足 `status=ON_SALE`、`startTime > now`、在日期左闭右开区间内且 `availableSeatCount > 0`。
- 数据库查询 SHALL 使用稳定排序 `startTime ASC, showId ASC` 和 `LIMIT limit + 1`，不得在内存中全量读取后筛选售罄场次。
- 返回超过 limit 的匹配结果时，结果仅含前 limit 条且 `truncated=true`；未超过时为 `false`。空结果为成功空列表且 `truncated=false`。
- 查询依赖不可用时 SHALL 抛 `TicketingErrorCode.QUERY_UNAVAILABLE`（306003），不得转为空列表。

#### Scenario: 空影院集合

- **GIVEN** 日期合法且 `cinemaIds` 为空
- **WHEN** 调用批量查询 API
- **THEN** 返回成功的空列表且 `truncated=false`

#### Scenario: 非法参数

- **GIVEN** 存在非正数、去重后超过 50 个影院、日期越过七天窗口、非法时间范围或非法 limit
- **WHEN** 调用批量查询 API
- **THEN** 抛出 `CommonErrorCode.INVALID_PARAMETER`（100001）

#### Scenario: 截断结果

- **GIVEN** 匹配场次多于请求的 limit
- **WHEN** 调用批量查询 API
- **THEN** 只返回前 limit 条且 `truncated=true`，不得静默截断

#### Scenario: 查询依赖不可用

- **GIVEN** 票务查询仓储不可用
- **WHEN** 调用批量查询 API
- **THEN** 抛出 `TicketingErrorCode.QUERY_UNAVAILABLE`（306003），不得返回空列表

### Requirement: 可售场次返回字段

每条 `SaleableShowView` SHALL 包含 `showId`、`movieId`、`cinemaId`、`price`、`startTime`、`endTime`、`expiresAt`、`source`、`saleable` 和必要的版本/余座快照字段。ID 在 Java 内为 long，金额为 BigDecimal 且固定两位小数语义；跨模块适配器不得重新计算金额。`expiresAt` SHALL 等于 `startTime`，只表示推荐候选截止时间，`saleable` SHALL 固定为 true，`source` SHALL 原样映射票务 `dataType`。

#### Scenario: 返回稳定的票务事实

- **GIVEN** 一条场次有两位小数 basePrice、余座和 dataType
- **WHEN** 查询返回该场次
- **THEN** `price` 使用 A 的 basePrice、`source` 等于 dataType、`expiresAt` 等于 startTime 且 saleable 为 true

### Requirement: 数据与模块边界

- 多影院、多影片场次全部按规则返回。
- 售罄、非 ON_SALE、已开场、日期外以及时间范围外场次不返回。
- A 的 API SHALL 不读取 content 持久化层；D SHALL 只能调用上述 Application API。

#### Scenario: D 通过公开端口调用

- **GIVEN** D 的推荐适配器需要场次候选
- **WHEN** 适配器查询场次
- **THEN** 只调用 `SaleableShowBatchQueryService`，不访问 ticketing 的 Controller、Mapper、Repository、Entity 或表
