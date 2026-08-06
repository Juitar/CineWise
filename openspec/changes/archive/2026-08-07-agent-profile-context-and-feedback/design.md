## Context

当前 `MultiToolSupervisor` 直接构造 `PlanGenerationRequest`，候选计划的 `planId` 原样流向运行结果。确认服务以短事务分别领取动作和保存订单结果，CAS 失败时会读取胜者。D 已提供同步的 `GetProfileSummaryTool` 与 `ProfileBehaviorRecorder`，二者均由 D 从 `CurrentUserAccessor` 获取身份。

## Goals / Non-Goals

**Goals:**

- 在认证请求线程内、模型生成之前读取一次画像摘要；只把 `enabled=true` 的最小 tags 格式交给模型请求。
- 服务端为通过校验并进入执行/持久化的方案生成小写 UUID，后续运行、卡片和确认动作使用同一值。
- 仅在本请求 CAS 实际保存 `REJECTED` 或 `SUCCEEDED` 后记录反馈；使用 `actionId`、动作已有 `planId` 和固定 UTC 时间。

**Non-Goals:**

- 不登记画像工具、不创建计划节点、运行、SSE 或新的持久化结构。
- 不修改 D、A、C、订单事务、Mapper、Repository、数据库或 SSE 协议。
- 不对画像读取/写入自动重试、补发或回滚订单和确认状态。

## Decisions

### 画像摘要作为模型请求的受控附加字段

`MultiToolSupervisor` 注入 D 的 `GetProfileSummaryTool`，使用已有运行 ID、固定内部 nodeId、`profile-summary`、traceId、剩余预算和状态版本构建 `ToolContext`。它不是 `ToolRegistry` 的成员，也不进入结果、运行步骤或事件。转换器仅接受启用摘要的 tag `type/value/polarity/weight/confidence/source/updatedAt`，并将其放入 `PlanGenerationRequest` 的只读字段；关闭或失败时传空上下文并继续。备选方案是作为 `CALL_TOOL`，会错误增加模型可见能力、轨迹和 SSE，故不采用。

### 服务器覆盖候选计划 ID

候选计划先按现有规则校验；一旦有效，Supervisor 以 `UUID.randomUUID().toString()` 替换计划 ID，再继续状态机和持久化结果。重规划只在尚未产生确认动作的运行内生成新方案；已创建动作始终保留其保存的 planId。备选方案是接受模型 ID，不能满足稳定、不可伪造的确认和反馈引用。

### 在 CAS 成功后做一次尽力而为的画像记录

确认服务在拒绝的 CAS 成功后调用 `recordPlanRejected`；订单成功且结果状态的 CAS 成功后调用 `recordPlanAccepted`。记录使用 `LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)`，并捕获画像调用异常，只记安全日志。CAS 读到其他请求的最终状态、取消、过期、无权限、参数或条件校验失败、订单失败和结果未知均不调用。回调位于短事务外，因此不扩展订单或确认事务；失败不会回滚已经保存的动作。

## Risks / Trade-offs

- [D 的画像读取失败] → 只传空画像上下文，继续普通推荐，不暴露内部错误。
- [画像写入失败] → 已成功订单和确认状态保留，不重试、不补发，仅记录不含 tags/身份的日志。
- [并发确认] → 只依据本次 `compareAndSet` 的 `applied=true` 调用；读取到胜者不调用。
- [模型上下文泄露] → 只用转换后的 tags，不保存到 SSE、运行记录或回复。

## Migration Plan

无需迁移。发布后新的有效方案使用服务器 UUID；回滚只停止新的画像调用，不改变已保存确认动作和订单。

## Open Questions

无。本 change 只使用 D 已合入的公开 Java 接口和现有确认 CAS 结果。
