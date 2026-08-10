# 设计

`TravelOrderSummaryQueryService` 是 A 侧 Application Port 实现，事务只读，用户身份始终来自 `CurrentUserAccessor`。它验证正十进制 `orderId` 后调用订单 Repository 的最小投影方法。

Repository 通过一次明确列出的 `ticket_order` 与 `movie_show` 查询按 `id` 和 `user_id` 限定资源，不返回金额、座位、支付、退款或用户字段。没有记录统一抛出 205001；`DataAccessException` 转换为 305001，不伪造空结果。

Port DTO 使用字符串 ID 和 `OffsetDateTime`，由应用层将权威 `LocalDateTime` 按业务时区转换。D 只能依赖此端口，并在自己的任务事实校验 `orderId/showId`；D 对外错误由 D 自己映射。
