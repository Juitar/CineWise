# 设计

Application Service 按当前业务时钟计算未来七天窗口，Repository 在 SQL 中限定影片、`ON_SALE`、未开场并 `HAVING` 至少一个 `AVAILABLE` 座位。分页排序固定为最近开场时间、影院 ID。

A 仅调用 D 的 `ContentSummaryQueryPort.findCinemaSummaries`，不读取 D 持久化层。D 内容不可用保留 303004；A 数据库访问异常映射 306003；不存在、下线或没有场次统一为空分页，避免猜测内容状态。
