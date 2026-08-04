## 背景和依据

本方案以以下已合入内容为准：

- `openspec/specs/agent-tool-contracts/spec.md`：`ToolContext`、`ToolResult<T>` 和服务端白名单。
- `openspec/specs/agent-plan-validation/spec.md`：计划在工具调用前校验白名单、必填字段、来源和 Java 类型。
- `openspec/specs/agent-run-state-machine/spec.md`：状态机只推进状态，不调用真实工具。
- `RankMoviePlanCommand`、`RankMoviePlanTool`、`FixedRecommendationResult`：D 当前公开的类型化边界。
- `CineWise-Docs/系分文档/后端/妙语购票_Agent智能决策中心系统分析设计.md` 第 4.2、4.3、11.6、11.9、11.10 节：B 是唯一计划和运行控制方，工具只接收类型化 Command 并返回结果。

## 调用边界

```text
已校验 ExecutionPlanNode + ExecutionRunState
  -> B: RankMoviePlan 执行适配器
     -> B: 构造 ToolContext、解析槽位、构造 RankMoviePlanCommand
        -> D: RankMoviePlanTool.execute(context, command)
           -> ToolResult<FixedRecommendationResult>
              -> B: ExecutionPlanStateMachine.recordToolResult(...)
```

- B 的适配器只能以注入的 `RankMoviePlanTool` 实例调用 `execute(ToolContext, RankMoviePlanCommand)`；不使用反射、`ApplicationContext`、Bean 名、类名、方法名或 HTTP 调用。
- D 工具不获得 B 的计划生成器、SSE 发布器、会话、运行状态或状态机引用。D 只收到公共上下文和领域 Command。
- B 不进入 `recommendation` 的 Application、Domain、Infrastructure、Controller、Mapper 或 Repository 包；A 的场次事实仍由 D 通过已实现的公开 `ShowQueryService` 获取。

## 白名单定义

本次在 B 的白名单装配处新增一个不可变 `ToolDefinition`：

| 属性 | 固定值 |
| --- | --- |
| `name` | `rankMoviePlan` |
| `commandType` | `RankMoviePlanCommand.class` |
| `resultType` | `FixedRecommendationResult.class` |
| `readOnly` | `true` |
| `timeout` | 3 秒（内部只读工具） |
| `idempotencyRequired` | `false` |
| `exposedErrorCodes` | `100001` |

输入定义必须和 D 的 record 完全一致：

| 输入名 | Java 类型 | 必填 |
| --- | --- | --- |
| `movieId` | `String` | 是 |
| `cinemaId` | `String` | 是 |
| `date` | `LocalDate` | 是 |
| `timeFrom` | `LocalTime` | 否 |
| `timeTo` | `LocalTime` | 否 |

`PlanSchemaValidator` 继续负责计划层的名称、必填字段、输入来源和类型校验；本次不放宽其已有规则。

## ToolContext 来源

当前 `ExecutionRunState` 不保存 `runId`、`traceId` 和超时预算，槽位快照也不应承载这些调用元数据。因此适配器使用 B 的受信任执行输入构造上下文：

- `runId`、`traceId`：由未来 B 运行用例/调度器传入的运行元数据提供，不能来自模型、用户消息或槽位。
- `nodeId`：必须取当前已校验 `ExecutionPlanNode.nodeId`。
- `targetName`：固定使用 `RankMoviePlanTool.TARGET_NAME`，并校验节点也为该名称。
- `inputRefs`：按节点中已声明的 `SLOT` 引用生成 `slots.<slotName>`；三个必填字段必须存在，两个时段字段要么同时存在要么同时缺失。不得把槽位值、用户信息或推荐结果放入其中。
- `deadlineMs`：固定为白名单的 3 秒预算，或由 B 的受信任总预算裁剪为不超过 3 秒的正数；不得采信模型给出的超时值。
- `clientRequestId`、`idempotencyKey`：本工具只读，均为 `null`。
- `stateVersion`：使用该节点 `SlotSnapshot.version()`，便于 D 回传同一版本；不从用户输入读取。

适配器开始调用前必须确认目标节点是状态机当前选中的只读 `CALL_TOOL` 节点。它先调用 `startNode()`，再处理本次调用的唯一结果；不会自行改变计划、重规划次数或其他节点。

## 槽位到 Command 的转换

适配器只接受节点中以 `InputReferenceSource.SLOT` 指向的字段，不接受 `NODE_RESULT` 作为这五个 Command 字段来源。本次固定映射如下：

