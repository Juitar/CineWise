# agent-travel-advice-card Specification

## Purpose
TBD - created by archiving change agent-travel-advice-card. Update Purpose after archive.
## Requirements
### Requirement: 受控出行建议卡片 payload
系统 SHALL 为 `eventType=card` 提供 `payload.type=TRAVEL_ADVICE_CARD`，外层继续包含非空十进制 `eventId`、非空 `runId` 和既有 `payload` 对象。payload MUST 只含下列展示字段：`type` 固定字符串、正十进制字符串 `taskId`、非空字符串 `taskStatus`、布尔 `available`、可空 `weather`、数组 `advice`、可空字符串 `source`、布尔 `degraded`、可空字符串 `fallbackType`、可空 ISO 8601 `dataAt`、可空 ISO 8601 `expiresAt` 和布尔 `expired`。`available=true` 时 `source` MUST 为非空字符串；`available=false` 时 `source` 可以为 null。

`weather` 非空时 MUST 仅含 `area`、`condition`、`risk` 三个可空字符串字段，已提供的字段不得为空白字符串；`advice` 每项 MUST 仅含非空字符串 `type`、`text`。非空的 `source` 只展示 D 已定义的真实来源、缓存或 Demo 标识，未生成建议时不得补造来源。payload、SSE、持久化 DTO 和日志不得包含 `userId`、精确起点、坐标、路线折线、途经点、地图几何、`weatherJson` 或 `adviceJson`。

#### Scenario: 可用建议映射为卡片
- **WHEN** D 的只读 Tool 返回 `available=true` 的结构化建议结果
- **THEN** B 生成带相同任务号、状态、天气摘要、建议数组、降级和时效事实的 `TRAVEL_ADVICE_CARD`，并保留既有 `eventId` 与 `runId`

#### Scenario: 没有已生成建议是正常结果
- **WHEN** D 的只读 Tool 返回 `available=false`
- **THEN** B 生成 `weather=null`、`advice=[]`、`source=null`、`dataAt=null`、`expiresAt=null`、`degraded=false`、`fallbackType=null` 的合法 `TRAVEL_ADVICE_CARD`，且不刷新、不创建任务、不重试、不要求确认

#### Scenario: 过期或降级建议仍可只读展示
- **WHEN** 结构化结果带有 `expired=true` 或 `degraded=true`
- **THEN** B 原样保留布尔值、可用的时效字段和 `fallbackType`，天气缺失或建议为空时不伪造实时数据

### Requirement: 卡片校验和 SSE 恢复安全
系统 SHALL 仅渲染字段完整且类型正确的 `TRAVEL_ADVICE_CARD`。未知卡片类型沿用安全文本处理；已知出行建议卡片缺少必填字段、时间格式错误、任务号非法、建议/天气项出现未知字段或敏感字段时 MUST 拒绝，且不得更新前端 SSE 续传游标。

#### Scenario: 合法实时事件与历史事件一致
- **WHEN** C 收到实时 SSE 或运行恢复中的合法 `TRAVEL_ADVICE_CARD`
- **THEN** C 只根据 `eventType` 和 payload 渲染同一张卡片，不需要推断字段或调用 D 的内部接口

#### Scenario: 非法卡片不推进游标
- **WHEN** C 收到字段非法、未知或包含禁止字段的出行建议卡片
- **THEN** 事件被安全拒绝且最近已处理事件 ID 保持不变

