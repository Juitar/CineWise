## 背景

当前后端已经建立 `agent/api`、`agent/application`、`agent/domain`、`agent/infrastructure` 和 `agent/tool/ticketing` 包占位，但尚无实际 Agent 类型。A 的票务工具和 D 的推荐工具后续都需要 B 提供统一工具协议；运行引擎也必须在执行模型计划前完成确定性校验。

本变更依据 Agent 智能决策中心设计第 11.1、11.3、11.6、11.9 和 11.10 节，以及仓库后端编码规范第 3 节。当前不接入外部模型、数据库、Redis、SSE 或真实领域工具，因此可以只用纯 Java 类型和单元测试完成。

## 目标与非目标

**目标：**

- 提供唯一的 `ToolContext + Command + ToolResult<T>` 公共调用结构。
- 提供不可变工具定义和白名单注册表，阻止任意名称调用。
- 提供类型化计划、节点、依赖和失败策略模型。
- 在执行前检查节点上限、节点唯一、依赖无环、输入引用、工具白名单，并拒绝当前阶段的写工具。
- 提供不依赖外部模型的确定性 `MockModelGateway`。
- 通过纯单元测试让该基础能力可独立验收。

**非目标：**

- 不实现运行调度、节点并行执行、失败重规划和持久化。
- 不实现 `agent_session` 至 `agent_feedback` 表、Flyway 或 Repository。
- 不实现 Redis 上下文、SSE 事件、REST Controller、认证和前端。
- 不实现 A/D 的真实 Command、业务结果 DTO 或工具适配器。
- 不解决确认动作有效期、会话单活动运行等后续持久化设计问题。
- 不实现服务端参数摘要、一次性 `actionId`、用户与计划版本绑定，写工具不得进入运行计划。

## 设计决定

### 1. 公共协议和计划模型保持纯 Java

`ToolContext`、`ToolResult<T>`、工具枚举、`ToolDefinition`、候选计划、运行计划、节点、依赖和校验结果放在 `agent.domain` 下的细分包中，不依赖 Spring、Jackson、MyBatis、Servlet 或供应商 SDK。`ModelGateway` 作为应用层端口，`MockModelGateway` 放在 `agent.infrastructure`。

这样可以满足现有 ArchUnit 规则，也允许未来真实模型适配器和 A/D 工具适配器复用相同类型。备选方案是直接在 Spring Bean 或 Controller DTO 中定义协议；这会把框架和 Web 类型带入计划核心，因此不采用。

### 2. ToolResult 公共字段只保留一套名称

`ToolContext` 固定为 A 已定义的 `String runId`、`String nodeId`、`String targetName`、`List<String> inputRefs`、正数 `long deadlineMs`、`String traceId`、可空 `String clientRequestId`、可空 `String idempotencyKey` 和可空 `Long stateVersion`；写工具不得使用空的请求标识或幂等键。

`ToolResult<T>` 严格使用 `ToolStatus status,T data,Integer errorCode,boolean retryable,boolean replanSuggested,String suggestedNextAction,boolean degraded,String fallbackType,Long stateVersion,Instant dataAt,Instant expiresAt`。`status` 非空并使用 A 已定义的 `SUCCESS/FAILED/PROCESSING`；`errorCode` 在成功时为空，`suggestedNextAction`、`fallbackType`、`stateVersion`、`dataAt` 和 `expiresAt` 可空；动态数据能够进入后续节点时后两项必须非空。业务来源、`isExpired` 和候选字段放入对应类型化 `data`；D 的 `dataTime` 在真实适配器接入时映射到公共 `dataAt`，不在公共结果中增加同义字段。

这与 Agent 详设和后端编码规范一致。D 的现有 OpenSpec 仍有 `dataTime` 表述，因此真实推荐工具开始编码前需要 D 确认字段位置和映射。

### 3. 工具注册表使用显式定义而不是反射查找

`ToolDefinition` 保存工具名称、Command 类型、结果类型、读写属性、超时、幂等要求、必填输入和允许错误码。`failurePolicy` 由计划节点保存。当前校验器允许登记写工具，但在确认服务、服务端参数摘要和 `actionId` 尚未实现时一律拒绝其候选节点。`ToolRegistry` 在构造时复制定义并建立不可变映射，拒绝重复名称和不完整定义。计划校验和未来路由都只按该映射查询，禁止把模型或用户字符串解释为 Bean 名、类名或方法名。

