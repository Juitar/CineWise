## ADDED Requirements

### Requirement: 工具调用必须使用统一公共上下文
系统 SHALL 使用 `ToolContext` 传递 `String runId`、`String nodeId`、`String targetName`、`List<String> inputRefs`、正数 `long deadlineMs`、`String traceId`、`String clientRequestId`、`String idempotencyKey` 和 `Long stateVersion`。除 `clientRequestId`、`idempotencyKey` 和 `stateVersion` 可按工具定义为空外，其他字段 MUST 非空；写工具的 `clientRequestId` 和 `idempotencyKey` MUST 非空。上下文 MUST NOT 包含邮箱、邀请码、完整订单明细、精确坐标、模型原始上下文、用户角色、金额或业务状态。

#### Scenario: 构造只读工具上下文
- **WHEN** 规划器为已注册的只读工具构造调用上下文
- **THEN** 系统接受完整的运行、节点、目标、输入引用、时限和追踪字段
- **AND** 允许 `clientRequestId`、`idempotencyKey` 和 `stateVersion` 按工具定义为空

#### Scenario: 构造写工具上下文
- **WHEN** 规划器为写工具构造调用上下文
- **THEN** 系统要求稳定的 `clientRequestId` 和 `idempotencyKey`
- **AND** 恢复查询必须复用原上下文，不得生成新的幂等键

### Requirement: 工具必须返回统一类型化结果
系统 SHALL 使用 `ToolResult<T>` 返回非空 `ToolStatus status`、类型化 `T data`、可空 `Integer errorCode`、`boolean retryable`、`boolean replanSuggested`、可空 `String suggestedNextAction`、`boolean degraded`、可空 `String fallbackType`、可空 `Long stateVersion`、可空 `Instant dataAt` 和可空 `Instant expiresAt`。成功结果的 `errorCode` MUST 为空；动态业务数据可供后续节点使用时，`dataAt` 和 `expiresAt` MUST 非空。业务字段 MUST 只存在于类型化 `data` 中，公共结果 MUST NOT 增加 `dataTime`、`recommendReplan` 或 `suggestedNextStep` 等同义字段。

#### Scenario: 返回成功的动态结果
- **WHEN** 工具返回可用于后续计划的动态业务数据
- **THEN** `ToolResult<T>` 使用已定义的公共字段和类型化 `data`
- **AND** 动态结果包含有效的 `dataAt` 和 `expiresAt`

#### Scenario: 返回结构化失败
- **WHEN** 工具无法完成调用
- **THEN** `ToolResult<T>` 返回稳定数值 `errorCode`、是否可重试、是否建议重规划和建议下一动作
- **AND** 不在 `data` 或错误字段中暴露原始第三方响应、模型上下文或敏感参数

### Requirement: 工具注册表必须是服务端白名单
系统 SHALL 通过 `ToolDefinition` 声明工具名称、Command 类型、结果类型、只读属性、超时、幂等要求、必填输入和允许错误码，并由 `ToolRegistry` 建立不可变白名单。节点的 `failurePolicy` 属于计划节点，不属于工具定义。重复名称、空名称或元数据不完整的定义 MUST 被拒绝。

#### Scenario: 注册合法工具
- **WHEN** 应用启动时提供名称唯一且元数据完整的工具定义
- **THEN** `ToolRegistry` 可以按精确名称查询定义
- **AND** 返回的注册表不能由调用方修改

#### Scenario: 查询未知工具
- **WHEN** 模型计划或用户输入引用未注册的工具名称
- **THEN** `ToolRegistry` 返回未找到结果
- **AND** 系统不得把该字符串解释为 Bean 名、类名或可执行方法

#### Scenario: 注册重复工具名称
- **WHEN** 两个工具定义使用相同名称
- **THEN** 注册表创建失败并指出重复名称

### Requirement: 工具不得控制主控计划
工具 SHALL 只处理类型化 Command 并返回 `ToolResult<T>`，MUST NOT 生成或修改计划、追问用户、调用其他工具、发布 SSE 或修改主会话。

#### Scenario: 调用基础工具协议
- **WHEN** `ToolRouter` 调用一个已注册工具
- **THEN** 工具只接收公共上下文和类型化 Command，并返回类型化结果
- **AND** 主控仍是唯一的计划和最终回复生成者
