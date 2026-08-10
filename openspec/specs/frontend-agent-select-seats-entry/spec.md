# frontend-agent-select-seats-entry Specification

## Purpose
TBD - created by archiving change frontend-agent-select-seats-entry. Update Purpose after archive.
## Requirements
### Requirement: 已确认场次可进入选座页

前端 SHALL 仅在 `card + BUSINESS_INTENT + SELECT_SEATS` 已通过外层计划字段和 `businessRef.showId`、`movieId`、`cinemaId` 校验时显示选座入口。三个业务 ID 必须是 JSON string、无前导零的正十进制字符串，且不超过 `9223372036854775807`；前端始终按字符串保存，不转换为 JavaScript `number`。

#### Scenario: 合法选座卡片

- **WHEN** 当前会话和运行收到合法 `SELECT_SEATS` 卡片
- **THEN** 前端显示“去选座”入口并导航到 `/shows/{showId}/seats?movieId={movieId}&cinemaId={cinemaId}`
- **AND** 三个 ID 必须分别 URL 编码并保留为字符串

#### Scenario: 字段缺失或非法

- **WHEN** 选座卡片缺少 `planId`、`planVersion`、`nodeId`、任一 `businessRef` ID，或任一 ID 不是 JSON string、无前导零正十进制字符串、正数或超过 Java `long` 上限
- **THEN** 前端不显示选座入口
- **AND** 不推进事件游标

### Requirement: 入口不触发交易写操作

前端 SHALL 不从 Agent 卡片生成建单、支付、确认、锁座或座位提交请求。

#### Scenario: 用户点击选座入口

- **WHEN** 用户点击“去选座”
- **THEN** 前端只执行路由跳转

