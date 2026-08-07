## ADDED Requirements

### Requirement: 出行建议 Tool 只在可信任务上下文中可见
系统 SHALL 仅在本轮服务端已有已校验且属于当前用户的出行任务上下文时，把 `getTravelAdvice` 放入允许 Tool 集合。`travelTaskId` MUST 只由该服务端上下文注入；用户文本、模型输出、URL 参数、前端请求和 Tool Command 不得提供该字段或 `userId`。

#### Scenario: 有可信任务上下文时调用出行建议
- **WHEN** `TRAVEL` 意图具备已校验的当前用户任务上下文
- **THEN** 模型可以看到 `getTravelAdvice`，执行适配器只使用服务端注入的任务号，D Tool 仍按认证上下文校验归属
