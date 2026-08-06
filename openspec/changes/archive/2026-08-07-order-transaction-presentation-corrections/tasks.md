# Tasks: order-transaction-presentation-corrections

## 1. 契约与模块逻辑

- [x] 1.1 冻结支付期限、状态、时间和座位编号展示规则；验证：OpenSpec strict 通过。（Owner: A）
- [x] 1.2 实现权威支付期限解析、倒计时 Hook 与边界测试。（Owner: A）
- [x] 1.3 集中订单、支付、电子票和退款状态中文展示映射并补齐类型测试。（Owner: A）

## 2. 展示与接线

- [x] 2.1 修正支付面板、订单详情和电子票展示组件的支付期限与座位编号语义。（Owner: A / 前端展示 AI）
- [x] 2.2 页面接入统一时间、状态和支付期限逻辑，不改变写操作与 RESULT_UNKNOWN 恢复。（Owner: A）
- [x] 2.3 完成小于 1024px 的移动展示调整，不修改 C 的公共壳层。（Owner: A / 前端展示 AI）

## 3. 验证与交付

- [x] 3.1 更新模块、组件和页面测试，覆盖合法、缺失、非法、截止和服务端状态优先场景。（Owner: A）
- [x] 3.2 执行 `pnpm check`、必要 E2E、OpenSpec strict 和 `git diff --check`。（Owner: A）
- [x] 3.3 已完成与前端展示分支的最终接线审查，并提交独立 PR #70。（Owner: A）
