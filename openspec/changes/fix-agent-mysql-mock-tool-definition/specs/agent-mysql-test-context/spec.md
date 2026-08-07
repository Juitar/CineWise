## ADDED Requirements

### Requirement: MySQL 集成测试的推荐执行器 mock 提供完整定义
`AgentPersistenceMySqlIntegrationTest` 的 `RankMoviePlanExecutionAdapter` mock SHALL 在 Spring ApplicationContext 初始化和每次 Mockito reset 后返回 `rankMoviePlan` 的目标名称及非空 `ToolDefinition`。

#### Scenario: Spring 初始化注册推荐执行器
- **WHEN** MySQL 集成测试创建 `MultiToolSupervisor`
- **THEN** `AgentToolExecutorRegistry` 接收到 `AgentToolDefinitions.rankMoviePlan()`，且不会因工具定义为空失败

#### Scenario: reset 后执行推荐测试
- **WHEN** `@BeforeEach` 重置推荐执行器 mock
- **THEN** 测试重新配置目标名称和工具定义，后续测试可正常执行

### Requirement: MySQL 监督器测试使用当前推荐工具契约
`AgentPersistenceMySqlIntegrationTest` 的监督器计划 SHALL 提供当前 `rankMoviePlan` 必填的 `cityCode`、`date` 和 `ticketCount`，成功 mock 结果 SHALL 使用尚未过期的 `RecommendationPlanResult`。

#### Scenario: 监督器持久化完整推荐计划
- **WHEN** 测试提交有效的完整推荐计划
- **THEN** 计划通过校验、调用 mock 执行器并持久化计划标识和版本

### Requirement: 当前工具终态事件兼容 V009 存储白名单
系统 SHALL 将公开的 `tool.complete` 和 `tool.error` 以 V009 已允许的 `tool.result` 写入数据库，并在读取及重放时还原原公开事件类型；没有当前终态字段的历史 `tool.result` SHALL 保持原类型。

#### Scenario: 成功工具事件写入并重放
- **WHEN** Agent 保存包含 `degraded` 的 `tool.complete`
- **THEN** 数据库事件类型为 `tool.result`，读取后的事件类型为 `tool.complete`

#### Scenario: 失败工具事件写入并重放
- **WHEN** Agent 保存包含 `errorCode` 的 `tool.error`
- **THEN** 数据库事件类型为 `tool.result`，读取后的事件类型为 `tool.error`

#### Scenario: 读取历史工具结果
- **WHEN** 历史 `tool.result` 载荷不含 `degraded` 或 `errorCode`
- **THEN** 读取后的事件类型仍为 `tool.result`
