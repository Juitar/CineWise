# Frontend Admin Workbench Spec

## ADDED Requirements

### Requirement: 管理端使用唯一正式路由

前端 SHALL 使用 `/admin/content`、`/admin/orders` 和 `/admin/agent-runs` 作为管理菜单和页面路由。访问 `/admin` SHALL 进入内容工作台，不得跳到未注册的管理路径。

#### Scenario: ADMIN 打开管理端首页

- **WHEN** 已登录 ADMIN 访问 `/admin`
- **THEN** 前端进入 `/admin/content`
- **AND** 工作台、订单管理和 Agent 轨迹菜单均指向已注册路由

#### Scenario: ADMIN 打开真实管理订单

- **WHEN** ADMIN 点击订单管理
- **THEN** 前端进入 `/admin/orders`
- **AND** 继续使用最新 `dev` 已实现的真实订单查询、筛选、分页和详情

#### Scenario: ADMIN 点击 Agent 轨迹菜单

- **WHEN** ADMIN 从管理壳层点击 Agent 轨迹
- **THEN** 前端进入 `/admin/agent-runs`
- **AND** 不进入旧的 `/admin/agent-logs` 或 404 页面

### Requirement: 管理页面只在 ADMIN 身份确认后展示

前端 SHALL 由 `RequireAdmin` 在管理布局外层检查 `/auth/me` 恢复结果。匿名用户 SHALL 回到登录页，USER SHALL 进入 403 页面，检查失败 SHALL 提供主动重试。

#### Scenario: USER 访问管理端

- **WHEN** `/auth/me` 返回 `role=USER`
- **THEN** 前端导航到 `/403`
- **AND** 不渲染内容、订单或 Agent 管理页面

#### Scenario: 身份查询暂时失败

- **WHEN** 管理路由的身份恢复请求失败且不能判定为匿名
- **THEN** 前端显示身份检查失败和重新检查按钮
- **AND** 不发送管理业务请求

### Requirement: 角色 C 页面不得展示伪造管理数据

内容工作台和 Agent 轨迹页 SHALL 只展示后端公开 DTO 返回的脱敏数据。内容接口尚未确认时 SHALL 展示待接入状态；Agent 轨迹 SHALL 按 B 已确认的契约查询，不得展示写死的统计、业务编号、影片影院、运行步骤或工具结果。

#### Scenario: 内容管理接口尚未提供

- **GIVEN** 最新后端没有 `GET /api/v1/admin/content/sources`
- **WHEN** ADMIN 打开 `/admin/content`
- **THEN** 页面说明内容源列表和同步接口待 D 提供
- **AND** 不展示模拟同步状态、更新时间或数据量

#### Scenario: 查询 Agent 管理列表

- **WHEN** ADMIN 打开 `/admin/agent-runs` 或修改 `status/userKeyword/startedFrom/startedTo/page/size`
- **THEN** 前端调用 `GET /api/v1/admin/agent-runs` 并展示 `PageResult<AdminAgentRunSummary>`
- **AND** 查询条件写入 URL，runId、planId 按字符串处理

#### Scenario: 查看 Agent 运行详情

- **WHEN** ADMIN 选择一个列表中的 runId
- **THEN** 前端调用 `GET /api/v1/admin/agent-runs/{runId}` 并展示脱敏节点摘要
- **AND** 不展示思维过程、系统提示、原始工具参数、槽位原文、ToolContext、凭据、精确位置、完整第三方响应或异常堆栈

#### Scenario: Agent 管理 Controller 尚未合入

- **GIVEN** B 已确认契约但后端接口返回 404 或服务错误
- **WHEN** 前端查询列表或详情
- **THEN** 页面显示不存在或暂不可用状态和 traceId
- **AND** 只读请求允许管理员主动重试，不展示模拟数据

### Requirement: 管理壳层不伪造通知状态

管理端顶部 SHALL 展示服务端确认的当前管理员昵称和可用的退出操作。没有正式通知接口时 SHALL NOT 展示固定通知数量或假通知入口。

#### Scenario: 管理员查看顶部区域

- **GIVEN** `/auth/me` 返回管理员昵称
- **WHEN** 管理壳层渲染顶部区域
- **THEN** 页面展示该昵称和退出菜单
- **AND** 不展示写死的通知数量
