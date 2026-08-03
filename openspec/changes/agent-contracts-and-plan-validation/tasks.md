## 1. 接口确认

- [x] 1.1 B 已核对 A 的正式 `ToolContext`/`ToolResult<T>` 类型定义、Agent 详设和后端编码规范；验证方式：本 change 的 spec/design 已列出字段、Java 类型、可空规则、`ToolStatus=SUCCESS/FAILED/PROCESSING` 和 `dataTime -> dataAt` 映射。（Owner：B）
- [x] 1.2 B 已核对 D change 的确认记录和 D 业务数据字段：公共结果使用 `dataAt`，业务 `dataTime`、来源和 `isExpired` 留在类型化 `data`；验证方式：D change 任务 1.3 已记录 B 确认，本 change 不新增同义公共字段。（Owner：B）
- [x] 1.3 B 已记录真实票务、推荐工具名称和 Command 不属于本 change；D 的“工具名称待确认”文档问题由真实工具适配 change 处理；验证方式：本 change 不新增任何真实领域 Command。（Owner：B）

## 2. 工具公共协议

- [x] 2.1 在 `agent.domain` 细分包实现 `ToolContext`、Command 标记、`ToolStatus` 和 `ToolResult<T>`，字段、Java 类型和可空规则与 design 一致；验证方式：`AgentContractsAndPlanValidationTest` 通过反射字段检查和写工具请求标识校验。（Owner：B）
- [x] 2.2 实现不可变 `ToolDefinition`，包含名称、Command 类型、结果类型、读写属性、超时、幂等要求、必填输入和允许错误码；验证方式：`AgentContractsAndPlanValidationTest` 覆盖非法幂等要求和不完整元数据，且计划失败策略不存入工具定义。（Owner：B）
- [x] 2.3 实现不可变 `ToolRegistry`，支持精确名称查询并拒绝重复名称；验证方式：`AgentContractsAndPlanValidationTest` 覆盖合法注册、重复名称、未知名称和外部映射修改。（Owner：B）
- [x] 2.4 增加工具协议安全测试，确认公共上下文不包含身份、金额、状态、精确位置和模型原始上下文字段，未知名称不会触发反射或 Bean 查找。（Owner：B）

## 3. 计划模型与校验

- [x] 3.1 实现候选计划、候选节点、运行计划、运行节点、固定节点类型、节点状态、失败策略和结构化校验问题类型；候选节点不得携带参数摘要、确认凭据或 `actionId`；验证方式：`AgentContractsAndPlanValidationTest` 通过反射确认候选节点不含确认字段，运行节点初始为 `PENDING` 并含确认、跳过和脱敏槽位快照字段。（Owner：B）
- [x] 3.2 实现计划基础校验：版本不小于 1、节点最多 12 个、节点 ID 唯一、字段完整且节点类型合法；验证方式：`AgentContractsAndPlanValidationTest` 覆盖通过、版本非法、重复节点和循环依赖用例。（Owner：B）
- [x] 3.3 实现依赖图校验：依赖节点存在、图无环且输入只引用已声明槽位或上游结果；验证方式：`AgentContractsAndPlanValidationTest` 覆盖循环、未知输入和非上游引用。（Owner：B）
- [x] 3.4 实现工具节点校验：目标存在于 `ToolRegistry`、必填输入齐全且输入类型匹配；验证方式：`AgentContractsAndPlanValidationTest` 覆盖合法只读工具、未知工具、未知输入和类型错误。（Owner：B）
- [x] 3.5 实现写工具保护校验：当前阶段拒绝所有非只读工具候选节点，不信任模型的参数摘要、确认凭据或 `actionId`；验证方式：`AgentContractsAndPlanValidationTest` 覆盖即使带 `VALIDATE` 和 `CONFIRM_ACTION` 的写工具仍返回 `WRITE_TOOL_NOT_SUPPORTED`，且没有运行计划。（Owner：B）
- [x] 3.6 实现 `PlanValidationResult`，一次返回稳定问题码、节点 ID 和字段路径；验证方式：`AgentContractsAndPlanValidationTest` 验证非法计划同时返回多个问题且没有运行计划。（Owner：B）

## 4. Mock 模型边界

- [x] 4.1 在 Application 层定义 `ModelGateway` 及类型化计划/回复请求响应，禁止泄露供应商 SDK、HTTP 或原始模型响应类型；验证方式：纯 Java 编译通过，完整架构测试待 5.2 执行。（Owner：B）
- [x] 4.2 在 Infrastructure 层实现版本为 `mock-plan-v1` 的 `MockModelGateway`，按固定场景、`clientRequestId`、输入和允许工具生成稳定计划；验证方式：`AgentContractsAndPlanValidationTest` 验证相同输入结果一致且实现不访问网络或当前时间。（Owner：B）
- [x] 4.3 增加 Mock 计划测试，覆盖确定性、节点顺序、工具白名单变化和非法夹具仍被 `PlanSchemaValidator` 拒绝；验证方式：`AgentContractsAndPlanValidationTest` 覆盖相同输入、白名单变化和统一校验结果。（Owner：B）

## 5. 验证与交付

- [x] 5.1 执行 Agent 相关单元测试并确认无数据库、Redis、SSE、Controller、认证和真实工具依赖；验证方式：`mvnw.cmd -Dtest=AgentContractsAndPlanValidationTest test`，10 通过、0 失败、0 跳过。（Owner：B）
- [x] 5.2 在 `backend/` 执行 `mvnw.cmd verify`，确保编译、测试、ArchUnit、Checkstyle、SpotBugs 和 JaCoCo 全部通过；验证方式：25 个测试中 24 通过、0 失败、1 个既有 MySQL 集成测试因环境未启用跳过。（Owner：B）
- [x] 5.3 执行 `openspec validate agent-contracts-and-plan-validation --strict` 并检查实现与 proposal、specs、design、tasks 一致；验证方式：严格校验通过。（Owner：B）
- [x] 5.4 提交前执行 `git diff --check` 和 `git status`，确认没有无关格式化、构建产物、真实配置或跨模块实现；验证方式：`git diff --check` 无输出，工作区只含本 PR 评审修正文件。（Owner：B）
