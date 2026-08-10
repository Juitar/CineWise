## ADDED Requirements

### Requirement: 服务端保守识别本轮意图
系统 SHALL 在生成候选计划前，使用经过既有脱敏处理的当前文本调用 `ModelGateway` 的类型化意图识别，并且只接受 `MOVIE`、`TRAVEL`、`GENERAL_CHAT` 三个结构化结果。识别不得调用 Tool、生成 Tool 参数、读取或写入订单或出行数据。非法 JSON、未知值、空值、超时、异常或不确定结果 MUST 降级为 `GENERAL_CHAT`。

#### Scenario: 普通文本识别为普通对话
- **WHEN** 用户提交“这啥”或问候语
- **THEN** 系统得到 `GENERAL_CHAT` 且不调用计划生成或任何 Tool

#### Scenario: 识别服务失败
- **WHEN** DeepSeek 返回非法 JSON、未知意图或调用超时
- **THEN** 系统以 `GENERAL_CHAT` 继续，且不暴露异常详情或内部字段

### Requirement: 普通对话生成安全文本回复
系统 SHALL 为 `GENERAL_CHAT` 通过 `ModelGateway.generateReply()` 生成 `TEXT` 回复，并沿用既有一次用户消息对应一次运行、消息持久化和 SSE 回复流程。回复只可说明产品能力、正常交流或引导用户表达观影需求，MUST NOT 捏造实时业务事实或包含 `travelTaskId`、`runId`、`actionId`、数据库 ID、内部接口路径或要求用户填写内部 ID。

#### Scenario: 普通文本不产生业务卡片或追问
- **WHEN** 普通文本的意图为 `GENERAL_CHAT`
- **THEN** 系统只产生正常 `TEXT`，不产生 `QUESTION`、`PLAN_CARD` 或 `TRAVEL_ADVICE_CARD`