| Command 字段 | 节点 `inputRefs.inputName` | 槽位值处理 |
| --- | --- | --- |
| `movieId` | `movieId` | 从对应槽位读取非空正十进制字符串 |
| `cinemaId` | `cinemaId` | 从对应槽位读取非空正十进制字符串 |
| `date` | `date` | 从 ISO-8601 日期字符串解析 `LocalDate` |
| `timeFrom` | `timeFrom` | 缺失时为 `null`，否则从 ISO-8601 本地时间字符串解析 `LocalTime` |
| `timeTo` | `timeTo` | 缺失时为 `null`，否则从 ISO-8601 本地时间字符串解析 `LocalTime` |

执行前再次检查以下内容，防止手工构造或过期的运行计划绕过计划校验：节点名称、输入名是否重复或未知、必填槽位是否存在、可选时段是否成对，以及 `timeFrom < timeTo`。随后创建 `RankMoviePlanCommand`，复用 D 对业务 ID 和时段的构造校验。

槽位转换或 Command 构造抛出 `IllegalArgumentException` 时，B 不调用 D，而是创建：

```text
ToolResult<FixedRecommendationResult>(
  status=FAILED, data=null, errorCode=100001,
  retryable=false, replanSuggested=false,
  suggestedNextAction=CHECK_INPUT,
  degraded=false, fallbackType=null,
  stateVersion=context.stateVersion(), dataAt=null, expiresAt=null)
```

这不是对 D 业务规则的修改，而是 B 在调用边界把无法构造 D Command 的输入错误转换为已有公共结果格式。

## 结果和失败处理

1. Command 合法时，适配器只调用一次 `RankMoviePlanTool.execute(context, command)`，不解释或改写其 `ToolResult<FixedRecommendationResult>`。
2. 无场次的 D 结果仍为 `SUCCESS`。当 `purchaseEligible=false`、`missingFactors=[SHOWTIME]`、`degraded=true` 时，适配器照常交给状态机，节点进入 `SUCCESS`，不重试、不跳过下游；展示或继续追问由后续独立 change 负责。
3. Command 转换失败的 `100001` 结果和 D 返回的 `FAILED` 结果都交给 `ExecutionPlanStateMachine.recordToolResult()`。状态机继续按既有 `failurePolicy`、`retryable` 和尝试次数决定重新排队或 `FAILED`，并在最终失败时跳过未开始下游节点。
4. 适配器不调用 `requestReplan()`，不生成新计划，不发布 SSE，不写数据库、Redis 或会话状态。D 工具的 `PROCESSING`、`SUCCESS`、`FAILED` 公共状态也不在适配器中另行定义。

## 测试方案

- 白名单与计划校验单元测试：验证五个字段的名称、Java 类型、只读标记、3 秒时限、`100001`，以及合法节点可通过 `PlanSchemaValidator`。
- B-D 最小集成测试：使用 D 的真实 `RankMoviePlanTool` 和固定推荐测试夹具，构造 B 已校验计划并执行适配器。验证正常成功、无场次成功降级、D 的结果对象被原样交给状态机。
- 适配器异常测试：覆盖缺必填槽位、日期/时间解析失败、只传一个时段、倒置时段和不匹配节点；每种情况均不调用 D，返回不可重试 `100001`，并由状态机完成失败和下游跳过。
- 失败状态测试：用 `RankMoviePlanTool` 的测试替身返回 `FAILED` 的公共结果，验证 `RETRY_ONCE + retryable=true` 仅重新排队一次，其余失败按现有状态机规则结束；替身仍只实现同一个 Java 方法签名，不引入字符串路由。
- 执行时只运行相关 Agent/D 测试；实现完成后再执行 Maven `verify`、OpenSpec 严格校验和 Git 检查。本次规划阶段不运行或修改业务代码。

## 风险和不做的兼容

- 当前状态机是内存快照，没有运行元数据持久化；本次显式由 B 执行输入提供 `runId`/`traceId`，不伪造会话或数据库模型。
- `SlotSnapshot.values` 为字符串映射，计划校验的 Java 类型不能单独保证内容可解析；适配器保留解析与 Command 二次校验。
- 当前 D 的 Command 在构造时即拒绝非法输入，因此“参数不合法”由 B 的转换边界产生公共失败结果，不要求 D 新增异常包装或修改推荐规则。
- 未捕获地改变 D 的 `ToolResult` 字段、A 的场次事实或前端卡片都属于范围外；若实现发现这些公开类型、字段或行为不一致，立即停止并由 B/D（涉及场次时加 A）确认。
