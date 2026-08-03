# 票务联调 JSON 夹具

本目录是 A 向 B/C 提供的票务契约示例，示例 ID、订单号和时间均为固定占位值，不对应任何真实环境数据。

## B：Agent 工具结果

- `b/query-shows-success.json`：动态场次查询成功，时效位于公共 `ToolResult`。
- `b/create-order-success.json`：确认完成后的建单成功结果。
- `b/create-order-idempotent-recovery.json`：响应丢失后按原请求标识恢复，业务数据与首次成功相同。
- `b/create-order-seat-conflict.json`：座位冲突，写工具不可自动重试，应刷新座位并重新选择。
- `b/query-order-not-found.json`：订单不存在或无权访问，不泄露资源归属。

支付不是 Agent 工具，不在 B 目录提供支付写入夹具。B 不得从这些 JSON 推导用户身份、金额或库存；实际执行仍调用 A 的公开 Application API/Tool Adapter。

## C：传统页面 REST 响应

- `c/show-list-success.json`、`c/seat-map-success.json`：场次和座位选择。
- `c/create-order-success.json`、`c/order-page-success.json`：建单、恢复和本人订单列表。
- `c/payment-success.json`、`c/electronic-ticket-success.json`：支付成功和电子票。
- `c/refund-impact-success.json`、`c/refund-success.json`、`c/alternative-shows-success.json`：退票确认、结果恢复和替代场次。
- `c/*-error.json`：座位冲突、幂等参数不一致、订单不存在和未登录。

C 必须通过公共请求层调用接口。写请求超时后复用原 `clientRequestId`、订单号或幂等键查询权威结果，不自动生成新键重放写请求。
