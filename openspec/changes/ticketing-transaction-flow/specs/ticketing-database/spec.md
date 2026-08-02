# Ticketing Database Spec

## ADDED Requirements

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
