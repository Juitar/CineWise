## Purpose

为已支付用户提供可追溯的天气和通用交通建议，并在用户主动操作时安全提供一条基础路线；外部服务不可用时不影响购票主流程。

## ADDED Requirements

### Requirement: 动态出行数据必须标明来源、时效和降级

系统 SHALL 为天气和路线响应提供 `source`、`dataTime`、`expiresAt`、`isExpired`、`degraded` 和 `fallbackType`。天气 Provider 未确定、超时或失败时 MUST 使用有效缓存或版本化 Demo 数据；路线 Provider 未配置、超时或失败时只能返回明确标记的版本化 Demo 路线或 `307001`，不得冒充实时结果。

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

### Requirement: 已配置时按影院区域查询高德实时天气

系统 SHALL 在配置有效的高德 Web 服务 `key` 且影院存在已登记行政区码时，调用高德天气查询接口获取实况天气。行政区码优先使用本地影院区域配置，其次使用本地影院城市和行政区码配置；不得根据模糊影院地址、用户精确位置、用户地址或路线数据猜测。密钥不得写入日志、快照或响应。成功结果 MUST 标记 `source=AMAP_WEATHER`、`degraded=false`，并根据天气状况返回出行提醒建议。

#### Scenario: 高德实况天气查询成功
- **GIVEN** 高德天气开关已开启、`key` 已配置，且影院区域有对应行政区码
- **WHEN** 系统为有效出行任务生成天气建议
- **THEN** 系统使用该行政区码查询高德天气接口，并返回天气状况、气温、天气提醒和来源时效字段
- **AND** 返回的 `source` 为 `AMAP_WEATHER`，`degraded=false`

#### Scenario: 高德配置或查询不可用
- **GIVEN** 高德 `key` 缺失、影院没有行政区码映射，或高德接口返回失败结果
- **WHEN** 系统查询天气建议
- **THEN** 系统继续按有效缓存、版本化 Demo、明确不可用的顺序返回，并保留通用交通建议
- **AND** 系统不把 Demo 或缓存数据标记为高德实时天气

### Requirement: 出行建议查询必须返回类型化页面数据

`GET /api/v1/travel/tasks/{taskId}/advice` 和 `POST /api/v1/travel/tasks/{taskId}/advice/refresh` SHALL 返回类型化的 `TravelAdviceResponse`。前端不得解析 `weatherJson` 或 `adviceJson` 字符串；这两个旧字段在所有调用方迁移前仅作为兼容字段保留，C 的页面只消费以下类型化字段。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `available` | boolean | 尚未生成快照时为 `false`；其余情况为 `true`。 |
| `weather` | object 或 null | 有天气事实时返回 `area`、`condition`、`risk`；天气不可用时为 `null`。 |
| `advice` | array | 每项包含 `type` 和 `text`；`type` 仅为 `WEATHER` 或 `TRANSPORT`。有快照时至少返回一条 `TRANSPORT`。 |
| `source` | string 或 null | 天气/建议来源，例如 `AMAP_WEATHER`、`DEMO_WEATHER_V1` 或 `UNAVAILABLE`。 |
| `dataAt` | ISO-8601 时间或 null | 对外字段名固定为 `dataAt`，映射快照内部的 `dataTime`。 |
| `expiresAt` | ISO-8601 时间或 null | 建议快照的失效时间。 |
| `expired` | boolean | 快照失效或任务已取消时为 `true`。 |
| `degraded` | boolean | 使用缓存、Demo 或天气不可用处理时为 `true`。 |
| `fallbackType` | string 或 null | 降级类型；非降级结果为 `null`。 |

类型化字段不返回用户精确位置、路线折线、途经点、邮箱、密钥或 Provider 原始 JSON。

#### Scenario: 正常天气建议
- **GIVEN** 本人任务已有未过期的真实天气建议快照
- **WHEN** 用户查询建议
- **THEN** 返回非空 `weather`、至少一条 `WEATHER` 和一条 `TRANSPORT` 建议
- **AND** 返回 `source=AMAP_WEATHER`、`degraded=false`、`expired=false`

#### Scenario: 天气不可用但通用建议可用
- **GIVEN** 天气、缓存和 Demo 都不可用，但任务建议快照已生成
- **WHEN** 用户查询建议
- **THEN** 返回 `weather=null` 和至少一条 `TRANSPORT` 建议
- **AND** 返回 `source=UNAVAILABLE`、`degraded=true`，不得编造天气状况

