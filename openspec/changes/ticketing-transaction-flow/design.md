# Design: ticketing-transaction-flow

## 实现范围

交易表结构迁移已完成，本阶段在现有Schema上实现建单、查询、取消、过期释放、Mock支付和电子票。五张表分别承担：

| 表 | 权威职责 | 数据库最终约束 |
| --- | --- | --- |
| `ticket_order` | 订单金额、期限、幂等和状态 | 订单号唯一；用户+客户端请求唯一；用户+幂等键唯一；票数与金额CHECK |
| `ticket_order_seat` | 下单座位和单价快照 | 订单+场次座位唯一；单价非负；追加写后不修改业务快照 |
| `mock_payment` | 每订单唯一Mock支付 | 支付单号、订单、支付幂等键分别唯一；金额非负 |
| `electronic_ticket` | 支付成功后每订单唯一电子票 | 票码和订单分别唯一；按用户+状态查询 |
| `refund_request` | 每订单唯一模拟退票 | 退票号、订单、用户+幂等键分别唯一；按用户、actionId查询 |

`ticket_order_seat`属于长期保留的追加写快照。为符合团队表审计字段规范，除`create_time`外补充`update_time`；创建时两者相同，当前业务不提供修改快照的通用接口。

## 后续状态边界

迁移只提供状态列与约束，不在数据库存储过程或触发器中实现状态机。后续Application Service必须按照冻结状态机和同一本地事务执行条件更新；Mapper不得提供任意`updateStatus`入口。

```text
Seat:  AVAILABLE -> LOCKED -> SOLD
       LOCKED -> AVAILABLE (cancel/expiry)

Order: PENDING_PAYMENT -> PAYING -> PAID
       PENDING_PAYMENT -> CANCELLED | EXPIRED
       PAID -> REFUNDING -> REFUNDED

Ticket: VALID -> REFUNDED

Refund: REQUESTED -> PROCESSING -> SUCCESS
```

## 模块与事务边界

- `order/api`只解析字符串业务ID、校验DTO和映射`Result<T>`。
- `order/application`拥有建单用例与本地事务边界，只从`CurrentUserAccessor`取当前用户。
- `ticketing/application`提供场次校验和条件锁座能力；`order`不跨模块访问座位Mapper。
- `order/infrastructure`持久化订单和订单座位快照，不暴露Entity给Controller。
- 建单事务不包含Redis、HTTP、Agent或其他外部调用。

## 原子建单

`POST /api/v1/orders`接收Header `Idempotency-Key`及Body `showId/seatIds/clientRequestId`，不接收`userId`或金额。一次事务按以下固定顺序执行：

1. 按`userId + clientRequestId`或`userId + idempotencyKey`查询既有订单。
2. 已有订单时比较两个请求标识、`showId`和排序去重后的`seatIds`；相同返回原订单，不同返回`205005`。
3. 重新读取场次状态、开场时间和服务端价格，校验场次`ON_SALE`且未开场。
4. 校验1至6个不重复座位均属于该场次，按`seatId`升序执行带`show_id/status/version`条件的`AVAILABLE -> LOCKED`更新。
5. 任一条更新影响行数不为1时抛出`204001`，使已锁座位、订单和明细全部回滚。
6. 用同一`orderNo`写入座位锁、`PENDING_PAYMENT`订单和订单座位快照。单价取`movie_show.base_price`，`totalAmount = unitPrice * ticketCount`。

并发重复请求可能同时通过首次幂等查询。如条件锁座或唯一约束竞争失败，当前事务必须先回滚，再在新的只读事务中按原请求键恢复已提交结果。查到同参订单时返回原结果；查到异参订单时返回`205005`；仍无订单时才返回`204001`。

## 恢复与后续交易

- `GET /api/v1/orders/by-request/{clientRequestId}`必须按当前用户过滤，未找到返回`205001`。
- 取消和过期只释放`status=LOCKED AND lock_order_no=当前订单号`的座位。
- Mock支付只接收订单号和Header幂等键，业务请求体为空，后端任何层均不定义六位模拟密码字段。
- 支付在同一事务内保证唯一支付、`PAYING/PAID`订单、`SOLD`座位和唯一电子票；提交后发布`PaymentSucceededEvent`。
- HTTP响应未知时只能调用查询接口恢复，不自动生成新幂等键或重放写请求。

