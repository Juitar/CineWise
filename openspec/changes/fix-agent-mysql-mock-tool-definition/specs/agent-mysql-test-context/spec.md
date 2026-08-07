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
