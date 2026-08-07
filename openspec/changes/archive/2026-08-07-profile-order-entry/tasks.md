## 1. OpenSpec 和范围

- [x] 1.1 C 确认 `/orders` 已由 `RequireAuth` 保护，且本次不修改 A 的订单接口和业务逻辑。（验证：`.umirc.ts`、订单页面与 API 文件核对）
- [x] 1.2 C 完成本 change 的严格校验。（验证：`openspec validate profile-order-entry --strict`）

## 2. 前端入口

- [x] 2.1 C 在 `/profile` 为已登录用户增加跳转 `/orders` 的“我的订单”入口，资料缺失时不显示。（验证：组件测试）
- [x] 2.2 C 在桌面公共导航为已登录用户增加订单入口，并保持匿名状态不显示。（验证：组件测试）
- [x] 2.3 C 在移动端底部导航为已登录用户增加订单入口，并保持小屏可访问和匿名状态不显示。（验证：组件测试）

## 3. 测试和交付

- [x] 3.1 C 增加或更新组件测试，覆盖个人中心跳转、匿名隐藏、桌面和移动端入口。（验证：`pnpm test`）
- [x] 3.2 C 执行前端完整检查、OpenSpec 严格校验和 Git 差异检查。（验证：`pnpm check`、`openspec validate profile-order-entry --strict`、`git diff --check`）
