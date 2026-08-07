# Change: 个人订单场次上下文前端消费

## 背景

A 的订单查询接口已在 `GET /api/v1/orders` 与 `GET /api/v1/orders/{orderNo}` 中提供 `movieId`、`cinemaId`、`showStartTime`。已归档的购票交易页面仍使用旧查询类型和占位文案，无法展示权威开场时间，电子票也没有通过订单号补充该上下文。

## 范围

- 为个人订单 GET 接口新增前端查询专用 `OrderQueryResponse`，保持写接口 `OrderResponse` 不变。
- 订单列表、详情、支付和电子票页面展示固定业务时区格式化后的 `showStartTime`。
- 电子票按票据返回的 `orderNo` 查询订单详情；替代场次只使用响应中的 ID 构造选座路由。
- 消费后端正式订单分页和详情 JSON 夹具，并补充 PC/移动端 E2E。

## 非范围

- 不修改 C 的路由、认证、公共请求层或共享类型。
- 不返回、伪造或缓存 D 负责的影片标题、海报和影院名称。
- 不新增 SQL、Flyway、后端接口、权限或交易状态迁移。

## Owner 与验收

- Owner：A；依赖已合入的 A 订单查询契约。
- 所有 ID 保持字符串，金额保持两位小数字符串，时间统一按 `Asia/Shanghai` 展示。
- 写响应、幂等和 RESULT_UNKNOWN 恢复语义保持不变；`pnpm check`、相关 E2E、OpenSpec strict 和差异检查通过。
