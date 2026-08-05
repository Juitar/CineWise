# Proposal: admin-order-read-model

## 背景

A已经拥有订单、支付、电子票和退款的权威交易数据，但管理端尚不能按管理员视角分页检索和查看聚合详情。系统设计要求提供严格只读、字段脱敏且仅向ADMIN开放的管理订单接口，并禁止管理端直接改变交易状态。

## 目标

- 提供`GET /api/v1/admin/orders`管理订单分页查询。
- 提供`GET /api/v1/admin/orders/{orderNo}`聚合详情查询。
- 支持`orderNo/userKeyword/status/movieId/showId/dateFrom/dateTo/page/size`白名单筛选。
- 聚合订单、座位快照、Mock支付、电子票和退款摘要，业务ID保持字符串、金额保持两位小数。
- 通过`CurrentUserAccessor`执行应用层ADMIN复核，并由C在公共安全链配置同一路径角色规则。
- 通过C公开用户摘要能力搜索用户并返回脱敏邮箱，不读取`sys_user`表或认证持久化实现。

## 非目标

- 不提供取消、退款、补票、改状态或其他管理写接口。
- 不实现JWT、Cookie、CSRF、登录、SecurityContext解析或第二条安全过滤链。
- 不访问C的Entity、Mapper、Repository或直接联表`sys_user`。
- 不访问D的内容持久化；第一阶段只返回权威`movieId/cinemaId/showId`，不伪造影片或影院名称。
- 不新增或修改Flyway迁移。

## Owner与协作

A拥有`admin/order`查询用例、交易聚合、REST契约和测试。C拥有认证上下文、安全链和用户摘要事实，需要确认并提供按关键字查用户ID、按用户ID批量返回脱敏邮箱的公开Application API。D不受本次代码影响；后续若管理页需要内容名称，只能追加使用D公开摘要端口。

## 验收结果

- ADMIN可以分页查询和查看订单聚合详情，普通用户得到HTTP 403和`100403`。
- 空结果返回空分页，非法筛选返回HTTP 400和`100001`。
- 用户关键字只经C公开能力解析，响应不包含完整邮箱、JWT、Cookie、幂等键、二维码载荷或退款内部快照。
- 两个接口执行前后交易表内容和版本保持不变。
