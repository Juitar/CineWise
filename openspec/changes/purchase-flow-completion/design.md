# Design: 购票交易页面出口

## 路由与权威数据

- 建单成功仅使用 `OrderResponse.orderNo` 构造 `/payments/{orderNo}` 与 `/orders/{orderNo}`；不从 URL、标题或本地缓存推断订单号。
- 支付结果只在 `PaymentResponse.paymentStatus === SUCCESS` 且 `ticketId` 为非空字符串时，允许构造 `/tickets/{ticketId}`。
- 所有业务 ID 保持字符串并逐项 `encodeURIComponent`；金额和时间继续由既有服务端快照展示。

## 状态边界

- `RESULT_UNKNOWN`、`PROCESSING`、`PENDING_PAYMENT`、`INITIALIZED`、`CANCELLED`、`EXPIRED`、`REFUNDED` 不显示电子票入口。
- 入口只改变前端导航，不触发新的建单、支付、查询或退款 POST；支付结果未知仍只执行既有只读查询。
- 支付成功但票号尚不可用时保留“查看订单/重新查询结果”等既有安全出口，不伪造 ticketId。

## 分层与协作

- `modules/order/routes.ts` 集中管理订单、支付和电子票路径。
- `pages/orders/confirm`、`pages/payments/result` 只编排服务端快照与 feature callback。
- `features/order-create-success`、`features/payment-result` 是展示层；前端展示 AI 只接收明确 Props 和 callback，不能请求 API 或调用 history。

## 验证

- 路由工具单元测试验证字符串 ID 与 URL 编码。
- 页面/E2E 覆盖建单成功的两个出口、支付成功有票号的电子票入口、无票号和 `RESULT_UNKNOWN` 时入口不可见。
- 运行 `pnpm check`、必要 E2E、OpenSpec strict 和 Git 差异检查。
