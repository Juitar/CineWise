## Purpose

定义 Agent 推荐前画像摘要的内部预取和模型上下文最小化规则。

## Requirements

### Requirement: 推荐前内部画像摘要预取
系统 SHALL 在 Agent 生成推荐候选计划前，使用现有运行上下文调用 `GetProfileSummaryTool.execute(ToolContext)` 一次。调用不得携带 userId，不得登记 `ToolRegistry`、创建 CALL_TOOL 节点、Agent run、运行步骤或 SSE。`enabled=false`、无同意或工具失败时，系统 MUST 继续普通推荐，且不得向模型、SSE 或运行记录传递 tags 或内部错误。

#### Scenario: 已启用画像进入模型上下文
- **WHEN** D 返回 `enabled=true` 的摘要
- **THEN** 系统仅将受控 tag 字段传给模型计划请求，不发布额外事件

#### Scenario: 画像关闭或不可用
- **WHEN** D 返回 `enabled=false` 或预取失败
- **THEN** 系统以空画像上下文继续生成推荐

### Requirement: 模型上下文最小化
系统 SHALL 仅将 D 返回 tag 的 `type`、`value`、`polarity`、`weight`、`confidence`、`source` 和 `updatedAt` 交给模型。系统 MUST NOT 将完整画像、身份、工具错误或原始结果写入模型输出、SSE 或 Agent 运行记录。

#### Scenario: 运行记录不包含画像内容
- **WHEN** 画像预取完成并生成方案
- **THEN** 持久化的运行和事件中不出现画像 tags 或 userId
