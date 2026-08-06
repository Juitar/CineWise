## ADDED Requirements

### Requirement: 仅执行已注册的类型化工具
系统 SHALL 通过 `ToolRegistry` 和类型化 executor 注册项选择工具；计划中的工具名、Command 类型、输入引用和白名单任一不匹配时，不得调用业务模块或反射目标。

#### Scenario: 已注册的多个只读工具按依赖执行
- **WHEN** 候选计划通过校验并包含多个依赖关系满足的已注册只读工具节点
- **THEN** 系统按状态机选择顺序执行每个节点一次，并返回各节点的统一 `ToolResult`

#### Scenario: 未注册工具或参数不匹配
- **WHEN** 计划引用未登记工具、未知输入或错误输入类型
- **THEN** 系统拒绝计划且不执行任何工具

### Requirement: 工具失败重规划不重放写操作
系统 SHALL 只在已执行的只读工具返回 `FAILED` 且 `replanSuggested=true` 时，在服务器限制内生成新计划；写工具、超时未知结果、SSE 重连和断线不得自动重试。

#### Scenario: 只读工具建议重新规划
- **WHEN** 已注册只读工具返回失败并建议重新规划，且未达到计划版本上限
- **THEN** 系统请求并校验新计划，保留原运行与客户端请求标识，并将新版本交给 CAS 持久化

#### Scenario: 确认后的建单结果未知
- **WHEN** `createOrder` 调用超时或连接中断导致结果未知
- **THEN** 系统使用原 actionId 和幂等键查询结果，不创建新 actionId 或自动再次建单

### Requirement: 未确认业务接口可先接入 Mock
系统 SHALL 为 `queryAvailableDates`、`queryShows`、`querySeats` 提供统一注册接口、Mock executor 和夹具；其中 `querySeats` 仅作为可插拔扩展位，不属于本 change 的关键路径验收。未接入正式公开 API、DTO 和字段前，不得实现猜测的生产 Command 或直接访问业务内部层。

#### Scenario: 正式业务接口尚未提供
- **WHEN** B 装配 Agent 核心流程且 A 的查询 Tool 尚未注册生产实现
- **THEN** 系统只允许测试夹具中的 Mock 注册，生产白名单不暴露该工具

#### Scenario: 选座走购票页
- **WHEN** 场次结果已由工具校验且用户进入选座阶段
- **THEN** Agent 输出 `card` + `BUSINESS_INTENT`/`SELECT_SEATS`，携带已确认的 `businessRef.showId`，不调用 `querySeats`
