## Context

`MultiToolSupervisor` 在 Spring 初始化时将所有 `AgentToolExecutor` 交给 `AgentToolExecutorRegistry`，注册表要求每个执行器返回非空 `ToolDefinition`。MySQL 集成测试以 Mockito mock 替换推荐执行器，但只固定了 `targetName()`；`definition()` 的默认 `null` 导致上下文初始化失败。`Mockito.reset(...)` 同样会清除初始固定行为。测试中的监督器计划仍使用旧推荐输入和结果类型，必须同步到当前工具定义，否则计划会在执行前被拒绝。

## Goals / Non-Goals

**Goals:**

- 让测试 mock 在创建上下文和每个测试开始后都提供真实的 `AgentToolDefinitions.rankMoviePlan()`。
- 让监督器测试夹具满足当前 `rankMoviePlan` 的必填输入，并返回类型化完整推荐结果。
- 通过现有 MySQL 集成测试验证 Spring 上下文可正常启动。

**Non-Goals:**

- 不修改生产工具注册表、MySQL、Flyway、配置或业务逻辑。
- 不调整 Agent 工具协议。

## Decisions

- 在 mock Bean 创建和 `@BeforeEach` 的 reset 后，显式 stub `targetName()` 与 `definition()`。这是 Mockito reset 的必要恢复步骤，且直接复用生产白名单定义，避免测试复制字段。
- 测试计划直接引用当前白名单必填字段，成功结果使用最小 `RecommendationPlanResult`。测试只验证 B 的监督器持久化，不依赖 D 已废弃的固定推荐服务。
- 仅检查同一个测试配置中的其他 `AgentToolExecutor` mock；不扩大到无关测试重构。

## Risks / Trade-offs

- [未来新增 mock 执行器仍可能漏配 definition] → 复核本测试类中全部 `AgentToolExecutor` mock，并在本次测试中保留上下文启动覆盖。
- [本地未提供 CI MySQL] → 运行定向编译/单元测试并如实保留 MySQL CI 未验证状态。
