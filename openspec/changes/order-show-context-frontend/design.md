# Design: 个人订单场次上下文前端消费

## 契约和分层

- `OrderQueryResponse extends OrderResponse` 仅供两个个人订单 GET 接口、`OrderPageResponse.records` 与 `useOrder` 使用。
- 建单、按 `clientRequestId` 恢复及取消请求继续使用 `OrderResponse`，避免展示字段扩大写响应契约。
- 页面只读取路由参数并组合 Hook 与 feature；`modules/order` 保持 API、查询状态、时间格式化和选座路由构造。

## 展示和降级

- `showStartTime` 统一由 `formatOrderDateTime` 固定按 `Asia/Shanghai` 显示。无偏移的 Java `LocalDateTime` 按该业务时区解释，非法值显示“时间信息暂不可用”。
- 订单列表筛选实际对应订单 `create_time`，文案为“下单日期”。
- 影片/影院内容未由 D 的公开 API 提供时，显示“影片信息暂不可用”，不得用 `showId` 拼成标题。
- 电子票先查询票据，随后仅以票据返回的 `orderNo` 查询订单；快速切换票据时只接受同一订单号的响应，避免旧订单上下文串入新票据。

## 验证

- API 测试消费 `order-page-success.json`、`order-detail-success.json`，确认三个字段和写响应类型边界。
- 组件和页面测试覆盖时间格式化、占位文案移除、下单日期筛选和权威路由。
- E2E 覆盖电子票票据到订单上下文的读取，PC 与移动端均验证；同时更新既有交易 P2 Mock 以满足新增订单详情读取。
