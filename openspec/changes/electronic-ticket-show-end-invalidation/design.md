# 实现设计

## 权威状态与边界

`movie_show.end_time` 是场次结束的唯一判定依据；业务时间由 A 的 `Clock` 以 `Asia/Shanghai` 转换，边界使用 `end_time <= now`。本 change 只迁移 `electronic_ticket`：

```text
VALID -> INVALIDATED
```

订单保留 `PAID`，座位保留 `SOLD`，不创建退款记录，也不触发退款专用的 `OrderInvalidated`。后者会取消 D 的出行任务，语义不适用于“影片结束”。

`INVALIDATED` 不是用户提示原因。V022 新增 `invalidation_reason`：自动任务固定写入 `SHOW_ENDED`；已有设计中的管理员异常作废使用 `ADMIN_INVALIDATED`。API 原样返回该字段，页面仅在值为 `SHOW_ENDED` 时显示影片结束提示，其他失效票显示通用文案。`REFUNDED` 票不使用该字段。

## 分层与并发

Job 只创建 traceId 并调用 Application Service。Application Service 每轮最多读取配置批量的候选票；每条候选由独立事务处理，单条失败不会中断整批。

Repository 以 `electronic_ticket.status = VALID`、电子票版本、订单 `PAID` 和场次结束时间作为一次条件更新的谓词。候选查询只用于有界缩小范围，条件更新才是并发及重复执行的最终防线。重复任务或两个实例竞争时，最多一个更新影响一行；其余调用得到 skipped。

候选按 `end_time, show_id` 排序并固定 `LIMIT`。查询以 `movie_show` 为驱动表并使用 MySQL `STRAIGHT_JOIN`，每轮始终从最早仍有效候选开始，不保存内存游标，服务重启后可继续补扫。V022 增加 `movie_show(end_time, id)` 与 `electronic_ticket(status, order_id, id)`，使 MySQL 从结束时间索引进入候选且不产生临时表/文件排序；真实 MySQL `EXPLAIN` 是迁移验证门。

## 配置、恢复与测试

新增可停用的任务配置：默认每 60 秒、每批最多 100 条。H2 `test` profile 固定在 V009，因而只验证旧列兼容；V022 状态、索引和执行计划由 MySQL 集成/验证库验证。

测试覆盖未结束、结束边界、已退款/已失效、重复与并发、订单和座位保持不变；前端覆盖失效原因和二维码隐藏。任务没有事件发布依赖，测试同时断言不会产生退款记录。
