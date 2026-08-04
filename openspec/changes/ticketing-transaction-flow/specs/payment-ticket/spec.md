# Payment and Electronic Ticket Spec

## ADDED Requirements

### Requirement: 固定成功Mock支付

系统 SHALL 提供`POST /api/v1/orders/{orderNo}/payments`。接口 SHALL 只接收本人订单号和Header `Idempotency-Key`，业务请求体为空；后端请求DTO、日志、OpenAPI、数据库和事件 SHALL NOT 定义或接收六位模拟密码。

#### Scenario: 成功支付待支付订单

- GIVEN 本人订单为`PENDING_PAYMENT`、未超过支付截止时间且全部座位仍由该订单`LOCKED`
- WHEN 用户使用稳定幂等键主动发起Mock支付
- THEN 同一事务内创建唯一支付、完成`PENDING_PAYMENT -> PAYING -> PAID`
- AND 支付为`SUCCESS`、全部座位为`SOLD`、生成唯一`VALID`电子票
- AND 返回`orderId/orderNo/paymentNo/orderStatus/paymentStatus/ticketId/stateVersion/updatedAt`

#### Scenario: 订单过期或状态不允许支付

- GIVEN 订单已经到期，或为`CANCELLED/EXPIRED/REFUNDING/REFUNDED`
- WHEN 发起Mock支付
- THEN 到期返回HTTP 409和`205003`，其他非法状态返回HTTP 409和`205002`
- AND 不创建支付或电子票，不改变订单和座位

#### Scenario: 座位归属不完整

- GIVEN 订单仍显示待支付，但任一座位不再是该订单拥有的`LOCKED`座位
- WHEN 发起Mock支付
- THEN 整个支付事务失败并回滚
- AND 不提交`PAYING/PAID`、支付、售座或电子票的部分结果

### Requirement: 支付幂等与并发串行化

系统 SHALL 通过每订单唯一支付和支付幂等键保证安全重放。支付事务 SHALL 锁定本人订单行，使支付、取消和过期对同一订单串行决定权威结果。

#### Scenario: 重复支付同一订单

- GIVEN 订单已经成功支付并生成电子票
- WHEN 使用原幂等键或新的请求键再次支付同一订单
- THEN 返回原支付和原电子票的权威结果
- AND 不创建第二条支付、第二张电子票或重复售座

#### Scenario: 同一支付键用于不同订单

- GIVEN 某支付幂等键已经绑定订单A
- WHEN 使用该键支付订单B
- THEN 返回HTTP 409和`205005`
- AND 订单B、座位、支付和电子票均不变

#### Scenario: 支付与过期或取消竞争

- GIVEN 一个临近截止时间的`PENDING_PAYMENT`订单
- WHEN 支付与取消或过期处理并发执行
- THEN 仅`PAID`、`CANCELLED`或`EXPIRED`中的一个合法终态成立
- AND `PAID`对应座位全部`SOLD`和唯一电子票，其他终态不产生支付或电子票

### Requirement: 支付结果只读恢复

系统 SHALL 提供`GET /api/v1/orders/{orderNo}/payment`，只按当前用户返回订单、支付和电子票的最新权威结果。HTTP超时或断网 SHALL NOT 触发服务端或调用方自动重放支付。

#### Scenario: 支付响应丢失

- GIVEN 支付事务已经成功提交但调用方未收到响应
- WHEN 当前用户按原`orderNo`查询支付结果
- THEN 返回原`paymentNo`、`PAID/SUCCESS`和原`ticketId`
- AND 查询不创建或更新任何交易记录

#### Scenario: 跨用户查询支付

- GIVEN 订单和支付属于用户A
- WHEN 用户B查询该订单支付结果
- THEN 返回HTTP 404和`205001`
- AND 不泄露订单或支付是否存在

### Requirement: 本人电子票详情

系统 SHALL 提供`GET /api/v1/tickets/{ticketId}`，返回本人电子票的`ticketId/ticketCode/orderId/orderNo/showId/seatIds/status/qrPayload/issuedAt/stateVersion/updatedAt`。业务ID SHALL 为字符串，二维码载荷 SHALL 只包含不可逆演示引用。

#### Scenario: 查询本人有效电子票

- GIVEN 当前用户存在支付成功生成的`VALID`电子票
- WHEN 按`ticketId`查询
- THEN 返回该票及订单、场次和座位快照
- AND 不返回模拟密码、JWT、邮箱或完整支付内部记录

#### Scenario: 跨用户查询电子票

- GIVEN 电子票属于用户A
- WHEN 用户B使用该`ticketId`查询
- THEN 返回HTTP 404和`205001`

### Requirement: 支付成功事件生产边界

系统 SHALL 定义冻结字段的`PaymentSucceededEvent`和发布端口。A SHALL 在支付事务全部权威写入完成后、事务仍活动时登记事件，
D SHALL 仅在`AFTER_COMMIT`阶段消费；事务回滚 SHALL NOT 触发消费者。监听或发布失败 SHALL NOT 改变已提交支付，A SHALL NOT
为补齐`cinemaArea`而读取D的私有Mapper、Repository、Entity或表。

#### Scenario: 首次支付成功后发布完整事件

- GIVEN A可从权威订单和场次取得`orderId/showId/userId/cinemaId/startAt/orderVersion`
- AND D的`ContentSummaryQueryPort`返回未过期且`area`非空的影院摘要
- WHEN 首次Mock支付事务成功提交
- THEN D的AFTER_COMMIT消费者收到一次字段完整的`PaymentSucceededEvent`
- AND 事件不包含模拟密码、JWT、Cookie、完整订单明细或二维码载荷

#### Scenario: 幂等支付重放

- GIVEN 某订单已经成功支付并登记过支付成功事件
- WHEN 使用原幂等键或新幂等键再次支付同一订单
- THEN 返回原支付和电子票结果
- AND 不重复登记`PaymentSucceededEvent`

#### Scenario: 支付事务回滚

- GIVEN 支付过程中座位归属、订单版本或唯一记录校验失败
- WHEN 支付事务回滚
- THEN D的AFTER_COMMIT消费者不收到支付成功事件
- AND 订单、支付、座位和电子票保持回滚前状态

#### Scenario: 影院摘要暂不可用于事件

- GIVEN D的公开影院摘要不存在、已过期、`area`为空或查询异常
- WHEN A执行Mock支付
- THEN 支付、座位和电子票事务仍可成功提交
- AND 本次不伪造`cinemaArea`或跨模块读取D私有数据
- AND 后续按PAID订单对账补偿缺失的提醒任务

#### Scenario: 发布或消费失败

- GIVEN 支付权威写入已经完成
- WHEN Spring事件登记失败或D的AFTER_COMMIT消费者抛出异常
- THEN 已提交订单保持`PAID`、支付保持`SUCCESS`、座位保持`SOLD`且电子票保持`VALID`
- AND 调用方可按`orderNo`查询恢复权威支付结果