备选方案是扫描注解并按 Bean 名动态调用。该方式会隐藏重复名称和缺失元数据，也增加任意名称调用风险，因此不采用。

### 4. 计划校验使用类型化模型和结构化问题列表

真实模型和 Mock 模型都先产生只含模型可提出字段的类型化候选计划；候选节点不携带参数摘要、确认凭据或 `actionId`。候选计划再交给 `PlanSchemaValidator`。校验通过后由服务端生成运行计划：节点初始为 `PENDING`，并补入 `requiresConfirmation`、`autoSkipped=false`、可空的跳过原因和来源节点，以及脱敏槽位快照。本 change 只定义该转换和初始值，不实现调度、状态推进或自动跳过。校验按以下顺序执行：

1. 检查计划 ID、版本和最多 12 个节点。
2. 检查固定节点类型、节点 ID 唯一和字段完整性。
3. 检查依赖节点存在且依赖图无环。
4. 检查输入仅引用已声明槽位或上游结果，并核对字段类型。
5. 检查 `CALL_TOOL` 存在于注册表且必填输入齐全。
6. 拒绝所有写工具候选节点；后续确认动作 change 必须由服务端根据已校验 Command 计算参数摘要，并与一次性 `actionId`、用户和计划版本绑定后才允许执行。

校验返回包含稳定问题码、节点 ID 和字段路径的问题列表；调用方只有在结果有效时才能继续。采用问题列表可以一次报告多个模型结构错误，也便于单元测试。备选方案是遇到首个错误直接抛异常；它不利于模型输出诊断，因此不采用。

### 5. MockModelGateway 使用版本化固定场景

`ModelGateway` 只暴露生成候选计划和结构化回复所需的 Agent 自有请求/响应。`MockModelGateway` 根据固定场景版本、`clientRequestId`、输入和允许工具列表选择预设计划，不调用网络、不读取当前时间，并保持节点顺序稳定。所有返回结果仍经过同一个 `PlanSchemaValidator`。

备选方案是使用随机节点或当前时间生成 ID。该方式会让回归测试不可重复，因此不采用。

### 6. 本变更只提供基础类型，不提前实现领域工具

测试使用本变更内部的假工具定义覆盖只读、写入、未知工具和缺参场景。`searchMovies`、`queryShows`、`createOrder` 和推荐工具等真实 Command 与结果类型仍由 A/D 确认后在后续 change 中实现，避免 B 猜测其他模块字段。

## 风险与取舍

- [公共 `ToolResult<T>` 会影响 A/D 适配器] → B 以 A 的正式类型定义、后端编码规范和 D 已记录的确认结论作为实现依据；实现完成后的正常开发 PR 由受影响 Owner 审查。
- [计划校验规则一次实现过多] → 本次只实现详设已经明确且能纯单测的结构规则，不实现槽位决策、运行调度、重规划、确认持久化或写工具执行。
- [Mock 场景与后续真实模型差异] → 固定 `mock-plan-v1` 版本，真实模型接入时新增适配器测试，不修改现有 Mock 语义。
- [结构化问题码与 Agent 业务错误码混淆] → 计划校验问题只用于内部诊断；对外错误码在后续 API change 中定义。

## 实施与回退

1. B 核对 A 的正式类型定义、后端编码规范和 D 已记录的确认结论，并据此实现公共协议与 `dataTime -> dataAt` 映射。
2. 在现有 Agent 包内新增纯 Java 类型、注册表、校验器和 Mock 实现。
3. 运行 Agent 单元测试、ArchUnit 和完整 Maven `verify`。
4. 后续真实工具和运行引擎 change 只依赖本变更公开类型，不复制公共协议。

本变更无数据库、配置和外部服务迁移。若实现未通过验证，回退本变更新增类和测试即可，不影响现有运行环境。

## 已确认依据与后续事项

- `ToolContext`、`ToolResult<T>`、`ToolStatus` 和公共时间字段以 A 的票务设计、Agent 详设和后端编码规范为准；D 的 change 已记录 B 对推荐工具、Command 和 `ToolResult<T>` 的确认，因此不构成本 change 的编码前置阻塞。
- D 的 `design.md` 仍保留推荐工具名称待确认；真实推荐工具开始前应由 B、D 修正文档记录。本变更不代替 D 选择业务工具名称。
