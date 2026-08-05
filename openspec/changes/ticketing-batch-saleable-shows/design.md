# 实现设计

## 分层

`SaleableShowBatchQueryService` 位于 ticketing/application，接收不可变查询条件、负责校验、计算业务日期窗口、调用 `ShowQueryRepository` 并组装 `SaleableShowBatchResult`。持久化层新增专用批量投影方法和 MyBatis SQL；不改变现有 `findSaleableShows`。

## 查询与截断

服务将去重后的影院 ID传给仓储，并请求 `limit + 1` 条。SQL 在 `GROUP BY` 后使用 `HAVING SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 1 ELSE 0 END) > 0` 排除售罄场次，再按 `ms.start_time, ms.id` 排序并 LIMIT。应用层只负责移除探测行并设置 truncated。

## 跨模块边界

返回 DTO 不含 Entity、Mapper 行对象或座位明细，也不含影院名称。D 先用自己的内容公开 API 将城市解析为本地 cinemaIds，再调用 A 的 Application API；A 不反向解析城市。

## 迁移与兼容

本 change 不修改表结构、Flyway、种子、现有单影院查询和 REST 行为。推荐模块的表和业务另行由 D 提交 change。

## 验证

单元测试覆盖校验、时间窗口、排序、截断、空集合和查询异常；MySQL 集成测试覆盖多影院、多影片、售罄过滤、状态/日期/时间边界及 limit。跨模块测试使用 stub/mock 的公开 Application API，验证不依赖 Mapper 或 Controller。
