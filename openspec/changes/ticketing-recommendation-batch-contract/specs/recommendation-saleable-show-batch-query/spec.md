## ADDED Requirements

### Requirement: 批量可售场次公开 Application API

系统 SHALL 通过 `SaleableShowBatchQueryService.query(SaleableShowBatchQuery)` 提供按日期和影院集合的票务事实。输入 `date` 按 Asia/Shanghai 解释，影院 ID 去重后最多 100 个；空集合 SHALL 成功返回空结果，且不得查询全部影院。

#### Scenario: 多影院、多影片可售查询

- **GIVEN** date 位于当前业务日到未来第六日，且包含一个或多个有效影院 ID
- **WHEN** D 调用公开 Application API
- **THEN** 返回这些影院当天所有 `ON_SALE`、尚未开场且 `AVAILABLE` 余座大于 0 的场次，并按 `startTime ASC, showId ASC` 稳定排序

#### Scenario: 影院集合边界

- **GIVEN** 输入去重后恰好 100 个正数影院 ID
- **WHEN** 调用查询
- **THEN** 正常执行；超过 100 个、包含 null 或非正数时抛 100001

#### Scenario: 空影院集合

- **GIVEN** 输入日期合法且 cinemaIds 为空
- **WHEN** 调用查询
- **THEN** 返回空 records 和 `truncated=false`，且不访问仓储

### Requirement: 返回票务快照和时效

每条记录 SHALL 返回 `movieId`、`cinemaId`、`showId`、`price`、`startTime`、`endTime`、`dataType`、`source`、`dataAt` 和 `expiresAt`。价格来自 A 的 `basePrice`；`dataType/source` 原样来自 `movie_show`；`dataAt` 是 A 在本次查询生成的业务快照时间。

`expiresAt` SHALL 等于 `min(startTime, dataAt + 60 seconds)`。到达该时刻的候选不可继续生成可购方案；它不是价格或库存不变承诺。

#### Scenario: 场次早于快照存活窗口

- **GIVEN** 某场次开场时间早于 `dataAt + 60 秒`
- **WHEN** 返回该场次
- **THEN** expiresAt 等于 startTime

#### Scenario: 场次晚于快照存活窗口

- **GIVEN** 某场次开场时间晚于 `dataAt + 60 秒`
- **WHEN** 返回该场次
- **THEN** expiresAt 等于 dataAt 加 60 秒

### Requirement: 截断和故障语义

服务每次最多返回 200 条匹配记录；当匹配数超过 200 条时 SHALL 仅返回前 200 条并设置 `truncated=true`，不得静默截断。查询无匹配时返回成功空列表。

仓储或数据库不可用时 SHALL 抛 `TicketingErrorCode.QUERY_UNAVAILABLE`（306003），REST 映射为 HTTP 503，不得伪装为空列表或部分结果。

#### Scenario: 截断

- **GIVEN** 匹配场次超过 200 条
- **WHEN** 调用查询
- **THEN** 返回前 200 条和 `truncated=true`

#### Scenario: 查询不可用

- **GIVEN** 票务仓储抛出数据访问异常
- **WHEN** 调用查询
- **THEN** 抛 306003；调用方可将其作为暂不可用处理

### Requirement: 跨模块访问边界

D SHALL 只调用上述 Application API 及 DTO；不得访问 ticketing 的 Mapper、Repository、Entity、Controller、数据库表或本应用 HTTP 接口。D 不得缓存或生成第二份场次、价格、余座或时效事实。

#### Scenario: 推荐消费

- **GIVEN** D 需要生成影片加影院的可购方案
- **WHEN** 查询票务事实
- **THEN** 仅调用 `SaleableShowBatchQueryService`，并直接使用返回的 dataAt、expiresAt、price 和余座字段
