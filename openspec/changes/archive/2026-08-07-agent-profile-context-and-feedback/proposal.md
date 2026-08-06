## Why

D 的画像摘要工具和确认后行为记录接口已进入 `dev`，但 B 尚未在推荐生成和确认动作中安全使用它们。当前模型也可能保留候选计划提供的临时 `planId`，无法作为后续确认和画像反馈的稳定引用。

## What Changes

- 在生成候选推荐计划前进行一次 B 内部画像摘要预取，只向模型传递最小、受控的已启用标签摘要。
- 对校验后且会被持久化展示的方案生成服务端小写 UUID `planId`，并在运行、卡片和确认动作中复用。
- 在确认动作实际通过 CAS 保存最终拒绝或订单成功状态后，调用 D 的 `ProfileBehaviorRecorder` 记录最小接受或拒绝反馈。
- 为画像不可用、重复确认、取消、过期、校验失败、订单失败和结果未知增加定向测试。

## Capabilities

### New Capabilities

- `agent-profile-context-prefetch`: Agent 在推荐生成前安全读取画像摘要，并按启用状态向模型提供最小上下文。
- `agent-plan-feedback-recording`: Agent 使用稳定方案 ID，在最终确认结果保存后记录一次画像反馈。

### Modified Capabilities

- `agent-plan-runtime`: 已展示方案的 `planId` 改由服务端生成并持久化，不能直接采用模型候选值。

## Impact

- 影响 B 的 `ModelGateway` 计划请求、`MultiToolSupervisor`、确认应用服务及其测试。
- 只调用 D 已合入的 `GetProfileSummaryTool.execute(ToolContext)` 和 `ProfileBehaviorRecorder` 公共应用接口；不修改 D、A、C、SSE、工具注册、订单持久化或数据库结构。
