## 1. 契约与范围

- [x] 1.1 复用 C 的原生语义化面包屑模式，不修改 C 的公共组件、路由或布局。
- [x] 1.2 冻结 A 页面规范路径、当前项不可点击、禁止 `history.back()` 和交易页面内容降级展示规则。

## 2. 前端实现

- [x] 2.1 A 新增交易面包屑展示组件和单测。
- [x] 2.2 A 将场次、选座、确认订单、订单、订单详情、支付、支付结果、电子票和退票页面替换为规范面包屑。
- [x] 2.3 A 将订单内容资料提示收敛为仅在不可用时展示的紧凑降级说明。

## 3. 验证

- [x] 3.1 更新页面和组件测试，覆盖业务路径、当前项不可点击和内容降级。
- [x] 3.2 等价分步执行 `format:check`、`lint`、`typecheck`、单 worker `test` 和 `build:verify`，并通过 `openspec validate ticketing-breadcrumb-navigation --strict` 与 `git diff --check`。
