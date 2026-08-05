# Design: frontend-admin-workbench

## 最新基线

本 change 最终基于 `origin/dev@ce7c8de`。其中管理能力来自此前已合并的 `7ce26a8` 基线，后续提交涉及购票出口、Agent action 迁移和认证邮箱验证码，没有修改管理端路由、页面或 Agent 管理契约。该基线已经完成以下管理员能力：

- `POST /api/v1/admin/auth/login`、`GET /api/v1/auth/me` 和 ADMIN 安全规则；
- C 的正式 `UserAdminQueryPort`；
- `GET /api/v1/admin/orders` 与详情接口；
- A 的 `modules/admin`、管理订单筛选、列表、分页、错误处理和详情抽屉。

因此本 change 不复制或修改订单业务实现，只修正角色 C 的管理壳层和页面。

## 路由与布局

正式管理路径固定为：

- `/admin/content`：C 的内容同步状态页；
- `/admin/orders`：A 已完成的真实管理订单页；
- `/admin/agent-runs`：C 的 Agent 脱敏轨迹页。

`/admin` 使用独立入口组件导航到 `/admin/content`。菜单、品牌链接和选中状态使用同一组路径。管理布局继续由 `.umirc.ts` 中的 `RequireAdmin` wrapper 保护，布局层不调用业务接口。

## 后端缺失能力

最新后端仍没有以下 Controller：

- `GET /api/v1/admin/content/sources`；
- `POST /api/v1/admin/content/sync`；
- `GET /api/v1/admin/agent-runs`；
- `GET /api/v1/admin/agent-runs/{runId}`。

内容管理仍采用失败关闭方式，不调用未确认接口。B 已书面确认 Agent 查询参数、分页结构、列表字段、详情节点字段和禁止返回字段，因此本次建立 `modules/admin-agent` 的类型、API、映射、Hook 和错误处理。Controller 未合入期间请求会进入 404/服务错误状态，不以 Mock 数据替代。

## 组件修改

- `.umirc.ts`：注册正式管理子路由和 `/admin` 入口页，保留最新交易路由。
- `pages/admin/index.tsx`：进入 `/admin/content`。
- `AdminSidebar`：统一菜单选中路径。
- `AdminTopBar`：保留真实昵称与登出，删除固定通知数量。
- `pages/admin/dashboard`：删除静态统计和业务记录，显示内容接口待接入状态。
- `modules/admin-agent`：实现 B 契约的 DTO、查询 URL、REST API、运行时映射、取消旧请求和错误分支。
- `pages/admin/agent-logs`：实现脱敏列表、筛选、分页、详情抽屉和节点摘要，路由使用 `/admin/agent-runs`。

## 测试方案

- 验证 `/admin` 导航到 `/admin/content`，三个菜单使用正式路径。
- 验证顶部显示认证状态中的昵称且没有固定通知数字。
- 验证内容页展示正确待接入状态，Agent 页按 B 契约展示列表和详情且不含禁止字段。
- 复用 `RequireAdmin` 现有测试覆盖匿名、USER、ADMIN 和身份检查失败。
- 运行 A 已合并的管理订单模块、页面测试，证明本 change 没有破坏真实订单接入。
- 执行 `pnpm check`；若默认全并发测试受现有用例超时影响，单独复跑失败文件并用受控并发执行全量测试，再分别执行生产构建，同时保留原始失败记录。

## 后续依赖

- B：按已确认契约补齐管理员 Agent Controller 和三份 JSON 夹具，供真实 HTTP 联调。
- D：提供内容源列表与同步任务的 OpenAPI、夹具和结果未知查询规则。
