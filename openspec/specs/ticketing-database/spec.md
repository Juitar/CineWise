# ticketing-database Specification

## Purpose
TBD - created by archiving change ticketing-transaction-flow. Update Purpose after archive.
## Requirements
### Requirement: 订单主表约束

系统 SHALL 创建`ticket_order`，字段 SHALL 包含`id/order_no/user_id/show_id/ticket_count/unit_price/total_amount/status/expire_time/client_request_id/idempotency_key/version/paid_time/cancelled_time/refunded_time/create_time/update_time`。

#### Scenario: 重复建单标识

- GIVEN 同一用户已经存在相同`client_request_id`或相同建单`idempotency_key`的订单
- WHEN 再次尝试插入新订单
- THEN 数据库唯一约束拒绝第二条记录
- AND 后续应用服务只能查询并返回原业务结果或报告参数摘要冲突

#### Scenario: 非法票数或金额

- GIVEN 票数不在1至6之间，或总金额不等于单价乘票数
- WHEN 插入订单
- THEN CHECK约束拒绝记录

### Requirement: 订单座位快照约束

系统 SHALL 创建`ticket_order_seat`，字段 SHALL 包含`id/order_id/show_seat_id/row_no_snapshot/seat_no_snapshot/unit_price/create_time/update_time`。同一订单 SHALL NOT 重复关联同一场次座位。

#### Scenario: 重复订单座位

- GIVEN 订单已经包含某个`show_seat_id`
- WHEN 再次插入相同`order_id+show_seat_id`
- THEN 数据库唯一约束拒绝重复明细

### Requirement: 每订单唯一支付与电子票

系统 SHALL 创建`mock_payment`和`electronic_ticket`。一个订单 SHALL 最多有一条Mock支付记录和一张电子票；支付金额 SHALL 非负。

#### Scenario: 重复支付或重复出票

- GIVEN 订单已经存在支付记录或电子票
- WHEN 再次为同一`order_id`插入记录
- THEN 数据库唯一约束拒绝第二条记录

### Requirement: 每订单唯一退票记录

系统 SHALL 创建`refund_request`。`refund_no`、`order_id`、`user_id+idempotency_key` SHALL 分别唯一，Agent确认`action_id` SHALL 可查询。

#### Scenario: 重复退票

- GIVEN 订单已经存在退票记录
- WHEN 再次为相同`order_id`插入退票记录
- THEN 数据库唯一约束拒绝第二条记录

### Requirement: 迁移只生成不执行

本阶段 SHALL 生成标准多行Flyway SQL文件，但 SHALL NOT 连接数据库或执行迁移。执行权保留给A后续手动确认。

#### Scenario: 本阶段交付检查

- GIVEN SQL文件已经生成
- WHEN 审查本change交付物
- THEN 可以静态核对五张表的字段、主键、唯一约束、索引和CHECK约束
- AND 没有Flyway schema history、数据库表或数据被本阶段修改

### Requirement: 原子锁座与建单

系统 SHALL 提供`POST /api/v1/orders`，仅接收Header `Idempotency-Key`与Body `showId/seatIds/clientRequestId`。系统 SHALL 在同一本地事务中校验当前用户、场次、服务端金额和座位，完成全部条件锁座、订单及明细写入。

#### Scenario: 成功建立待支付订单

- GIVEN 已登录用户选择一个未开场的`ON_SALE`场次和1至6个`AVAILABLE`座位
- WHEN 使用稳定`clientRequestId`和`Idempotency-Key`创建订单
- THEN 服务端使用场次权威单价计算总额
- AND 座位全部为`LOCKED`且`lock_order_no`指向新订单
- AND 订单为`PENDING_PAYMENT`，订单明细数与座位数相等

#### Scenario: 任一座位不可锁定

- GIVEN 请求包含多个座位且至少一个座位不属于该场次、非`AVAILABLE`或版本已变化
- WHEN 创建订单
- THEN 返回HTTP 409和`204001`
- AND 本请求不保留任何座位锁、订单或订单明细

#### Scenario: 并发竞争同一座位

- GIVEN 20个独立请求竞争同一个`AVAILABLE`座位
- WHEN 请求并发创建订单
- THEN 最多一个请求成功
- AND 座位只指向成功订单，不存在超卖或孤立订单明细

### Requirement: 建单幂等与结果恢复

系统 SHALL 通过`userId + clientRequestId`和`userId + Idempotency-Key`保证建单幂等，并提供`GET /api/v1/orders/by-request/{clientRequestId}`查询当前用户的原订单。

#### Scenario: 同键同参重试

- GIVEN 同一用户已使用某`clientRequestId`和幂等键成功建单
- WHEN 使用相同场次、相同座位集合和相同两个请求标识重试
- THEN 返回原`orderId/orderNo`及其最新权威状态
- AND 不再锁座或创建第二个订单

#### Scenario: 同键异参重试

- GIVEN 同一用户已使用某`clientRequestId`或幂等键建单
- WHEN 重试时改变场次、座位集合或两个请求标识中的另一个
- THEN 返回HTTP 409和`205005`
- AND 原订单和座位锁不变

#### Scenario: 建单响应丢失

- GIVEN 服务端已提交订单但调用方未收到响应
- WHEN 当前用户按原`clientRequestId`查询
- THEN 返回已提交的原订单
- AND 其他用户使用同一`clientRequestId`不能看到该订单

### Requirement: 交易恢复与密码隔离

系统 SHALL 在后续任务中提供本人订单查询、取消、过期释放、Mock支付查询与唯一电子票。后端契约 SHALL NOT 接收、存储或记录六位模拟密码。

#### Scenario: 未知写结果恢复

- GIVEN 建单或支付写请求的HTTP结果未知
- WHEN 调用方使用原`clientRequestId`、`orderNo`或原幂等键恢复
- THEN 返回MySQL中的最新权威结果
- AND 不自动重放写请求或生成新幂等键
