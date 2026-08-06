## Purpose

定义 C 可直接消费的类型化出行建议响应和固定示例。

## ADDED Requirements

### Requirement: 建议接口必须返回类型化响应

系统 SHALL 对 `GET /api/v1/travel/tasks/{taskId}/advice` 和 `POST /api/v1/travel/tasks/{taskId}/advice/refresh` 返回 `TravelAdviceResponse`：`available`、`taskId`、`taskStatus`、可空 `weather`、`advice[]`、`source`、`dataAt`、`expiresAt`、`isExpired`、`degraded`、`fallbackType`。兼容字段 `weatherJson/adviceJson` 不得成为新页面依赖。

#### Scenario: 正常天气建议

- **GIVEN** 有效天气快照和通用交通建议
- **WHEN** 当前用户查询建议
- **THEN** `weather` 为对象，`advice` 为类型化数组，并返回来源和完整时间字段

#### Scenario: 天气不可用但仍有交通建议

- **GIVEN** 天气 Provider、缓存和 Demo 均不可用
- **WHEN** 系统返回建议
- **THEN** `weather=null`
- **AND** `advice` 仍包含通用交通建议，且 `degraded=true` 并说明 `fallbackType`

#### Scenario: Demo 天气降级

- **GIVEN** 使用版本化 Demo 天气
- **WHEN** 页面读取建议
- **THEN** `source` 明确标识 Demo，`degraded=true` 或按快照规则返回降级标识
- **AND** 不声称为实时事实

#### Scenario: 已过期建议

- **GIVEN** 快照已过期或任务已取消
- **WHEN** 页面读取已有建议
- **THEN** 仍返回快照内容且 `isExpired=true`
- **AND** 不生成新快照

#### Scenario: 尚未生成建议

- **GIVEN** 任务没有建议快照
- **WHEN** 页面读取建议
- **THEN** 返回 `available=false`、`weather=null`、`advice=[]`

### Requirement: 建议查询必须保护本人范围

系统 SHALL 使用 `CurrentUserAccessor` 校验任务归属；不存在或非本人统一返回 HTTP 404/`207001`，不泄露任务或快照内容。

#### Scenario: 查询他人建议

- **WHEN** 当前用户查询他人的任务建议
- **THEN** 返回 HTTP 404/`207001`，且响应不包含任务或快照字段

### Requirement: 固定夹具和 OpenAPI 示例必须覆盖页面状态

D SHALL 提供正常、天气不可用、Demo 降级、过期、尚未生成五类建议夹具，并提供 `207001`、`207002`、`207003`、`107001`、`207004` 的错误示例。路线、餐饮和邮件提醒不属于本期建议 DTO。

#### Scenario: C 使用固定夹具

- **WHEN** C 按 OpenAPI 示例加载页面状态
- **THEN** 五类建议和五类错误均能由固定 JSON 表达
- **AND** 页面不需要解析 `weatherJson` 或 `adviceJson`