## 订单查询、取消与过期

`OrderQueryService`按`CurrentUserAccessor`返回本人列表、详情和恢复结果。列表的`status`只接受`OrderStatus`枚举白名单，日期使用Asia/Shanghai左闭右开区间，`size`不超过公共上限。持久化层先分页查订单，再一次批量查询本页座位快照，不对每个订单生成一次N+1查询。

V005新增A拥有的`ticket_order_operation`表，只保存交易写动作幂等关系和已确定结果，不存储JWT、确认凭证或其他模块数据。取消的`parameter_hash`由`action + orderId`确定性生成；同`userId + action + idempotencyKey`命中后，同参返回原结果，异参返回`205005`。

`OrderCancellationTransaction`首先按`userId + orderNo`锁定订单行，再校验幂等记录和状态。它在同一事务中执行带`PENDING_PAYMENT + version`条件的`CANCELLED`更新、写入操作记录，并通过`SeatReleaseService`只释放当前`orderNo`拥有的`LOCKED`座位。释放数与订单明细数不一致时抛出不可恢复的一致性异常并回滚，不提交半取消状态。

`OrderExpiryService`每批最多扫描100个候选主键，逐个调用独立的`OrderExpiryTransaction`。后者使用`id/status/expire_time/version`条件抢占`EXPIRED`迁移，成功后安全释放座位；条件失败表示另一取消、过期或支付流程已经取得权威结果，当前任务安全跳过。`ExpiredOrderReleaseJob`每30秒只调用该Application Service，不访问Mapper。

## Mock支付与电子票

V003已有`mock_payment`和`electronic_ticket`及每订单唯一约束，本阶段不新增或修改任何Flyway脚本，也不占用C暂定的V006。

`POST /api/v1/orders/{orderNo}/payments`只接收路径订单号和Header幂等键，请求体为空。`PaymentApplicationService`负责身份、输入校验和唯一约束竞争后的只读恢复；`PaymentTransaction`拥有本地事务并按以下顺序执行：

1. 按当前用户和订单号锁定订单行，使支付、取消和过期串行竞争。
2. 查询订单已有支付和当前幂等键绑定；同订单已有支付时返回原结果，同键绑定其他订单时返回`205005`。
3. 校验订单为`PENDING_PAYMENT`且`expire_time > now`，以订单金额作为唯一支付金额。
4. 写唯一`PROCESSING`支付并条件迁移订单到`PAYING`。
5. 仅将`status=LOCKED AND lock_order_no=当前订单号`的全部座位更新为`SOLD`并清除锁；影响行数必须等于订单票数。
6. 条件迁移支付到`SUCCESS`、订单到`PAID`，写`paid_time`并生成每订单唯一`VALID`电子票。
7. 事务提交后才允许发布`PaymentSucceededEvent`；HTTP响应和事件监听结果均不参与事务成功判定。

`GET /api/v1/orders/{orderNo}/payment`和`GET /api/v1/tickets/{ticketId}`只读权威数据并按当前用户过滤。支付POST结果未知时，调用方只能查询原订单支付结果，不能自动重放POST或生成新幂等键。

支付编号、票码和二维码载荷由雪花ID派生；二维码载荷只包含`cinewise:ticket:<ticketCode>`演示引用，不含用户身份、模拟密码或订单明细。

事件使用冻结字段`eventId/orderId/showId/userId/cinemaArea/startAt/orderVersion/occurredAt`。当前D公开内容摘要端口不包含`cinemaArea`，A只定义事件和发布端口，不读取D私有Mapper/表、不伪造区域；待D补齐公开Application API后接通事务后发布适配。

## 配置和时间

`TICKET_LOCK_MINUTES`与`ORDER_PAYMENT_MINUTES`默认15，范围5至30。为避免订单在座位锁失效后仍显示可支付，建单的座位锁与订单截止时间统一使用两个配置中的较早时间。所有时间通过注入`Clock`生成并按毫秒持久化。

## 模拟退票与替代场次

V003已有`refund_request`及每订单唯一、`user_id + idempotency_key`唯一约束，本阶段不新增或修改Flyway脚本。`impact_snapshot`保存退款金额、订单与票版本以及`clientRequestId/refundReason/actionId`的确定性请求快照，用于同键参数一致性校验和结果恢复，不存储JWT或B的确认内容。

