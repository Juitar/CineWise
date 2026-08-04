# 票务联调 JSON 夹具

本目录是 A 向 B/C 提供的票务契约示例，示例 ID、订单号和时间均为固定占位值，不对应任何真实环境数据。

## B：Agent 工具结果

- `b/query-shows-success.json`：动态场次查询成功，时效位于公共 `ToolResult`。
- `b/create-order-success.json`：确认完成后的建单成功结果。
- `b/create-order-idempotent-recovery.json`：响应丢失后按原请求标识恢复，业务数据与首次成功相同。
- `b/create-order-seat-conflict.json`：座位冲突，写工具不可自动重试，应刷新座位并重新选择。
- `b/query-order-not-found.json`：订单不存在或无权访问，不泄露资源归属。

支付不是 Agent 工具，不在 B 目录提供支付写入夹具。B 不得从这些 JSON 推导用户身份、金额或库存；实际执行仍调用 A 的公开 Application API/Tool Adapter。

## A 的票务页面 REST 夹具，C 负责公共请求层接入

`/movies/**`、`/cinemas/**` 与购票入口由 C 负责；本目录的 `/shows`、选座、订单、支付、电子票、退票页面及其 REST 契约均由 A 负责。C 只通过公共请求层接入这些接口，不维护第二套业务 DTO 或请求封装。

所有标注 Cookie 认证的接口由浏览器自动携带 HttpOnly Cookie；公共请求层不读取、保存或拼接 Cookie 值。所有成功夹具均为 HTTP `200`，错误夹具的状态码见下表。

| 夹具 | HTTP 方法与路径 | 必要请求头 | 结果未知时的恢复方式 |
| --- | --- | --- | --- |
| `c/available-dates-success.json` | `GET /api/v1/shows/available-dates?movieId={movieId}&cinemaId={cinemaId}` | 无 | 只读查询，可直接重新查询。 |
| `c/show-list-success.json` | `GET /api/v1/shows?movieId={movieId}&cinemaId={cinemaId}` | 无 | 只读查询，可直接重新查询。 |
| `c/seat-map-success.json` | `GET /api/v1/shows/{showId}/seats` | Cookie 认证 | 只读查询，可重新读取权威座位图。 |
| `c/create-order-success.json` | `POST /api/v1/orders` | Cookie 认证、`Idempotency-Key`、`Content-Type: application/json` | 使用原 `clientRequestId` 调用 `GET /api/v1/orders/by-request/{clientRequestId}`；不得生成新键重发建单。 |
| `c/order-page-success.json` | `GET /api/v1/orders` | Cookie 认证 | 只读分页查询，可直接重新查询。 |
| `c/payment-success.json` | `POST /api/v1/orders/{orderNo}/payments` | Cookie 认证、`Idempotency-Key`；无请求体 | 使用原 `orderNo` 调用 `GET /api/v1/orders/{orderNo}/payment`；不得自动重发支付。 |
| `c/electronic-ticket-success.json` | `GET /api/v1/tickets/{ticketId}` | Cookie 认证 | 只读查询，可直接重新查询。 |
| `c/refund-impact-success.json` | `POST /api/v1/orders/{orderNo}/refund-confirmation` | Cookie 认证；无请求体 | 只读影响查询，可重新查询后由用户再次确认。 |
| `c/refund-success.json` | `POST /api/v1/orders/{orderNo}/refunds` | Cookie 认证、`Idempotency-Key`、`Content-Type: application/json` | 使用原 `orderNo` 调用 `GET /api/v1/orders/{orderNo}/refund`；不得自动重发退票。 |
| `c/alternative-shows-success.json` | `GET /api/v1/orders/{orderNo}/alternative-shows` | Cookie 认证 | 只读查询，可直接重新查询。 |
| `c/seat-conflict-error.json` | 建单失败响应 | 同建单接口 | HTTP `409`；刷新座位图并由用户重新选择，不自动重试。 |
| `c/idempotency-mismatch-error.json` | 幂等写请求失败响应 | 同对应写接口 | HTTP `409`；保留原请求，不得把同一键用于不同参数。 |
| `c/order-not-found-error.json` | 本人订单查询失败响应 | Cookie 认证 | HTTP `404`；不泄露资源归属。 |
| `c/unauthenticated-error.json` | 受保护接口失败响应 | 缺少或失效 Cookie | HTTP `401`；交由公共请求层进入登录恢复。 |
| `c/admin-order-page-success.json` | `GET /api/v1/admin/orders` | ADMIN Cookie认证 | 只读查询，可保留筛选条件后手动重试。 |
| `c/admin-order-detail-success.json` | `GET /api/v1/admin/orders/{orderNo}` | ADMIN Cookie认证 | 只读查询，可按原订单号刷新。 |
| `c/admin-user-query-too-broad-error.json` | 用户关键字命中超过100人 | ADMIN Cookie认证 | HTTP `400/201010`；补充更多关键字。 |
| `c/admin-user-directory-unavailable-error.json` | 用户目录暂不可用 | ADMIN Cookie认证 | HTTP `503/301002`；保留筛选上下文并手动重试。 |

`c/unauthenticated-error.json` 是 C 已确认的最终目标格式，依赖 C 后续认证 PR 用自定义 `AuthenticationEntryPoint` 返回完整 `Result` JSON。当前 `SecuritySkeletonConfiguration` 仍使用 `HttpStatusEntryPoint`，运行时可能只返回空的 HTTP `401`；在认证实现合并前，不得把该夹具表述为当前接口已经支持的响应体。

C 必须通过公共请求层调用接口。写请求超时后复用原 `clientRequestId`、订单号或幂等键查询权威结果，不自动生成新键重放写请求。
