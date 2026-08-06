# 按影片查询可售影院

为影片详情购票入口新增 `GET /api/v1/shows/available-cinemas`。A 聚合权威场次和座位事实，D 的 `ContentSummaryQueryPort` 补齐影院展示摘要；C 在契约合入后接入页面。

本次不新增 Flyway、城市筛选、内容持久化访问或交易写操作。
