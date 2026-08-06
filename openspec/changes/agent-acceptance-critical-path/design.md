## Context

B 已有候选计划、服务端计划校验、运行状态机、`ToolContext + ToolCommand + ToolResult<T>`、确认动作和 `createOrder` 适配器。当前生产装配固定使用 `MockModelGateway`，`MultiToolSupervisor` 只按字符串查找只读适配器，工具失败不会真正触发持久化重规划，结果持久化只生成进度或错误消息。

本 change 由 B 负责。A 负责日期、场次、座位和建单 Application API，D 负责推荐 API；B 只能依赖它们的公开 Tool/Application API 和类型化 DTO。现有 `createOrder` 确认和订单写入保持不变。

## Goals / Non-Goals

**Goals:**

- 用 `ModelGateway` 隔离 DeepSeek HTTP 调用，默认模型为 `deepseek-v4-flash`；密钥只从运行环境读取。
- 把工具执行改为按明确的类型化注册项选择，依次执行通过校验的节点，发布安全的开始、成功和错误事实。
- 工具失败并明确建议重规划时，在上限内生成并校验新计划；写工具不参与自动重试或重规划执行。
- 用 Mock 工具和固定 DTO 夹具覆盖主控流程，正式业务 API 到位后只需新增 B 侧适配器注册。
- 将回复类型和工具阶段写成前端可识别的持久化 SSE 事件。

**Non-Goals:**

- 不实现电影、日期、场次、座位、库存或订单业务规则，不改 A/D 的 Controller、Repository、Mapper、Entity 和表。
- 不新增订单写入、确认接口或写工具自动重试。
- 不把原始模型输出、API Key、用户 ID、Cookie 或完整工具响应写入日志、轨迹或 SSE。

## Decisions

### 1. 真实模型以 Spring `RestClient` 调用 DeepSeek 的 OpenAI 兼容接口

`DeepSeekModelGateway` 使用 `@ConfigurationProperties` 接收 `DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`、`DEEPSEEK_API_KEY`，请求 `POST /chat/completions`、`Authorization: Bearer`、`stream=false` 和 JSON 输出。计划和回复分别使用受控提示词和 Jackson 解析；任何 HTTP、超时、空 choices 或 JSON 结构问题都转换为安全的 `AgentModelGatewayException`，不泄露供应商响应。

进入请求体前统一经过 `PromptSanitizer`，替换邮箱、Bearer/JWT 凭证和坐标等敏感文本；模型请求不携带真实 `userId`，槽位只保留完成计划所需的脱敏值。

没有引入供应商 SDK：当前项目已有 Spring MVC，`RestClient` 足以覆盖单次非流式调用，少一层 SDK 版本和密钥配置风险。`MockModelGateway` 通过 profile/property 继续用于测试和默认开发环境；显式启用真实模型且配置完整时才替换。

### 2. 工具适配器以 `AgentToolExecutor<C extends ToolCommand, R>` 注册

注册项同时声明目标名、`ToolDefinition` 和类型化 `execute(ToolContext, C)`。启动时将 executor 与白名单逐项比对，拒绝同名、未登记、类型不符或写工具缺少确认入口的组合。主控只从这个注册表取 executor，不能按 Bean 名、反射或 Controller 路由。

工具返回统一使用 `ToolResult`；参数错误沿用稳定参数错误码，下游未预期运行时异常由 B 适配层映射为 `CommonErrorCode.INTERNAL_ERROR`、`retryable=false`、`replanSuggested=false` 的失败结果。异常原文只进入受保护日志，不进入模型提示、SSE 或持久化载荷。

`rankMoviePlan` 先迁移为该接口；A 的 `queryAvailableDates`、`queryShows` 已通过正式 `ticketing.api.*Tool.execute(ToolContext, Command)` 接入，B 只依赖其公开 Command/ToolResult，不访问 A 的 Application Service 内部实现。`querySeats` 不作为本 change 验收依赖，保留扩展注册位；选座由 `card` 事件中的 `BUSINESS_INTENT`/`SELECT_SEATS` 携带已确认的 `showId`、`movieId`、`cinemaId`，前端跳转购票页后由 A 重新查询座位。三个字段必须是 JSON string，匹配 `^[1-9]\\d*$` 且能解析为正 Java `long`；缺失、前缀、前导零、零、负数、非数字和超范围值均不能生成选座卡片。`createOrder` 仅登记定义和确认后调用入口，普通主控不直接执行它。

### 3. 重规划只处理已执行的只读失败

