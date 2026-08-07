# 支付成功事件补充电影上下文

## Why

画像模块需要在支付成功事件中获得 A 权威场次对应的电影 ID。当前事件只有 `showId`，D 无法安全确认电影并生成电影类型画像；D 已确认 `movieId` 可为空，缺失时仍记录购票行为但不生成电影类型标签。

## What Changes

- 扩展 A 的 `ShowContextView`，增加可空内部字段 `Long movieId`。
- 沿场次查询 Repository、MyBatis 映射、支付实时发布和支付对账补偿链路传递 `movieId`。
- 扩展 `PaymentSucceededEvent`，对外使用可空正十进制字符串 `movieId`。
- 同步支付事件测试、夹具和重复/补偿语义。

## Non-Goals

- 不修改 `OrderInvalidated` 或退票画像行为。
- 不由 A 查询电影名称、电影类型或生成画像标签。
- 不修改 D 的内容查询和画像算法，不新增数据库迁移。

## Owner and compatibility

- Owner：A；受影响消费者：D、现有出行任务消费者。
- D 已确认 `movieId=null` 时仍记录 `PAID_ORDER`，不猜测、不补采、不阻断支付。
- 事件新增字段保持记录构造顺序契约；现有出行消费者继续使用已有字段。
