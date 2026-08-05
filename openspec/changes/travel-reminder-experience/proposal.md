## Why

支付后的电子票目前没有可恢复的出行提醒任务和天气提醒。MVP 除影院区域天气、天气相关提醒和通用出发建议外，还需要让用户主动查询前往已购票影院的步行、骑行和公交路线；周边餐饮不进入本版本。

## What Changes

- 在支付事务提交后的 `PaymentSucceededEvent` 消费端，按事件和订单双重幂等创建或恢复 `travel_task`；A 在 `PaymentSucceededEvent`、`OrderInvalidated` 中同时提供稳定影院业务 ID `cinemaId`，D 写入 V013 新增的 `travel_task.cinema_id`；订单失效时按版本取消任务。
- 提供天气、确定性通用交通建议、建议快照和 EMAIL 提醒调度；在 Provider、授权、配额和密钥确认后接入真实天气数据，外部数据失败时明确使用缓存、版本化 Demo 或省略天气事实，不影响电子票展示。
- 用户可先跳过路线规划，之后在出行建议页主动发起请求；确认位置共享说明并授权浏览器提供本次位置后，页面只展示步行、骑行和公交三张方式卡，不自动请求路线或地图。用户选择其中一种并提交本次坐标、方式和任务版本后，系统返回该路线摘要、静态地图和分段指引；不比较三种方案，也不保存用户位置、路线折线或地图图片。
- 不提供周边餐饮、持续定位、常用地点、驾车路线、实时路线刷新或 Agent 对话中的位置收集。
- 建立 `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 的迁移、任务状态、通知恢复、Mock 与回归测试方案。
- 明确 A、C、B 的协作边界：A 在交易事务内登记事件、由 D 在提交后消费，并每 5 分钟分别扫描最近 24 小时的 `PAID`、`REFUNDED` 订单进行补偿；A、D 确认 `cinemaId` 进入出行任务以定位影院终点；C 负责位置授权和路线展示；B 只能读取任务或建议摘要，不通过对话创建、刷新、发送提醒或收集位置。

## Capabilities

### New Capabilities

- `travel-task-reminder`: 支付后出行任务、建议快照与 EMAIL 提醒的创建、取消、调度、幂等和结果恢复。
- `travel-advice-query`: 天气、通用出发建议，以及用户主动发起的步行、骑行、公交路线查询、静态地图、来源时效和降级规则。

### Modified Capabilities

- 无。

## Impact

- 代码范围：`backend` 下 `travel` 模块的 api、application、domain、infrastructure 和对应测试；D 的天气 Provider、路线 Provider、静态地图代理、缓存、定时任务、Demo 资源和回归清单。周边餐饮不作为本 change 的交付范围。
- 数据范围：V013 在已发布 V007 的 `travel_task` 增加 `cinema_id BIGINT NULL`，不建物理外键、不回填历史行。新事件创建的任务必须写入非空影院 ID；历史任务保持 `NULL`，路线查询明确返回不可用。三张出行表不保存精确起点、路线折线、途经点或静态地图图片；D 不修改已发布迁移。
- 跨模块：A 的 `PaymentSucceededEvent`、`OrderInvalidated`、补偿调用和 `cinemaId`；C 的 `EmailDeliveryPort`、当前位置授权与路线页面；B 的 `ToolContext`、`ToolResult<T>` 与只读工具注册。
- 外部依赖：真实天气和高德路线/静态地图 Provider 必须先确认授权、密钥、配额、超时和启用开关；确认前使用版本化 Demo 天气，路线明确返回不可用。
