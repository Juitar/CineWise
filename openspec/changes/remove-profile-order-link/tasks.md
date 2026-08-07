## 1. OpenSpec 和范围

- [x] 1.1 C 确认订单页的“内容资料来源与时效”来自 `OrderContentNotice`，本次不修改。（验证：订单页和组件代码核对）
- [x] 1.2 C 完成本 change 的严格校验。（验证：`openspec validate remove-profile-order-link --strict`）

## 2. 个人中心入口

- [x] 2.1 C 删除 `/profile` 中重复的“我的订单”链接和仅服务该链接的样式。（验证：组件测试和代码核对）
- [x] 2.2 C 更新个人中心组件测试，确认订单链接不再显示，隐私说明和退出登录仍可用。（验证：`pnpm test -- src/pages/profile/index.test.tsx`）

## 3. 交付检查

- [x] 3.1 C 执行相关测试、OpenSpec 严格校验和 Git 差异检查。（验证：测试命令、`openspec validate remove-profile-order-link --strict`、`git diff --check`）
