## Purpose

为已支付用户提供可追溯的天气和通用交通建议，并在用户主动操作时安全提供一条基础路线和影院周边餐饮候选，外部服务不可用时不影响购票主流程。

## ADDED Requirements

### Requirement: 动态出行数据必须标明来源、时效和降级

系统 SHALL 为天气、路线和餐饮响应提供 `source`、`dataTime`、`expiresAt`、`isExpired`、`degraded` 和 `fallbackType`。外部 Provider 未确定、超时或失败时 MUST 使用有效缓存或版本化 Demo 数据；没有可用数据时 MUST 明确省略该事实，不得冒充实时结果。

#### Scenario: 返回 Demo 天气
- **GIVEN** 真实天气 Provider 不可用且版本化 Demo 数据可用
- **WHEN** 系统查询天气建议
- **THEN** 返回结果标识 Demo 来源和有效时间
- **AND** `degraded=true` 且不声称为实时天气

#### Scenario: 过期动态数据
- **GIVEN** 动态结果已超过 `expiresAt`
- **WHEN** 系统读取该结果
- **THEN** 结果标记 `isExpired=true` 并只作只读参考
- **AND** 系统不将其作为新的当前路线、天气或提醒事实

### Requirement: 基础路线只能由用户主动发起且不保存精确位置

系统 SHALL 仅在用户主动请求并确认第三方位置共享说明后，使用一次性设备位置或手动地点生成一条基础路线、预计耗时和预计出发时间。精确起点、路线折线和途经点 MUST 不写入 MySQL、Redis、日志、画像、建议快照、URL 或 Agent 轨迹，且不持续定位。

#### Scenario: 用户拒绝定位后使用手动地点
- **GIVEN** 浏览器定位被拒绝、超时或不可用
- **WHEN** 用户主动提交手动地点
- **THEN** 系统仅使用该次请求的地点生成基础路线
- **AND** 请求结束后不保留该地点

#### Scenario: 路线服务失败
- **GIVEN** 用户已主动请求路线
- **WHEN** 路线 Provider 或地图渲染失败
- **THEN** 系统返回路线服务暂不可用
- **AND** 继续展示影院地址和通用交通建议，且不生成文字路线替代结果

### Requirement: 简单周边餐饮查询必须受半径和业务范围限制

系统 SHALL 仅在用户主动查询时，按影院位置和受控 `radiusMeters` 返回基础餐饮 POI；未指定半径时使用服务端默认值，超出允许范围时返回稳定参数错误。结果 MUST 稳定排序并标明营业状态已知性、来源和时效。

#### Scenario: 餐饮半径越界
- **GIVEN** 用户提交的半径超出服务端允许范围
- **WHEN** 系统查询周边餐饮
- **THEN** 系统返回 `107003`
- **AND** 不调用 Provider、不修改任务或建议

#### Scenario: 餐饮结果为空或 Provider 超时
- **GIVEN** 查询没有候选或 Provider 不可用
- **WHEN** 用户主动查询周边餐饮
- **THEN** 系统返回空候选或明确降级标识
- **AND** 不读取长期画像、不按用餐时段判断，也不执行预订、排队、点餐或支付

### Requirement: 用户刷新建议必须受任务状态和频率限制

系统 SHALL 仅允许任务处于 `READY` 或 `NOTIFIED` 时刷新建议，且距上次刷新至少五分钟；任务已取消时 MUST 拒绝刷新。

#### Scenario: 高频刷新
- **GIVEN** 有效任务距上次刷新不足五分钟
- **WHEN** 用户请求刷新建议
- **THEN** 系统返回 `107001`
- **AND** 不调用外部 Provider 或覆盖现有快照

#### Scenario: 已取消任务刷新
- **GIVEN** 任务状态为 `CANCELLED`
- **WHEN** 用户请求刷新建议
- **THEN** 系统返回 `207002`
- **AND** 不生成新快照或提醒
