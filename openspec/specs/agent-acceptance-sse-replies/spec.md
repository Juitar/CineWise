# agent-acceptance-sse-replies Specification

## Purpose
定义 Agent 回复、工具阶段和选座业务意图写入持久化 SSE 流时的安全字段和终态规则。
## Requirements
### Requirement: Agent 回复和工具阶段映射为安全 SSE 事件
系统 SHALL 将 `QUESTION`、`PLAN_CARD`、`PROGRESS`、`ERROR` 及工具开始、完成、错误事件写入既有持久化 SSE 流；选座 SHALL 使用 `card` 事件中的 `BUSINESS_INTENT` 卡片，内层 `intent=SELECT_SEATS`；事件不得包含模型原文、密钥或完整业务内部响应。`PROCESSING` 不是终态，不能写成完成事件。

#### Scenario: 工具执行并产生计划卡片
- **WHEN** 已校验计划执行工具并生成可展示计划回复
- **THEN** SSE 依次包含 `tool.start`、`tool.complete` 和带 `planId`、`planVersion` 的 `PLAN_CARD`

#### Scenario: 工具仍在处理中
- **WHEN** 工具返回 `PROCESSING`
- **THEN** SSE 只输出进行中的 `tool.start` 和 `message.start`/`PROGRESS`，不输出 `tool.complete`、`tool.error`、`message.complete` 或 `run.complete`

#### Scenario: 用户需要补充信息或选择座位
- **WHEN** 服务端决定继续追问或等待座位选择
- **THEN** SSE 输出类型分别为 `QUESTION` 或 `card`；选座卡片外层包含 `planId`、`planVersion`、`nodeId`，内层为 `BUSINESS_INTENT`/`SELECT_SEATS`，并在 `payload.businessRef` 中包含 `showId`、`movieId`、`cinemaId`

#### Scenario: 模型或工具发生安全失败
- **WHEN** 模型、计划校验或工具执行无法完成
- **THEN** SSE 输出 `ERROR` 和安全错误码/文案，不输出异常原文
