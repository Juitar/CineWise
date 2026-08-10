# 推荐批量可售场次契约升级

## 背景

D 的推荐主流程需要在同一日期、多个影院中获取真实可购的影片场次。现有批量查询已经提供数据库侧余座过滤和截断，但影院上限、时效字段和来源字段仍是旧版演示契约，且 D 尚未接入。

## 范围

- A 升级 `ticketing/application` 的批量只读可售场次契约。
- 每次请求接收 `date + cinemaIds`；影院 ID 去重后最多 100 个，返回最多 200 条记录和显式 `truncated`。
- 返回 `movieId`、`cinemaId`、`showId`、票价、开始/结束时间、`dataType`、`source`、`dataAt`、`expiresAt` 以及必要的余座/版本快照。
- 把票务查询不可用错误 `306003` 映射为 HTTP 503。
- 更新单元、MySQL 集成和跨模块契约测试。

## 非范围

- 不新增或修改 Flyway、表、索引、种子或推荐历史。
- 不实现 D 的影片/影院解析、过滤、评分、推荐记录或页面。
- 不修改 D 的推荐适配器；D 在 A 合入后自行改为调用本公开 Application API。
- 不新增本应用 REST Controller，也不允许 D 通过本应用 HTTP 调用该能力。

## Owner 与验收

- A 是票务事实和本次 Application API 的 Owner；D 仅为消费者。
- 可售条件为 `ON_SALE`、`startTime > now`、当天左闭右开窗口内且 `AVAILABLE` 座位数大于 0。
- `expiresAt = min(startTime, dataAt + 60 秒)`；D 只按 A 返回的值判断时效。
- 空结果返回成功空列表；依赖异常抛 306003/HTTP 503；超过 200 条必须返回 `truncated=true`。
