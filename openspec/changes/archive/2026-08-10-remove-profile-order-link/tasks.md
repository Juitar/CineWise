## 1. OpenSpec 和范围

- [x] 1.1 C 确认订单页的“内容资料来源与时效”来自 `OrderContentNotice`，本次不修改。（验证：订单页和组件代码核对）
- [x] 1.2 C 更新本 change 并完成严格校验。（验证：`openspec validate remove-profile-order-link --strict`）

## 2. 个人中心和公共导航入口

- [x] 2.1 C 在 `/profile` 恢复“我的订单”链接，跳转现有 `/orders` 页面。（验证：组件测试）
- [x] 2.2 C 已删除桌面侧边栏的订单菜单项；本次继续删除桌面用户菜单和移动端底部导航的订单入口及仅服务该入口的逻辑。（验证：组件测试）
- [x] 2.3 C 更新组件测试，覆盖个人中心进入 `/orders` 和公共导航不显示订单入口。（验证：`pnpm test`）

## 3. 交付检查

- [x] 3.1 C 执行相关测试、OpenSpec 严格校验和 Git 差异检查。（验证：`pnpm check`、`openspec validate remove-profile-order-link --strict`、`git diff --check`）
