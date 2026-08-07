## ADDED Requirements

### Requirement: 模型网关提供受控意图和文本回复
真实 DeepSeek 与 Mock `ModelGateway` SHALL 支持类型化意图识别及 `TEXT` 回复。真实模型响应必须按受控 JSON 解析；意图解析失败返回 `GENERAL_CHAT`，文本回复包含禁止内部字段或实时业务断言时必须替换为安全文本。模型原文、供应商异常和内部标识不得写入日志、SSE 或持久化数据。

#### Scenario: 文本回复模型异常
- **WHEN** 普通对话的回复模型调用失败或返回不合格内容
- **THEN** 系统输出安全 `TEXT`，不调用 Tool 且不暴露供应商异常
