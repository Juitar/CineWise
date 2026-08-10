# travel-route-http-api Specification

## Purpose
TBD - created by archiving change travel-route-http-api. Update Purpose after archive.
## Requirements
### Requirement: 浏览器可按本人出行任务请求一次性路线

系统 MUST 提供 `POST /api/v1/travel/tasks/{taskId}/route`。路径只接收任务 ID；请求体只包含 JSON 数值 `longitude`、`latitude`、`travelMode` 和 `thirdPartySharingConfirmed`。

#### Scenario: 驾车或步行路线成功

- **WHEN** 当前用户对有效本人任务确认本次位置共享，并提交合法坐标和 `DRIVING` 或 `WALKING`
- **THEN** 系统返回 `BasicRouteResult` 摘要，且不返回任何坐标、地址或路线折线

#### Scenario: 位置共享未确认

- **WHEN** `thirdPartySharingConfirmed` 缺失或为 `false`
- **THEN** 系统返回 HTTP 422 和 `107002`

#### Scenario: 任务不存在或不属于当前用户

- **WHEN** `taskId` 不存在或不属于当前用户
- **THEN** 系统返回 HTTP 404 和 `207001`，且不调用路线 Provider

#### Scenario: 路线不可用

- **WHEN** 坐标非法、影院无合法坐标、方式不支持或路线 Provider 不可用
- **THEN** 系统返回 HTTP 503 和 `307001`，且不得在响应或日志中泄露坐标

### Requirement: 精确坐标只用于本次路线请求

系统 MUST 先校验原始经度在 `[-180, 180]`、原始纬度在 `[-90, 90]`，合法后统一四舍五入到 6 位小数。坐标不得写入数据库、缓存、URL、日志、埋点、Mock、Agent 消息或 SSE；接口不得自动重试。

#### Scenario: 路线请求完成或失败

- **WHEN** 路线 Provider 返回、超时或抛出异常
- **THEN** 系统只返回路线摘要或标准错误，不保留或回显本次精确坐标

