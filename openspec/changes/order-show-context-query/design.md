# Design: 个人订单场次上下文只读投影

## 设计结论

为个人订单查询建立独立的只读主投影。该投影在 Mapper 中以 `ticket_order.show_id = movie_show.id` 连接 A 的两张表，同时选择订单字段和三个场次字段。交易使用的 `OrderRepository.OrderSnapshot`、`OrderSnapshotRow` 以及 `findByOrderNoForUpdate` 保持不变。

## 分层

- API：列表和详情返回专用 `OrderQueryResponse`，在原订单字段基础上增加 `movieId`、`cinemaId`、`showStartTime`。
- Application：`OrderQueryService` 返回 `OrderQueryView`；当前用户继续由 `CurrentUserAccessor` 获取。
- Infrastructure：新增个人订单只读投影行和查询方法；列表主查询一次连接 `movie_show`，座位仍按本页订单 ID 批量加载。
- Transaction：建单、幂等恢复、取消、支付和退款继续使用原 `OrderRepository` 交易投影，不依赖新增读模型。

## 数据与兼容性

- `movieId`、`cinemaId` 来自 `movie_show` 的 BIGINT，REST 转为十进制字符串。
- `showStartTime` 来自 `movie_show.start_time`，REST 按 `Asia/Shanghai` 转换为带偏移时间。
- 订单创建前已经校验场次存在，`movie_show` 为订单的逻辑必需记录，因此只读投影使用 `INNER JOIN`，与现有管理订单读模型保持一致。
- 本次只增加 GET 响应字段，不修改请求、状态、错误码、权限或数据库结构。
- POST 建单、取消和按 `clientRequestId` 恢复继续返回现有 `OrderResponse`；前端把个人订单 GET 类型定义为其扩展类型，避免要求写接口额外查询展示字段。

## 查询与锁边界

列表查询一次取得当前页订单及场次主字段，再通过既有 `findSeatIdsByOrderIds` 一次加载座位。详情用本人 `userId + orderNo` 查询一条只读投影，再加载该订单座位。新增 SQL 不使用 `FOR UPDATE`，也不在交易事务持锁期间调用。

## 测试

- H2 集成测试覆盖列表和详情三个字段、字段时间转换、本人过滤、空分页及跨用户 404。
- Mapper/服务测试确认列表座位仍批量加载，交易 Mapper 的 `FOR UPDATE` SQL 无改动。
- 固定 JSON 夹具增加成功列表和详情中的三个字段，并校验不会出现 D 的内容字段。
- 运行 `mvnw.cmd verify`、OpenSpec 严格校验和 `git diff --check`。
