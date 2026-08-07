## ADDED Requirements

### Requirement: 管理员可以分页查询脱敏运行列表
系统在字段来源完成确认后 SHALL 在 `GET /api/v1/admin/agent-runs` 返回 `Result<PageResult<AdminAgentRunSummary>>`。接口只接受 `status`、`userKeyword`、`startedFrom`、`startedTo`、`page`、`size`；结果固定按开始时间倒序、内部 ID 倒序，返回 `total`、`page`、`size`、`records`。每条记录的字段可空性和错误摘要来源必须与 C 确认后再固定。

#### Scenario: 管理员按 C 的筛选字段查询
- **WHEN** ADMIN 请求 `status=FAILED`、有效的 ISO 8601 时间范围和 `page=1&size=20`
- **THEN** 系统只返回符合条件的脱敏运行摘要和统一分页格式
- **AND** 不接受客户端指定排序字段

#### Scenario: 用户关键词没有匹配用户
- **WHEN** ADMIN 提交合法 `userKeyword` 但 C 的公开用户目录没有返回匹配用户 ID
- **THEN** 系统返回 `total=0` 和空 `records`
- **AND** 不把空 ID 集合解释为未筛选而查询全部运行

### Requirement: 管理员可以查询单次脱敏运行详情
系统在字段来源完成确认后 SHALL 在 `GET /api/v1/admin/agent-runs/{runId}` 返回列表同名摘要字段和 `nodes`。每个节点只允许包含确认后的安全摘要字段；不得用节点类型、JSON 或事件 payload 伪造 `targetName`、`toolStatus`、错误码或错误摘要。

#### Scenario: 管理员查看存在运行
- **WHEN** ADMIN 请求存在的 `runId`
- **THEN** 系统返回该运行的安全摘要和按持久化顺序排列的步骤摘要
- **AND** 节点摘要可由 C 的 agent-logs 页面直接渲染

#### Scenario: 请求不存在运行
- **WHEN** ADMIN 请求不存在的 `runId`
- **THEN** 系统返回项目现有 404 格式和 Agent 错误码 `206005`
- **AND** 响应不伪造空运行详情

### Requirement: 访问管理运行轨迹必须限制为管理员
系统 SHALL 使用现有 `/api/v1/admin/**` 安全规则，并在 Agent Application Service 复核 `CurrentUserAccessor` 的角色。未登录请求 SHALL 保留全局 401 规则；已登录 `USER` 请求 SHALL 返回 403，且不得先查询运行、步骤或用户目录。

#### Scenario: 普通用户访问列表或详情
- **WHEN** 已登录 USER 请求列表或详情路径
- **THEN** 系统返回 403
- **AND** 不返回任何运行数据

### Requirement: 管理查询不得泄露敏感 Agent 数据
系统 SHALL 以明确 DTO 白名单返回数据，且不得返回原始 Tool 参数、步骤 JSON、模型输入/输出/思维过程、Cookie、JWT、CSRF、用户认证信息、精确坐标、距离上下文、画像标签、确认命令、座位、支付或其他敏感业务载荷。

#### Scenario: 持久化记录包含敏感内容
- **WHEN** 测试持久化数据的步骤 JSON、消息、事件或确认字段包含可识别的敏感值
- **THEN** 列表和详情 JSON 均不包含这些字段名或值
- **AND** 工具执行只显示白名单节点摘要

### Requirement: 实际运行状态不得被管理查询猜测改写
系统在接口实施后 SHALL 返回 Agent 运行持久化的实际状态。当前前端未声明的 `WAITING_LOCATION` SHALL 保持该值，不得映射或伪造为其他状态。

#### Scenario: 运行正在等待位置授权
- **WHEN** ADMIN 查询状态为 `WAITING_LOCATION` 的运行
- **THEN** 响应 `status` 为 `WAITING_LOCATION`
- **AND** 响应不包含位置、坐标或距离上下文
