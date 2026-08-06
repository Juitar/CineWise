# agent-acceptance-sse-replies Specification

## Purpose
TBD - created by archiving change agent-acceptance-critical-path. Update Purpose after archive.
## Requirements
### Requirement: Agent 回复和工具阶段映射为安全 SSE 事件
系统 SHALL 将 `QUESTION`、`PLAN_CARD`、`PROGRESS`、`ERROR` 及工具开始、完成事件写入既有持久化 SSE 流；选座 SHALL 使用 `card` 事件中的 `BUSINESS_INTENT` 卡片，内层 `intent=SELECT_SEATS`；事件不得包含模型原文、密钥或完整业务内部响应。

#### Scenario: 工具执行并产生计划卡片
- **WHEN** 已校验计划执行工具并生成可展示计划回复
- **THEN** SSE 依次包含 `tool.start`、`tool.complete` 和带 `planId`、`planVersion` 的 `PLAN_CARD`

#### Scenario: 用户需要补充信息或选择座位
- **WHEN** 服务端决定继续追问或等待座位选择
- **THEN** SSE 输出类型分别为 `QUESTION` 或 `card`，选座卡片只包含 `planId`、`planVersion`、`nodeId` 和 `businessRef.showId` 等受控字段

#### Scenario: 模型或工具发生安全失败
- **WHEN** 模型、计划校验或工具执行无法完成
- **THEN** SSE 输出 `ERROR` 和安全错误码/文案，不输出异常原文
