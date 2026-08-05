# Tasks: purchase-flow-completion

- [x] 1.1 冻结 `orderNo`、`ticketId`、状态和 Owner 边界，不新增写请求或跨模块访问。（Owner: A）
- [x] 1.2 增加订单、支付、电子票路由构造工具与单元测试。（Owner: A）
- [x] 2.1 接线建单成功页“去支付”“查看订单”出口，并补 E2E。（Owner: A）
- [x] 2.2 前端展示 AI 实现 `OrderCreateSuccess` 与 `PaymentResult` 的纯展示 Props、视觉和组件测试。（Owner: 前端展示 AI）
- [x] 2.3 接线支付成功“查看电子票”出口，处理无 `ticketId` 与非成功状态。（Owner: A；Blocked by 2.2）
- [x] 3.1 执行 `pnpm check`、完整 E2E、OpenSpec strict 和差异检查。（Owner: A）
- [x] 3.2 完成差异审查后提交独立 PR。（Owner: A）
