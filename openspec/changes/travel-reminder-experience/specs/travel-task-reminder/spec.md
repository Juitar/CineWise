## Purpose

定义 D 出行任务的本人查询、详情聚合和刷新错误边界。

## ADDED Requirements

### Requirement: 任务详情必须返回公开聚合 DTO

系统 SHALL 对 `GET /api/v1/travel/tasks/{taskId}` 返回 `taskId`、`status`、`triggerAt`、`version`，以及 `order`、`movie`、`cinema` 三个摘要对象。订单包含 `orderId/orderNo/showId/showStartTime`；影片包含 `movieId/title/posterUrl/source/dataAt`；影院包含 `cinemaId/name/area/address/source/dataAt/expiresAt/isExpired`。

#### Scenario: 本人读取成功详情

- **GIVEN** 任务属于当前用户且 A、影片、影院摘要可用
- **WHEN** 当前用户查询任务详情
- **THEN** 返回 HTTP 200 和完整聚合 DTO
- **AND** 不返回价格、座位、库存、支付、退款、邮箱、精确位置或路线几何

#### Scenario: 任务不存在或不属于本人

- **WHEN** 当前用户查询不存在或他人的任务
- **THEN** 返回 HTTP 404，错误码为 `207001`

#### Scenario: 已取消任务仍可读取

- **GIVEN** 任务状态为 `CANCELLED`
- **WHEN** 当前用户查询详情
- **THEN** 返回 HTTP 200 且 `status=CANCELLED`
- **AND** 不返回 `207002`

#### Scenario: 订单或内容摘要不可用

- **GIVEN** A 订单摘要、影片摘要或影院摘要不可用
- **WHEN** 当前用户查询详情
- **THEN** 返回 HTTP 503 和 D 错误码 `207004`
- **AND** 不透传 A/D 内部错误码，也不补造资料

### Requirement: 刷新必须使用固定错误码

系统 SHALL 对 `POST /api/v1/travel/tasks/{taskId}/advice/refresh` 保持 `207002`/`207003`/`107001` 的 HTTP 映射：取消任务 409、不可刷新状态或版本冲突 409、五分钟内重复刷新 429。

#### Scenario: 刷新已取消任务

- **WHEN** 当前用户刷新 `CANCELLED` 任务
- **THEN** 返回 HTTP 409/`207002`，且不生成新快照

#### Scenario: 状态或版本不可刷新

- **WHEN** 任务不在允许刷新状态或版本条件不满足
- **THEN** 返回 HTTP 409/`207003`

#### Scenario: 五分钟内重复刷新

- **WHEN** 距上次刷新不足五分钟
- **THEN** 返回 HTTP 429/`107001`
