# 实现设计

## 应用边界

`SaleableShowBatchQueryService` 继续是唯一公开入口。D 只能依赖该 Application Service 及其不可变 DTO，不访问 ticketing 的 Controller、Mapper、Repository、Entity 或表，也不通过本应用 HTTP 查询。

## 查询和时效

服务使用注入的 `Clock` 在进入查询时取得一次 `dataAt`，并按 Asia/Shanghai 计算 `now` 和日期区间 `[date 00:00, nextDate 00:00)`。仓储 SQL 以 `ON_SALE`、`start_time > now` 和 `HAVING AVAILABLE > 0` 过滤，稳定按 `start_time ASC, id ASC` 排序并读取 201 条探测截断。查询不释放过期锁、不更新座位或场次。

每条返回的 `expiresAt` 是 `min(startTime, dataAt + 60 秒)`。它只保证推荐候选的重新查询时点，不承诺库存或票价在该时间前保持不变；建单仍重新校验场次、价格和座位。

`dataType` 和 `source` 原样来自 A 的 `movie_show` 权威字段。`dataAt` 是本次票务只读快照生成时间，不能由 D 自行伪造或计算。

## 参数和失败

`cinemaIds` 去重后最多 100 个；空集合是成功空结果，避免无条件扫描全影院。空值、非正数、超过上限或不在滚动七天窗口内的日期为 100001。查询仓储发生 `DataAccessException` 时映射到 `QUERY_UNAVAILABLE`（306003/HTTP 503），绝不降级为空列表或部分结果。

## 兼容与验证

这是已合并批量查询的向后兼容字段扩展与规则升级：旧的可售、排序、上限 200 和截断语义保留，影院上限提升为 100。无需迁移；`movie_show.source` 已存在，只需纳入投影。测试覆盖 100 家影院边界、可售过滤、空结果、截断、时效临界、来源字段和不可用错误；D 负责在其 change 中完成适配器接入与推荐回归。
