# electronic-ticket-show-end-invalidation Specification

## Purpose
TBD - created by archiving change electronic-ticket-show-end-invalidation. Update Purpose after archive.
## Requirements
### Requirement: 场次结束时自动使有效电子票失效

系统 SHALL 在 `movie_show.end_time <= 当前业务时间` 时，将关联已支付订单的 `VALID` 电子票迁移为 `INVALIDATED`，并写入 `invalidated_time` 与 `invalidation_reason=SHOW_ENDED`。业务时间 SHALL 由注入的 `Clock` 取得。

#### Scenario: 结束边界

- **GIVEN** 已支付订单关联一张 `VALID` 电子票，场次 `end_time` 等于当前业务时间
- **WHEN** 电子票结束失效任务执行
- **THEN** 电子票状态为 `INVALIDATED`，其 `invalidated_time` 等于当前业务时间、`invalidation_reason=SHOW_ENDED` 且版本仅增加一次

#### Scenario: 尚未结束

- **GIVEN** 已支付订单关联一张 `VALID` 电子票，场次结束时间晚于当前业务时间
- **WHEN** 电子票结束失效任务执行
- **THEN** 电子票保持 `VALID`

### Requirement: 自动失效不改变交易与出行语义

系统 SHALL 仅更新满足条件的有效电子票。它不得改变已支付订单状态、不得释放已售座位、不得创建退款记录，且不得发布退款专用 `OrderInvalidated` 或支付成功事件。

#### Scenario: 已结束的已支付票据

- **GIVEN** 电影已结束且订单为 `PAID`、座位为 `SOLD`
- **WHEN** 自动失效完成
- **THEN** 订单仍为 `PAID`，座位仍为 `SOLD`，不存在由该任务产生的退款或出行取消事件

### Requirement: 重复和并发调度安全

系统 SHALL 使用数据库条件更新使任务可重复执行。单轮候选扫描 SHALL 有批量上限，单条失败不得阻止其他候选下次被处理。

#### Scenario: 两次任务竞争同一电子票

- **GIVEN** 两个任务实例同时扫描同一张满足条件的 `VALID` 电子票
- **WHEN** 两者尝试失效该电子票
- **THEN** 至多一个条件更新成功，电子票版本只增加一次

### Requirement: 失效票展示结束原因

电子票页面 SHALL 根据服务端的 `invalidationReason` 展示原因，且不得对失效票渲染可用二维码。

#### Scenario: 用户查看自动失效电子票

- **GIVEN** 电子票查询结果状态为 `INVALIDATED` 且 `invalidationReason=SHOW_ENDED`
- **WHEN** 用户打开电子票页面
- **THEN** 页面展示结束失效原因及不可用提示，不展示有效电子票二维码

#### Scenario: 管理员异常作废票据

- **GIVEN** 电子票查询结果状态为 `INVALIDATED` 且 `invalidationReason=ADMIN_INVALIDATED`
- **WHEN** 用户打开电子票页面
- **THEN** 页面只展示通用“电子票已失效”提示，不得推断为影片结束

