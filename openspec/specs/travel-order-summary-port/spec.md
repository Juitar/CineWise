# travel-order-summary-port Specification

## Purpose
TBD - created by archiving change travel-order-summary-port. Update Purpose after archive.
## Requirements
### Requirement: 查询本人订单摘要
系统 SHALL 提供 `TravelOrderSummaryQueryPort.queryMyOrder(String orderId)`，返回 `orderId/orderNo/showId/movieId/cinemaId/showStartTime`；ID 为无前导零正十进制字符串，时间为带 Asia/Shanghai 偏移的 ISO 8601。

#### Scenario: 合法本人订单
- **WHEN** 当前认证用户传入合法正十进制订单 ID
- **THEN** 返回与 A 权威订单和场次一致的六个字段，且不包含敏感或交易明细字段

#### Scenario: 非法订单 ID
- **WHEN** 参数为空、空白、0、负数、前导零或超出正 `long`
- **THEN** 返回 `100001`，且不访问 Repository

#### Scenario: 不存在或非本人
- **WHEN** 最小投影没有匹配当前用户的订单
- **THEN** 返回 `205001`，不泄露资源是否属于其他用户

#### Scenario: 查询依赖不可用
- **WHEN** Repository 查询抛出数据库访问异常
- **THEN** 返回 `305001`/HTTP 503，不转换为空结果

