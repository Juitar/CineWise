# order-lifecycle Specification

## Purpose
定义本人订单查询、取消、过期释放及幂等恢复的订单生命周期规则，确保订单状态变化、座位释放与重复请求处理均以交易权威数据为准。
## Requirements
### Requirement: 本人订单列表与详情

系统 SHALL 提供`GET /api/v1/orders`和`GET /api/v1/orders/{orderNo}`。列表 SHALL 按当前用户过滤，支持`orderNo/status/dateFrom/dateTo/page/size`筛选，按创建时间和主键倒序返回有界分页。详情 SHALL 返回订单权威状态及座位快照。

#### Scenario: 查询本人订单

- GIVEN 已登录用户存在多个不同状态的订单
- WHEN 按合法状态、日期和分页条件查询
- THEN 只返回当前用户且满足条件的订单
- AND `orderId/showId/seatIds`为字符串，金额为两位小数字符串，时间为ISO 8601

#### Scenario: 跨用户查询订单

- GIVEN 订单属于用户A
- WHEN 用户B使用该`orderNo`查询详情
- THEN 返回HTTP 404和`205001`
- AND 不泄露订单是否存在

### Requirement: 取消动作幂等记录

系统 SHALL 通过新的前向Flyway迁移创建`ticket_order_operation`，保存`user_id/action/idempotency_key/order_id/order_no_snapshot/parameter_hash/result_status/result_version/create_time/update_time`。`user_id + action + idempotency_key` SHALL 唯一，不建立物理外键。

#### Scenario: 同一取消键重放

- GIVEN 用户已使用某`Idempotency-Key`取消一个订单
- WHEN 使用相同键取消相同订单
- THEN 返回原已确定的`CANCELLED`结果
- AND 不重复改变订单或释放座位

#### Scenario: 同一取消键用于不同订单

- GIVEN 用户的取消幂等键已绑定订单A
- WHEN 使用该键取消订单B
- THEN 返回HTTP 409和`205005`
- AND 订单B及其座位不变

### Requirement: 幂等取消待支付订单

系统 SHALL 提供`POST /api/v1/orders/{orderNo}/cancel`，要求登录、本人订单和Header `Idempotency-Key`。取消事务 SHALL 完成`PENDING_PAYMENT -> CANCELLED`、写入幂等操作结果，并只释放`status=LOCKED AND lock_order_no=当前订单号`的座位。

#### Scenario: 成功取消待支付订单

- GIVEN 当前用户的订单为`PENDING_PAYMENT`且其座位仍属于该订单
- WHEN 用稳定幂等键取消
- THEN 订单为`CANCELLED`，`cancelled_time`和版本已更新
- AND 该订单仍为`LOCKED`的座位恢复`AVAILABLE`并清除锁归属

#### Scenario: 取消非待支付订单

- GIVEN 本人订单为`PAYING/PAID/EXPIRED/REFUNDING/REFUNDED`
- WHEN 尝试取消
- THEN 返回HTTP 409和`205002`
- AND 订单、座位和操作记录均不变

#### Scenario: 旧订单不得释放新归属

- GIVEN 订单A的座位已经不再以订单A作为`lock_order_no`
- WHEN 取消或过期订单A
- THEN 不得改变该座位的当前状态或归属
- AND 交易不得以部分释放状态提交

### Requirement: 可重复的订单过期释放

系统 SHALL 每30秒触发`ExpiredOrderReleaseJob`，每批最多扫描100个`status=PENDING_PAYMENT AND expire_time <= now`订单。Job SHALL 只调用Application Service，每个订单在独立事务中条件更新为`EXPIRED`并安全释放座位。

#### Scenario: 重复或并发处理过期订单

- GIVEN 一个待支付订单的`expire_time <= now`
- WHEN 两个任务并发处理且后续再次执行
- THEN 最多一次条件更新成功，最终订单为`EXPIRED`
- AND 座位只被安全释放一次，后续执行无额外修改

#### Scenario: 取消与过期竞争

- GIVEN 一个刚好到期的`PENDING_PAYMENT`订单
- WHEN 用户取消与过期任务并发执行
- THEN 仅`CANCELLED`或`EXPIRED`一个终态成立
- AND 座位为`AVAILABLE`，不存在部分事务结果

#### Scenario: 支付中或已支付订单不过期

- GIVEN 订单已为`PAYING`或`PAID`
- WHEN 过期任务扫描
- THEN 不修改订单状态
- AND 不释放`LOCKED`或`SOLD`座位
