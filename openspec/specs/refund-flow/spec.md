# refund-flow Specification

## Purpose
定义本人退票影响查询、原子退票、结果恢复与替代场次查询的规则。
## Requirements
### Requirement: 本人退票影响查询

系统 SHALL 提供`POST /api/v1/orders/{orderNo}/refund-confirmation`，只读取当前用户的订单、电子票、场次和座位快照，不创建确认记录或改变交易状态。本人订单仅在`PAID`、电子票为`VALID`且原场次尚未开场时可退；退款金额 SHALL 使用订单权威`totalAmount`。

#### Scenario: 查询可退订单影响

- GIVEN 本人订单为`PAID`、电子票为`VALID`且`show.start_time > now`
- WHEN 查询退票影响
- THEN 返回`orderId/orderNo/refundAmount/orderStatus/ticketStatus/showStartTime/orderVersion/ticketVersion/impactText`
- AND 金额为两位小数字符串，业务ID为字符串，查询不写数据库

#### Scenario: 订单不可退

- GIVEN 订单不是`PAID`、电子票不是`VALID`或场次已经开场
- WHEN 查询退票影响
- THEN 返回HTTP 409和`205006`
- AND 不创建退款记录，不改变订单、电子票或座位

#### Scenario: 跨用户查询影响

- GIVEN 订单属于用户A
- WHEN 用户B查询该订单退票影响
- THEN 返回HTTP 404和`205001`
- AND 不泄露订单是否存在

### Requirement: 固定页面确认后的原子模拟退票

系统 SHALL 提供`POST /api/v1/orders/{orderNo}/refunds`。接口要求Header `Idempotency-Key`和Body `refundReason/clientRequestId/actionId?`；传统页面二次确认时`actionId`省略。Agent路径 SHALL 在未来的Tool Adapter前置校验B的确认凭证，当前REST不得伪造、存储或绕过B的确认结果。

#### Scenario: 成功退票

- GIVEN 本人订单仍为`PAID`、电子票仍为`VALID`、全部订单座位仍为`SOLD`且原场次尚未开场
- WHEN 用户在固定页面确认后使用稳定幂等键发起退票
- THEN 同一事务创建唯一退款记录并完成`REQUESTED -> PROCESSING -> SUCCESS`
- AND 订单完成`PAID -> REFUNDING -> REFUNDED`并写入`refundedTime`
- AND 电子票完成`VALID -> REFUNDED`并写入失效时间
- AND 仅订单明细引用且仍为`SOLD`的座位恢复为`AVAILABLE`
- AND 返回`refundId/refundNo/orderId/orderNo/refundStatus/refundAmount/orderStatus/ticketStatus/stateVersion/updatedAt`

#### Scenario: Agent确认不能由REST冒充

- GIVEN 请求包含非空`actionId`
- WHEN 直接调用传统页面REST退票接口
- THEN 返回HTTP 422和`205004`
- AND 不写入退款、订单、电子票或座位

#### Scenario: 任一权威状态不完整

- GIVEN 订单仍显示`PAID`，但电子票不是`VALID`或任一订单座位不再是`SOLD`
- WHEN 发起退票
- THEN 整个事务失败并回滚
- AND 不提交退款记录、`REFUNDING/REFUNDED`、票失效或部分座位释放

### Requirement: 退票幂等、并发和结果恢复

系统 SHALL 通过每订单唯一退款和`userId + Idempotency-Key`唯一约束保证安全重放。退款事务 SHALL 锁定本人订单行；同一幂等键的参数一致性 SHALL 使用`orderId/clientRequestId/refundReason/actionId`的确定性快照校验。

#### Scenario: 重复退票同一订单

- GIVEN 订单已经成功退票
- WHEN 使用原幂等键和相同参数，或使用新的请求键再次退票同一订单
- THEN 返回原退款记录和最新订单、电子票终态
- AND 不创建第二条退款、不重复失效电子票或释放座位

#### Scenario: 同一退票键参数不一致

- GIVEN 某退票幂等键已经绑定一组请求参数
- WHEN 使用该键退票另一订单或改变`clientRequestId/refundReason/actionId`
- THEN 返回HTTP 409和`205005`
- AND 目标订单、电子票和座位均不变

#### Scenario: 两个退票请求竞争

- GIVEN 一个可退的`PAID`订单
- WHEN 两个请求并发发起退票
- THEN 最多创建一条`SUCCESS`退款记录
- AND 最终订单为`REFUNDED`、电子票为`REFUNDED`、座位为`AVAILABLE`

#### Scenario: 退票响应丢失

- GIVEN 退票事务已经提交但调用方未收到响应
- WHEN 当前用户调用`GET /api/v1/orders/{orderNo}/refund`
- THEN 返回原`refundNo`及最新订单、电子票终态
- AND 查询不创建或更新任何交易记录

### Requirement: 本人替代场次查询

系统 SHALL 提供`GET /api/v1/orders/{orderNo}/alternative-shows`，按本人原订单的影片查询指定日期范围内其他`ON_SALE`且`start_time > now`的场次。日期范围最多七天；空结果 SHALL 返回空数组，不伪造场次。

#### Scenario: 查询同影片替代场次

- GIVEN 本人订单关联影片仍有其他未来可售场次
- WHEN 使用合法`dateFrom/dateTo`查询替代场次
- THEN 返回按开场时间和场次ID升序排列的`showId/cinemaId/startTime/basePrice/status/availableSeatCount`
- AND 不返回原订单场次、停售场次、已开场场次或其他影片场次

#### Scenario: 没有替代场次

- GIVEN 日期范围内没有其他未来可售场次
- WHEN 查询替代场次
- THEN 返回成功和空数组
- AND 既有退票结果保持成功

### Requirement: 复用现有交易表

退票链路 SHALL 复用V003已有`refund_request`、`ticket_order.refunded_time`、`electronic_ticket`和订单座位快照。本阶段 SHALL NOT 新增或修改Flyway脚本，也 SHALL NOT 占用任何预留迁移版本。

#### Scenario: 退票实现不改变Schema

- GIVEN V003和V005已经在共享数据库执行
- WHEN 实现并部署本阶段退票链路
- THEN Flyway迁移目录和历史checksum保持不变
- AND 应用只使用现有字段、JSON快照及唯一约束完成退票
