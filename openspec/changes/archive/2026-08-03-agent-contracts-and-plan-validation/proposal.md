## 背景

当前 `agent` 模块只有分层占位，没有可供规划器、执行器和 A/D 工具适配器共同使用的基础类型。先实现不依赖数据库和外部模块的工具协议、计划模型与校验能力，可以让 B 独立开发和测试，并为后续运行引擎、SSE 和真实工具接入提供稳定输入。

## 本次变更

- 新增类型化工具公共协议，定义 `ToolContext`、`ToolResult<T>`、工具状态、建议动作和工具元数据。
- 新增工具白名单注册能力；计划只能引用已注册工具，工具不得直接生成计划、追问用户或调用其他工具。
- 新增计划、节点、依赖、输入引用和失败策略模型，以及 `PlanSchemaValidator` 的结构校验规则；当前阶段拒绝写工具进入执行计划。
- 新增 `ModelGateway` 边界和确定性的 `MockModelGateway`，相同输入在固定条件下返回相同计划。
- 增加覆盖正常、非法计划、未知工具、循环依赖、超限和确定性 Mock 的单元测试。
- 暂不实现数据库、Redis、SSE、Controller、真实票务/推荐工具、JWT 认证、前端页面、确认动作和写工具执行。

## 能力范围

### 新增能力

- `agent-tool-contracts`: 定义 Agent 工具调用公共上下文、统一结果、工具元数据和白名单注册规则。
- `agent-plan-validation`: 定义计划和节点结构、依赖及输入引用校验、工具白名单校验和确定性 Mock 模型行为。

### 修改的已有能力

无。当前仓库尚无 Agent 主规格。

## 影响范围

- 代码范围：`backend/src/main/java/com/miaoyu/ticket/agent/**` 及对应单元测试。
- 跨模块影响：A/D 后续工具适配器需要使用本变更定义的 `ToolContext` 和 `ToolResult<T>`；字段与类型以 A 的正式设计和 D 已记录的确认结论为依据。真实 Command 和业务结果 DTO 仍由对应 Owner 在各自变更中实现；本 change 完成实现后按正常开发流程提交 PR 到 `dev`。
- 接口影响：本变更不新增 REST、SSE 或数据库接口，不修改现有 Controller DTO。
- 数据与环境：不新增 Flyway、MySQL、Redis、Docker 或外部模型配置。
- 依据：`CineWise-Docs` 中 Agent 智能决策中心设计的工具协议、计划 Schema、白名单路由和 Mock 模型要求。
