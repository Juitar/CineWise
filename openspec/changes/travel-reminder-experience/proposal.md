## Why

支付后的电子票目前没有可恢复的出行提醒任务，天气、通用交通建议、基础路线和周边餐饮也没有统一的离线演示与失败处理方式。PRD 要求第 5 天交付轻量出行和工具失败兜底；本变更把 D 负责的任务、建议、提醒和查询能力定为可实现、可联调的范围。

## What Changes

- 在支付事务提交后的 `PaymentSucceededEvent` 消费端，按事件和订单双重幂等创建或恢复 `travel_task`；A 已确认 `PaymentSucceededEvent`、`OrderInvalidated` 增加 `String cinemaId`，A 只产生完整匹配 `^[1-9][0-9]*$` 且不超过 Java `long` 正数上限 `9223372036854775807` 的值，D 仍做防御校验后写入拟由 V013 增加的 `travel_task.cinema_id`；订单失效时按版本取消任务。
- 提供天气、确定性通用交通建议、建议快照和 EMAIL 提醒调度；外部数据失败时明确使用缓存、版本化 Demo 或省略天气事实，不影响电子票展示。
- 提供用户主动发起的单条基础路线和简单周边餐饮查询；路线仅使用一次性位置或手动地点，不保存精确位置和路线几何。
- 建立 `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 的迁移、任务状态、通知恢复、Mock 与回归测试方案。
- 明确 A、C、B 的协作边界：A 在交易事务内登记事件、由 D 在提交后消费，并每 5 分钟分别扫描最近 24 小时的 `PAID`、`REFUNDED` 订单进行补偿；C 提供公共邮件端口和路线展示，B 只能读取任务或建议摘要，不通过对话创建、刷新或发送提醒。

## Capabilities

### New Capabilities

- `travel-task-reminder`: 支付后出行任务、建议快照与 EMAIL 提醒的创建、取消、调度、幂等和结果恢复。
- `travel-advice-query`: 天气、通用交通建议、基础路线和简单周边餐饮的只读查询、来源时效、隐私和降级规则。

### Modified Capabilities

- 无。

## Impact

- 代码范围：`backend` 下新增 `travel` 模块的 api、application、domain、infrastructure 和对应测试；D 的 Provider、缓存、定时任务、Demo 资源和回归清单。
- 数据范围：在已发布 V007 的 `travel_task` 上，以 V013 增加 `cinema_id BIGINT NULL`。该字段不建物理外键、不设默认值、不回填历史任务，并使用 `CHECK (cinema_id IS NULL OR cinema_id > 0)`；历史任务和退款先到且影院 ID 非法的取消墓碑可保留 `NULL`，路线查询明确不可用。
- 跨模块：A 的 `PaymentSucceededEvent`、`OrderInvalidated` 和补偿调用；C 的 `EmailDeliveryPort`、当前用户和路线展示；B 的 `ToolContext`、`ToolResult<T>` 与只读工具注册。
- 外部依赖：天气、餐饮 POI、高德路线服务未确认时使用版本化 Demo Provider；路线的精确起点与几何不写 MySQL、Redis、日志、画像、快照、URL 或 Agent 轨迹。
