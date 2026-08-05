# Tasks: frontend-admin-workbench

## 1. 最新基线核对

- [x] 1.1 C 获取最新 `origin/dev@ce7c8de`；验证：分支包含 PR #69 管理订单前端、PR #71 用户目录端口，以及开发期间追加且不修改管理前端的购票、Agent action 和认证提交。
- [x] 1.2 C 核对最新管理员能力；验证：认证与管理订单前后端已完成，内容同步接口仍不存在；B 已确认管理员 Agent 契约但 Controller 和夹具待实现。
- [x] 1.3 C 严格校验本 change；验证：`openspec validate frontend-admin-workbench --strict` 通过。

## 2. 管理壳层

- [x] 2.1 C 统一 `/admin/content`、`/admin/orders`、`/admin/agent-runs` 路由和菜单；验证：三个菜单均进入已注册页面，`/admin` 进入内容页。
- [x] 2.2 C 清理顶部固定通知数量并保留真实昵称和退出；验证：昵称来自 `AuthProvider`，页面没有模拟通知数量。
- [x] 2.3 C 复核 `RequireAdmin` 保护范围；验证：匿名、USER、ADMIN、身份检查失败测试通过。

## 3. 角色 C 管理页面

- [x] 3.1 C 移除内容工作台静态统计、订单和 Agent 数据，展示 D 接口待接入状态；验证：页面不含模拟业务记录。
- [x] 3.2 C 移除 Agent 轨迹页静态数据，并按 B 契约实现列表、筛选、分页、详情和节点摘要；验证：页面不含模拟 runId、禁止字段或原始工具参数。
- [x] 3.3 C 增加管理入口、菜单、顶部、内容页和 Agent 页单元测试；验证：路由、真实昵称、Owner、接口提示和无模拟数据断言通过。
- [x] 3.4 C 新增 `modules/admin-agent` 类型、API、查询 URL、映射、Hook 和错误处理；验证：模块与页面 5 个测试文件、12 个用例通过。

## 4. 最新订单能力回归

- [x] 4.1 C 运行 A 的管理订单模块、组件和页面测试；验证：真实查询、筛选、分页、详情和错误状态未被本 change 破坏。
- [x] 4.2 C 检查变更范围；验证：不修改 `modules/admin`、管理订单 features 和 `pages/admin/orders` 业务实现。

## 5. 验证与交付

- [x] 5.1 C 运行前端完整检查；验证：最终 `ddd4fcf` 基线上格式、Lint、类型通过，B 契约模块与页面 5 文件/12 用例通过，限 4 进程全量 59 文件/225 用例通过，生产构建通过。默认 `pnpm check` 此前连续两次仅有 A 的 `pages/admin/orders/index.test.tsx` 同一用例超过 5 秒；该文件单独 3/3 通过，未修改 A 的测试超时。
- [x] 5.2 C 运行 OpenSpec 严格校验、`git diff --check` 和 Git 状态检查。
- [x] 5.3 C 记录 B/D 剩余依赖；验证：B 待补 Controller 与三份夹具，D 待确认内容管理契约。
