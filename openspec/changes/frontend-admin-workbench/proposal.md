# Proposal: frontend-admin-workbench

## 背景

最新 `dev` 已合并管理员认证、真实管理订单后端、C 的用户目录端口，以及 A 的管理订单列表和详情前端。但管理菜单与路由仍使用不同路径：菜单指向 `/admin/content` 和 `/admin/agent-runs`，路由实际注册 `/admin/` 和 `/admin/agent-logs`。角色 C 的内容工作台与 Agent 轨迹页仍展示写死的演示数据，而对应 B/D 管理接口尚未实现。

## 目标

- 保留最新 `dev` 已完成的真实管理订单页面和 `modules/admin` 请求层。
- 统一管理端入口为 `/admin/content`、`/admin/orders`、`/admin/agent-runs`。
- 保留 `RequireAdmin` 身份检查，角色确认前不渲染任何管理业务页面。
- 让管理壳层只展示真实管理员昵称和可用操作，不显示固定通知数量。
- 移除角色 C 页面中的伪造内容状态、订单统计和 Agent 轨迹。
- 按 B 已确认的管理员 Agent 契约接入列表、筛选、分页和详情；Controller 未合入期间显示安全错误状态。
- D 接口尚未提供且契约未确认时继续显示明确的待接入状态，不猜测内容同步 DTO。

## 非目标

- 不修改 A 已完成的管理订单 DTO、API、Hook、筛选、列表和详情抽屉。
- 不实现 B 的管理员 Agent 轨迹后端 Controller 和夹具，只消费 B 已明确确认的 DTO。
- 不实现 D 的内容源、同步任务后端接口或自行推测 DTO。
- 不新增用户管理、运营统计、导出、订单写操作、Agent 配置或排期维护。
- 不修改已确认的统一 `/login` 登录流程。

## Owner 与协作

C 负责管理端壳层、路由、权限保护、内容工作台、Agent 轨迹前端和测试。A 已完成 `/admin/orders` 真实接口接入；B 已确认 Agent 管理契约并负责补齐 Controller 与夹具；D 仍需提供内容源列表与同步任务接口。

## 验收结果

- ADMIN 从 `/admin` 进入 `/admin/content`，三个管理菜单均能打开已注册页面。
- `/admin/orders` 继续使用最新 `dev` 的真实查询、筛选、分页和详情，不退回 Mock 数据。
- USER 和匿名用户仍由 `RequireAdmin` 阻止，角色检查完成前不渲染管理页面。
- 内容页不出现写死的统计和业务记录；Agent 轨迹页只显示 B 契约允许的脱敏列表与节点摘要。
- 格式、Lint、类型、管理员范围测试、限并发全量测试、生产构建、OpenSpec 严格校验和 Git 检查通过；默认全并发测试若受现有 A 用例超时影响，必须记录具体用例和复跑证据。
