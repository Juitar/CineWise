## ADDED Requirements

### Requirement: 候选计划和运行计划必须使用固定结构
系统 SHALL 使用包含 `planId`、`version` 和节点列表的类型化候选计划。候选节点 SHALL 只包含模型可提出的 `nodeId`、`type`、`targetName`（适用时）、`inputRefs`、`dependsOn`、`failurePolicy` 和受保护写操作适用的 `businessParameterHash`；节点类型 MUST 限于 `ASK_USER`、`CALL_TOOL`、`COMPUTE`、`VALIDATE`、`CONFIRM_ACTION` 和 `RENDER_RESULT`。候选计划通过校验后，服务端 SHALL 生成运行计划；运行节点额外包含初始 `status=PENDING`、`requiresConfirmation`、`autoSkipped=false`、可空 `skipReason`、可空 `skipSourceNodeId` 和脱敏 `slotSnapshot`。本 change 只定义这些运行字段和初始值，不实现节点调度或自动跳过。

#### Scenario: 校验合法候选计划
- **WHEN** 候选计划版本不小于 1、节点不超过 12 个且所有节点字段合法
- **THEN** 系统接受其固定结构并继续后续规则校验

#### Scenario: 将合法候选计划转换为运行计划
- **WHEN** 候选计划通过全部结构和业务规则校验
- **THEN** 系统生成由服务端填充初始执行状态的运行计划，模型不能直接写入这些状态字段
- **AND** 每个运行节点使用初始状态、确认标记、跳过字段和槽位快照

#### Scenario: 拒绝超限或未知节点
- **WHEN** 候选计划版本小于 1、节点超过 12 个或包含未知节点类型
- **THEN** `PlanSchemaValidator` 返回结构化校验错误
- **AND** 候选计划不得进入执行阶段

### Requirement: 节点标识和依赖必须可安全执行
计划中的 `nodeId` MUST 唯一，`dependsOn` MUST 只引用同一计划内节点且依赖图 MUST 无环。节点输入 MUST 只引用已声明槽位或其上游节点结果，且引用类型必须与目标工具字段类型一致。

#### Scenario: 拒绝循环依赖
- **WHEN** 两个或多个节点形成直接或间接循环依赖
- **THEN** `PlanSchemaValidator` 拒绝整个候选计划并指出相关节点

#### Scenario: 拒绝非法输入引用
- **WHEN** 节点引用不存在的槽位、非上游节点结果或类型不匹配的结果
- **THEN** `PlanSchemaValidator` 拒绝该计划并指出字段路径

### Requirement: 工具节点必须符合注册定义
每个 `CALL_TOOL` 节点的 `targetName` SHALL 存在于 `ToolRegistry`。计划 SHALL 提供工具声明的全部必填输入，并遵守工具的只读属性、超时和失败策略。

#### Scenario: 校验已注册只读工具
- **WHEN** `CALL_TOOL` 引用已注册只读工具且必填输入均可由槽位或上游结果提供
- **THEN** `PlanSchemaValidator` 接受该工具节点

#### Scenario: 拒绝未知或缺参工具
- **WHEN** `CALL_TOOL` 引用未知工具或缺少任一必填输入
- **THEN** `PlanSchemaValidator` 拒绝候选计划
- **AND** 不调用工具、不尝试按名称查找任意 Bean 或方法

### Requirement: 受保护写操作必须经过确认
写工具节点的依赖中 MUST 包含且只能包含一个与相同业务参数摘要关联的 `CONFIRM_ACTION` 节点；它仍可以依赖必要的 `VALIDATE` 或其他上游节点，并使用 `FAIL` 失败策略。候选计划 MUST NOT 跳过受保护写操作前的 `VALIDATE` 或 `CONFIRM_ACTION`。

#### Scenario: 校验合法写工具节点
- **WHEN** 写工具节点的依赖中有且只有一个已声明的确认节点，双方业务参数摘要一致、同时包含必要上游校验且失败策略为 `FAIL`
- **THEN** `PlanSchemaValidator` 接受该写工具结构

#### Scenario: 拒绝绕过确认的写工具
- **WHEN** 写工具缺少确认节点、依赖多个确认节点、参数摘要不一致或配置自动重试策略
- **THEN** `PlanSchemaValidator` 拒绝整个候选计划

### Requirement: Mock 模型必须返回可复现候选计划
开发和测试环境 SHALL 通过 `ModelGateway` 接口使用 `MockModelGateway`。相同 `clientRequestId`、相同输入和相同允许工具列表 MUST 返回相同的候选计划和节点顺序，所有 Mock 结果仍 MUST 经过 `PlanSchemaValidator`。

#### Scenario: 重复生成 Mock 计划
- **WHEN** 使用相同请求标识、输入和工具白名单重复调用 `MockModelGateway`
- **THEN** 系统返回相同 `planId`、版本、节点内容和节点顺序

#### Scenario: Mock 计划校验失败
- **WHEN** Mock 夹具产生不符合计划规则的候选结果
- **THEN** 系统返回结构化校验失败
- **AND** 不因为结果来自 Mock 而绕过校验

### Requirement: 模型供应商细节不得进入计划领域
`ModelGateway` SHALL 只暴露类型化计划和回复请求/响应，供应商 SDK、鉴权、HTTP 响应和原始模型输出 MUST 留在后续基础设施适配器中。

#### Scenario: 使用 Mock 模型网关
- **WHEN** 计划用例调用 `ModelGateway` 获取候选计划
- **THEN** 领域和应用代码只依赖 Agent 自有类型
- **AND** 不依赖外部模型 SDK、Web 类型或供应商响应类型
