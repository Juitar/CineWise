## Context

认证壳层已经在应用启动时调用 `GET /api/v1/auth/me`，并把 `CurrentUser` 保存在 `AuthProvider` 的运行时状态中。`/profile` 已由 `RequireAuth` 保护，但当前页面没有读取认证状态，而是展示与后端无关的静态数据。桌面顶部菜单已经调用 `AuthProvider.logout()`，移动端没有对应入口。

后端 `profile` 包目前只有分层占位文件，尚未提供资料编辑、画像标签或个性化开关接口。本 change 只能使用已实现的认证摘要和幂等退出接口。

## Decisions

### 1. 当前用户只读取 AuthProvider

个人中心直接读取 `AuthProvider.currentUser`，不重复调用 `/api/v1/auth/me`，也不创建第二份用户缓存。展示字段只使用公开 `CurrentUser` 的 `nickname`、`emailMasked`、`emailVerified`、`status` 和 `privacyPolicyVersion`；不显示完整邮箱、Cookie、JWT 或内部会话字段。

### 2. 页面只保留真实可用功能

删除写死的订单、观影记录、画像标签、推荐语和无实际动作的设置入口。订单由 A 的 `/orders` 页面负责；画像、资料编辑和个性化开关等待对应后端接口后再通过独立 OpenSpec 开发。本版本不提供账号删除功能。

### 3. 退出行为放入共享认证 Hook

在 `modules/auth` 新增退出 Hook，统一管理提交中状态、调用 `AuthProvider.logout()` 和跳转 `/login`。个人中心和桌面顶部菜单都使用该 Hook，避免两处各自维护防重复提交与跳转逻辑。

`AuthProvider.logout()` 已约定无论请求成功还是失败都清理浏览器内的认证状态。共享 Hook 捕获请求错误后仍进入登录页，不在页面保留可能包含旧身份的内容；服务端接口本身幂等，但前端不会自动重复提交请求。

### 4. 使用单份响应式页面

个人中心使用同一份语义化 JSX 和 CSS 响应式布局。移动端退出按钮触控高度不小于 44px；PC 端保留顶部菜单入口，同时在个人中心提供相同操作。

## 状态处理

| 状态                       | 页面行为                                                |
| -------------------------- | ------------------------------------------------------- |
| 已认证且存在 `currentUser` | 展示真实账号摘要和退出按钮                              |
| 已认证但摘要意外为空       | 展示无法读取资料提示，不伪造默认用户                    |
| 正在退出                   | 按钮显示“正在退出”并禁用，忽略后续点击                  |
| 退出成功                   | 清理认证状态并替换进入 `/login`                         |
| 退出请求失败               | 认证状态仍按现有 Provider 规则清理，并替换进入 `/login` |

## Risks / Trade-offs

- 当前版本不展示 PRD 中完整的画像、订单和账号管理能力，但不会用静态数据制造已经实现的假象。
- 退出请求失败时服务端 Cookie 可能尚未失效；本 change 沿用现有认证 Provider 的安全清理行为，不改变认证接口恢复规则。

## 测试方案

- 组件测试验证服务端字段展示、未实现内容不出现、退出防重复和失败后跳转。
- Playwright 在桌面和移动项目验证个人中心退出，退出后再次访问受保护页面会进入登录页。
- 执行 `pnpm check`、`openspec validate frontend-profile-minimum --strict` 和 `git diff --check`。

## 回退方式

回退本 change 的前端和 OpenSpec 文件即可；不涉及后端、数据库或数据迁移。
