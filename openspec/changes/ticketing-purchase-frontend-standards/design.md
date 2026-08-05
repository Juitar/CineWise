# Design: 购票主线前端规范整改

## 分层

- `pages/shows`、`pages/seats`、`pages/orders/confirm` 只读取路由参数、组合 Hook 和展示组件。
- `modules/ticketing` 负责场次与座位查询、DTO、错误和查询状态。
- `modules/order` 负责订单请求、稳定幂等会话、结果未知恢复、金额展示工具和订单成功数据。
- `features` 只渲染 props；座位图和订单成功卡片不发送请求、不改变领域状态。

## 交互

- 小于 1024px 使用 antd-mobile 的 `SpinLoading`、`ErrorBlock`、`Button`；桌面使用 Ant Design。
- 座位继续使用语义化 checkbox button，业务 ID 保持 string，路由座位使用重复 `seatId` 参数。
- 场次和座位查询失败保留上下文并允许只读重试；缺失或无效参数不伪造数据。
- 建单超时/网络断开/502/504 进入 `RESULT_UNKNOWN`，只使用原 `clientRequestId` 查询，不生成新 POST。

## 数据与时间

- 价格与金额保持两位小数字符串，由统一金额工具展示；页面不使用浮点金额计算。
- ISO 8601 时间由统一 formatter 解析和展示；非法时间使用明确降级文案。
- 建单成功页只展示服务端 `orderNo`、金额和截止时间，并通过 callback 导航。

## Owner 边界

不读取 C 的认证内部状态，不修改公共请求层、路由表、布局或全局样式；不访问其他模块私有持久化对象。