当只读结果为 `FAILED` 且 `replanSuggested=true`，主控在同一 `runId`、原 `clientRequestId` 和上限内再次请求模型。新计划仍须通过 `PlanSchemaValidator`，并由现有 CAS 事务保存新版本；旧结果不能在 CAS 失败后重放。`PROCESSING`、网络断线、SSE 重连和写工具的未知结果都不触发重新调用。

### 4. SSE 事件使用既有持久化事件流

工具执行前后写入 `TOOL_START`、`TOOL_COMPLETE` 或 `TOOL_ERROR`；历史 `TOOL_RESULT` 只保留读取兼容。回复映射为 `QUESTION`、`PLAN_CARD`、`PROGRESS`、`ERROR`；选座映射为 `card` 事件中的 `BUSINESS_INTENT` 卡片，内层 `intent=SELECT_SEATS`，`businessRef` 必须包含来自同一已校验场次结果的 `showId`、`movieId`、`cinemaId`。持久化 JSON 工厂和 SSE 校验器都按 `^[1-9]\\d*$` 与正 Java `long` 范围拒绝非法值，不能只校验非空。每个卡片保留 `planId`、`planVersion`、`nodeId` 和已过滤 payload，旧版本及未知类型沿用现有客户端安全降级。

`MultiToolSupervisorResult.NodeToolResult` 同时保留节点 ID 和目标名，持久化层按节点关联工具事件，不按重规划后的列表位置猜测旧结果属于哪个节点。

## Risks / Trade-offs

- [DeepSeek 模型或 API 参数变化] → 把供应商请求/响应限制在一个基础设施类，保留 Mock 单测和 HTTP stub 测试。
- [A/D 正式 DTO 尚未提供] → 不猜字段；只交付可注册接口、Mock、夹具和待接入清单。
- [工具失败导致重复写] → 写工具只能在现有确认动作之后调用，并复用 actionId/幂等键查询结果，不自动重试。
- [真实模型输出不稳定] → 必经 JSON 解析和 `PlanSchemaValidator`，不合法计划返回安全错误而不调用工具。

## Migration Plan

1. 默认保持 Mock，部署环境先配置 Secret 和环境变量后再显式启用 DeepSeek。
2. 发布前执行定向 Agent 测试、`backend/mvnw.cmd verify`、严格 OpenSpec 校验和 `git diff --check`。
3. 真实模型异常时关闭启用开关并回退 Mock 仅限开发/演示；生产请求返回安全模型错误，不把用户请求改造成固定业务结果。

## Open Questions

- A：`queryAvailableDates`、`queryShows` 已合入 `dev`，正式入口为 `ticketing.api.*Tool.execute(ToolContext, Command)`；B 已接入类型化 Adapter。`cinemaId` 必须来自已校验来源且为正十进制业务 ID，A 的 Tool 按 Application 边界校验大于零；不存在或无可售排期返回成功空结果。`queryShows` 的 Agent Command 必须填写 `businessDate`。`querySeats` 当前不纳入本 change，沿用选座页重新查询方案。
- A：`queryAvailableDates`、`queryShows` 已合入 `dev`，成功结果现在提供 `dataAt`、`expiresAt`，B 的 Adapter 会拒绝缺少时效窗口的动态成功结果；当前代码和 `TicketingReadToolExecutionAdapterTest` 已验证该规则。
- D：`RankMoviePlanTool` 已新增返回 `ToolResult<RecommendationPlanResult>` 的兼容公开入口，并保留旧 `execute`。D 的公开完整 Command 以 `cityCode/date/ticketCount` 为必填条件，`genres/excludedGenres` 以 JSON 数组文本传递；B 已切换到新入口和完整 Command。D 的成功、空结果、降级夹具确认名称、评分、距离和预计路程没有可靠来源时为 `null`；C 当前卡片 Schema 未包含这些字段，因此 B 只映射已确认字段。
- D/B 合入顺序：D 保留兼容入口，B 已完成新结果适配；D 后续可以在独立改动中删除旧入口或将新入口改回 `execute`。B 不修改 D 推荐模块内部逻辑。
- C：选座使用 `card` + `BUSINESS_INTENT`，外层必须有 `planId`、`planVersion`、`nodeId`，卡片内必须有 `intent=SELECT_SEATS` 和完整的 `businessRef.showId/movieId/cinemaId`。C 使用 `/shows/{showId}/seats?movieId={movieId}&cinemaId={cinemaId}`，负责校验并编码 query 参数；B 不依赖页面 state、localStorage 或前端推断补齐 ID。
