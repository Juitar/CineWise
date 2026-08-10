## Context

`getTravelAdvice` 已注册为只读 Tool，但普通 `/agent` 会话没有可信 `travelTaskId` 来源。当前 `MultiToolSupervisor` 将全局注册 Tool 全部交给计划模型，并在追问前扫描全局只读 Tool，导致模型误选出行 Tool 后把内部任务号变成 `QUESTION`。现有 `ModelGateway`、消息持久化和 SSE 已支持受控结构化回复；本次不增加表或事件种类。

## Goals / Non-Goals

**Goals:**

- 在计划前通过不带 Tool 的 DeepSeek 结构化结果得到 `MOVIE`、`TRAVEL` 或 `GENERAL_CHAT`。
- 从意图和服务端可信出行任务上下文生成本轮允许 Tool 集合，并在初始计划和重规划中复用。
- 普通、不确定或安全降级对话生成受控 `TEXT`，沿用一次运行、现有消息持久化和 SSE 格式。
- 只允许当前已校验计划实际涉及、且可由用户正常提供的观影字段进入 `QUESTION`。

**Non-Goals:**

- 不修改 D 的 `GetTravelAdviceTool`、订单或出行任务数据、数据库表、Flyway 和前端 API。
- 不从用户文本、模型输出、URL 或 Tool Command 接收 `userId` 或 `travelTaskId`，也不让前端上传该字段。
- 不增加 Agent run、SSE 事件种类或重试写入。

## Decisions

### 1. 意图识别是独立、保守的模型调用

新增 B 自有 `AgentIntent` 和类型化意图请求/响应。输入先经过既有 `PromptSanitizer`；DeepSeek 只返回固定 JSON 意图，不接收 Tool 定义、槽位或业务结果。非法 JSON、未知值、空值、超时和异常统一映射为 `GENERAL_CHAT`。备选的关键词规则会把不确定输入误归类，因此不用作允许 Tool 的依据。

### 2. Tool 可见范围由服务端计算并贯穿运行

新增 `AgentToolAvailabilityPolicy`：`MOVIE` 仅返回 `rankMoviePlan`、`queryAvailableDates`、`queryShows` 的已登记子集；`GENERAL_CHAT` 返回空集；`TRAVEL` 仅在服务端已有已校验且归属当前用户的任务上下文时返回 `getTravelAdvice`。普通会话没有该上下文时，即使识别为 `TRAVEL` 也转为安全 `TEXT` 引导。初始计划和 `replan()` 接收同一集合，校验器继续拒绝集合外工具。

### 3. 普通对话使用 `TEXT` 的受控事实

为 `AgentReplyMessageType` 和受控 payload 增加 `TEXT`，`ReplyComposer` 只把固定能力边界和用户原始问题的脱敏摘要交给 `ModelGateway.generateReply()`。提示词禁止实时业务事实、内部 ID、路径和要求用户填写 ID；模型异常使用安全静态文本。该类型按既有 `message.start/message.complete` 和持久化回复路径映射，不新增外层 SSE 协议。

### 4. 追问从已校验的本轮计划反推

移除扫描全局注册表的缺失字段逻辑。仅检查当前允许 Tool 且通过 `PlanSchemaValidator` 的 `ASK_USER`/待执行节点所需输入，并再次使用字段允许表。`travelTaskId`、所有运行/动作/计划号以及影片、影院、场次等业务引用 ID 一旦缺失或被模型要求追问，立即输出安全 `TEXT`，不生成 `QUESTION`。

## Risks / Trade-offs

- [意图模型不可用] → 保守降级为 `GENERAL_CHAT`，不调用 Tool。
- [真实模型返回不安全文本] → 网关按受控 JSON 解析并检查禁止字段；不合格时使用安全静态文本。
- [已有观影追问回归] → 保留正式观影槽位和现有计划校验，覆盖城市、日期、人数缺失测试。
- [可信任务上下文来源尚未接入普通会话] → 本 change 不猜测字段或新建入口；无上下文一律不暴露出行 Tool。

## Migration Plan

1. 先增加意图、可见 Tool 和 `TEXT` 类型，再接入 Supervisor 与回复/SSE 映射。
2. 增加 Mock、DeepSeek、Supervisor 和提交服务的定向测试。
3. 仅部署代码；无 schema 迁移。回退时移除新路由即可，旧 SSE 消费者仍按现有 `TEXT` 格式显示。

## Open Questions

普通 `/agent` 会话的可信出行任务上下文入口目前不存在。本次按无上下文安全文本处理；若后续由 D/C 提供公开、归属已校验的上下文入口，需要另建 change 说明来源和调用方。