#### Scenario: Demo 或缓存降级
- **GIVEN** 系统使用版本化 Demo 或有效缓存生成建议
- **WHEN** 用户查询建议
- **THEN** 返回来源、`dataAt`、`expiresAt`、`degraded=true` 和对应 `fallbackType`
- **AND** 前端可以据此展示“演示数据”或“非实时数据”，不得显示为高德实时天气

#### Scenario: 过期或取消任务的既有建议
- **GIVEN** 任务已取消，或建议快照超过 `expiresAt`
- **WHEN** 用户查询既有建议
- **THEN** 返回原有类型化 `weather` 和 `advice`
- **AND** 返回 `expired=true`，前端只读展示且不得自动刷新

#### Scenario: 本人校验与刷新异常
- **GIVEN** 用户查询或刷新非本人任务
- **WHEN** 系统校验任务归属
- **THEN** 返回 HTTP 404 和 `207001`

- **GIVEN** 用户刷新已取消任务、过期版本任务或五分钟内已刷新过的任务
- **WHEN** 系统请求 `POST /api/v1/travel/tasks/{taskId}/advice/refresh`
- **THEN** 分别返回 HTTP 409/`207002`、HTTP 409/`207003` 或 HTTP 429/`107001`

### Requirement: 页面实现必须有固定建议响应夹具

D SHALL 提供固定夹具和 OpenAPI 示例，覆盖正常天气、天气不可用、Demo 降级、过期建议和尚未生成建议五种响应。夹具的字段、来源、时间和错误码必须与 `TravelAdviceResponse` 一致；C 只依赖这些夹具和公开接口，不依赖数据库 JSON 字段或 D 的内部类。

#### Scenario: C 使用固定夹具开发页面
- **GIVEN** D 已提供五种建议响应夹具和对应 OpenAPI 示例
- **WHEN** C 实现出行建议页面
- **THEN** 页面只读取类型化 `weather`、`advice` 和来源时效字段
- **AND** 页面不解析 `weatherJson`、`adviceJson`，也不读取 D 的数据库或内部类

### Requirement: 高德真实路线只能由用户主动发起且不保存精确位置

系统 SHALL 仅在用户主动请求并确认第三方位置共享说明后，使用一次性设备位置或手动地点调用高德真实路线 Provider。设备位置必须已经由 C 完成授权；用户拒绝定位、定位超时或定位不可用时，只能由用户主动提交手动地点。请求使用一次性起点、影院终点和 `travelMode`；连接超时为 2 秒、读取超时为 5 秒。成功响应必须返回 `travelMode`、`durationMinutes`、`suggestedDepartureAt`、`source=AMAP_ROUTE`、`dataTime`、`expiresAt`、`degraded=false`。精确起点、路线折线和途经点 MUST 不写入 MySQL、Redis、日志、画像、建议快照、URL 或 Agent 轨迹，且不持续定位。

#### Scenario: 用户拒绝定位后使用手动地点
- **GIVEN** 浏览器定位被拒绝、超时或不可用
- **WHEN** 用户主动提交手动地点
- **THEN** 系统仅使用该次请求的地点生成基础路线
- **AND** 请求结束后不保留该地点

#### Scenario: 高德路线成功
- **GIVEN** 用户主动请求、已确认位置共享、定位已授权或已提交手动地点，且高德路线配置和影院终点可用
- **WHEN** D 查询高德真实路线
- **THEN** 返回 `source=AMAP_ROUTE`、`degraded=false` 和路线摘要
- **AND** C 仅在页面内存中使用本次响应的路线折线渲染地图

#### Scenario: 高德路线未配置、超时或失败
- **GIVEN** 用户已主动请求路线
- **WHEN** 路线开关关闭、Key 缺失、影院终点缺失、连接或读取超时、网络异常、高德非成功响应或返回字段不完整
- **THEN** 有 `DEMO_ROUTE_V1` 时返回 `source=DEMO_ROUTE_V1`、`degraded=true` 和 `fallbackType=DEMO_ROUTE`
- **AND** 无 Demo 时返回 HTTP 503/`307001`，继续展示影院地址和通用交通建议，且不生成文字路线替代结果

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
