## MODIFIED Requirements

### Requirement: Agent 回复和工具阶段映射为安全 SSE 事件
系统 SHALL 将 `TEXT`、`QUESTION`、`PLAN_CARD`、`PROGRESS`、`ERROR` 及工具开始、完成、错误事件写入既有持久化 SSE 流；选座 SHALL 使用 `card` 事件中的 `BUSINESS_INTENT` 卡片，内层 `intent=SELECT_SEATS`；事件不得包含模型原文、密钥、完整业务内部响应或内部 ID 追问。`PROCESSING` 不是终态，不能写成完成事件。

#### Scenario: 普通对话写入文本事件
- **WHEN** 服务端决定本轮为普通对话或安全降级
- **THEN** SSE 使用既有消息事件格式输出 `TEXT`，不新增 Agent run 或 SSE 事件种类