`POST /api/v1/orders/{orderNo}/refund-confirmation`是固定页面的只读影响查询。它按当前用户聚合订单、电子票和原场次，只有`PAID + VALID + startTime > now`返回可退影响；退款金额始终取订单`totalAmount`。传统页面在本地展示二次确认后调用退票接口。非空`actionId`属于未来Agent Tool路径，当前REST直接拒绝，A不实现B的确认存储或校验。

`RefundTransaction`在同一本地事务中按固定顺序执行：

1. 按当前用户和订单号锁定订单行，使重复退票和其他订单状态写入串行竞争。
2. 查询当前幂等键及订单已有退款；原键异参或同键绑定其他订单返回`205005`，同订单已退款返回权威原结果。
3. 重新校验订单`PAID`、电子票`VALID`、原场次未开场，并以订单金额生成影响快照。
4. 写唯一`REQUESTED`退款，条件迁移订单到`REFUNDING`、退款到`PROCESSING`。
5. 条件迁移电子票`VALID -> REFUNDED`，仅释放订单明细引用且仍为`SOLD`的全部座位；影响行数必须等于订单票数。
6. 条件迁移退款到`SUCCESS`、订单到`REFUNDED`并写`refunded_time`。任一影响行数异常时抛出一致性异常，使整笔事务回滚。

`GET /api/v1/orders/{orderNo}/refund`只读恢复原退款结果。`GET /api/v1/orders/{orderNo}/alternative-shows`先校验本人订单，再通过`ticketing/application`查询同影片、排除原场次、未来且`ON_SALE`的场次；日期范围最多七天，空结果返回空数组。替代场次查询不属于退款事务，失败或空结果不得回滚已成功退票。

## 测试策略

- H2用于快速的REST、幂等和回滚回归。
- 并发条件更新必须在真实MySQL 8环境验证；H2结果不作为防超卖的唯一证据。
- 必测20请求抢同一座位、同键同参恢复、同键异参、多座位部分冲突全回滚、跨用户恢复不可见。
- 退票必须覆盖影响查询、同键同参/异参、跨用户、订单/票/座位归属回滚、并发重复退票和响应丢失恢复；真实MySQL验证同一订单最多一条成功退款。

## OpenAPI与联调夹具

联调夹具统一放在`backend/src/test/resources/fixtures/ticketing`，以版本库内静态示例作为A向B/C交付的单一来源。A负责`/shows`、选座、订单、支付、电子票、退票页面及其REST夹具；C负责`/movies/**`、`/cinemas/**`、购票入口和公共请求层，并仅通过该请求层接入A的票务REST。`c`目录保存完整`Result<T>`响应，覆盖场次、座位、订单、支付、电子票、退票和关键错误；`b`目录保存公共`ToolResult<T>`，覆盖动态场次、建单成功、原订单幂等恢复、座位冲突和订单不存在。

B夹具严格使用公共`status/data/errorCode/retryable/replanSuggested/suggestedNextAction/degraded/fallbackType/stateVersion/dataAt/expiresAt`字段，业务字段只放入`data`。支付不进入Agent工具夹具。写工具失败不得建议自动重试；原建单结果恢复返回与首次成功相同的业务数据，不增加`replayed`等未冻结字段。

C夹具严格使用当前Controller DTO，ID为字符串、金额为两位小数字符串、时间带偏移，错误码为JSON数值。夹具不得包含密码、JWT、Cookie、用户ID、确认凭证或真实环境数据。自动契约测试解析全部JSON，核对关键字段、敏感词和恢复语义，并通过实际`/v3/api-docs`核对安全声明、`Idempotency-Key`、请求体和响应Schema；夹具漂移或OpenAPI缺失时构建失败。

## 迁移策略

- 所有内部主键由应用分配BIGINT雪花ID，不使用`AUTO_INCREMENT`。
- 金额使用`DECIMAL(10,2)`，时间使用`DATETIME(3)`。
- 不建立物理外键；逻辑ID建立必要索引。
- 文件生成后先静态审查。A明确授权前不得连接数据库、启动Flyway或修改任何schema。
- 一旦迁移在共享数据库执行，后续只能新增向前迁移，不能修改历史文件。
