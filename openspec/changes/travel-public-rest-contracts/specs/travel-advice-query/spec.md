## Purpose

定义 C 可直接消费的类型化建议响应和降级含义。

## MODIFIED Requirements

### Requirement: 类型化建议必须与天气可用性一致

系统 SHALL 返回 `available/taskId/taskStatus/weather/advice/source/dataAt/expiresAt/isExpired/degraded/fallbackType`。天气不可用时 `weather=null` 且 `advice` 只保留 `TRANSPORT`；有 Demo 天气对象时可同时返回 `WEATHER` 和 `TRANSPORT`，并用来源和降级字段说明不是实时事实。

#### Scenario: 天气不可用

- **GIVEN** 快照没有可靠天气对象
- **WHEN** 用户查询建议
- **THEN** `weather=null`、`advice` 仅含 `TRANSPORT`、`degraded=true`

#### Scenario: Demo 天气

- **GIVEN** 快照使用版本化 Demo 天气
- **WHEN** 用户查询建议
- **THEN** 返回类型化天气和 `WEATHER`、`TRANSPORT` 建议
- **AND** `source` 和 `fallbackType` 明确为 Demo 降级

### Requirement: 固定夹具必须由 MVC 响应验证

D SHALL 使用 MockMvc 覆盖正常、天气不可用、Demo、过期和未生成建议五类夹具；C 不解析 `weatherJson/adviceJson`。

#### Scenario: C 按夹具开发页面

- **WHEN** D 校验五类固定夹具
- **THEN** MVC 响应的类型化字段与夹具一致
