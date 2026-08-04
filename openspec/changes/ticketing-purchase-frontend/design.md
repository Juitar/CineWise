# Design: ticketing-purchase-frontend

## 1. 架构分层设计

本变更对 A 负责的购票流程前端进行模块化落地，遵循项目的 `pages` -> `features` -> `modules` -> `shared` 分层架构：

```text
frontend/src/
├── pages/
│   ├── shows/index.tsx              # 场次选择路由页面：/shows?movieId=&cinemaId=
│   ├── seats/index.tsx              # 座位选择路由页面：/shows/:showId/seats?movieId=&cinemaId=
│   ├── orders/
│   │   ├── confirm/index.tsx        # 订单确认页：/orders/confirm?showId=&seatId=1&seatId=2&movieId=&cinemaId=
│   │   ├── index.tsx                # 本人订单列表页：/orders (第一批暂不实现)
│   │   ├── detail/index.tsx         # 订单详情页：/orders/:orderNo (第一批暂不实现)
│   │   └── refund/index.tsx         # 退票与替代场次页：/orders/:orderNo/refund (第一批暂不实现)
│   ├── payments/
│   │   ├── index.tsx                # Mock 模拟支付页：/payments/:orderNo (第一批暂不实现)
│   │   └── result/index.tsx         # 支付结果与读轮询页：/payments/:orderNo/result (第一批暂不实现)
│   └── tickets/index.tsx            # 电子票只读展示页：/tickets/:ticketId (第一批暂不实现)
├── modules/
│   ├── ticketing/                   # 场次、座位领域模块 (第一批落地)
│   │   ├── api.ts                   # GET /api/v1/shows, GET /api/v1/shows/{showId}/seats
│   │   ├── types.ts                 # ShowSummary, SeatMapResponse 等 DTO
│   │   └── hooks.ts                 # useShows, useSeatMap
│   └── order/                       # 订单领域模块 (第一批落地建单与建单恢复)
│       ├── api.ts                   # POST /api/v1/orders, GET /api/v1/orders/by-request/{clientRequestId}
│       ├── types.ts                 # CreateOrderRequest, OrderResponse 等
│       ├── recovery.ts              # RESULT_UNKNOWN 异常分类与原 clientRequestId 读补偿
│       └── hooks.ts                 # useCreateOrder
└── features/
    └── seat-map/                    # 响应式交互座位图组件 (第一批落地)
```

## 2. 核心状态与网络恢复设计 (Recovery Strategy & RESULT_UNKNOWN)

微服务与分布式事务中，前端发送写入请求（`POST /api/v1/orders`、`POST .../payments`、`POST .../cancel`、`POST .../refunds`）按以下严格分类判定：
1. **进入 RESULT_UNKNOWN**：
   - 写请求网络断开；
   - 写请求超时；
   - 代理层 502/504（由 `ApiError.status === 502 || status === 504` 显式识别），无法确认后端是否已经提交；
   - 写请求已发送但响应包无法确认。
2. **不得进入 RESULT_UNKNOWN**：
   - HTTP 400、401、403、404、409、422 及服务端明确返回的业务异常。
3. **等幂只读补偿机制**：
   - 遇到 RESULT_UNKNOWN 时，前端**绝对不得自动重新发送 POST 请求**；
   - **建单响应未知**：通过 `GET /api/v1/orders/by-request/{clientRequestId}` 按原 `clientRequestId` 查询；
   - **支付响应未知**：通过 `GET /api/v1/orders/{orderNo}/payment` 按固定或退避间隔轮询（最长 30 秒 / 15 次上限；返回 `PAID`/`SUCCESS` 立即结束并进入查看电子票；`PENDING_PAYMENT` 停止轮询允许主动返回支付；终态 `CANCELLED/EXPIRED/REFUNDED` 立即结束；超时展示手动“重新查询结果”按钮，绝不自动重发支付 POST）；
   - **取消响应未知**：通过 `GET /api/v1/orders/{orderNo}` 查询订单最新详情；
   - **退票响应未知**：通过 `GET /api/v1/orders/{orderNo}/refund` 查询最终退费快照。

## 3. ID 格式、幂等键生成与安全脱敏

- **业务 ID**：全部保留和声明为 `string`，禁止转换为 JavaScript Number；
- **幂等键与请求 ID**：用户点击确定写操作时，使用 `crypto.randomUUID()` 一次性生成稳定的 `clientRequestId` 和 `Idempotency-Key`；使用 `useRef` 保障在 React 组件重渲染时保持同一会话标识不改变；
- **模拟支付密码**：六位数字仅短暂保存在组件局部内存中，完成格式校验并准备发起支付请求时立即清空。密码不得进入请求体、Header、URL、日志、埋点、localStorage、sessionStorage、错误对象或测试快照；支付 POST 接口请求体为空；
- **电子票展示**：统一使用“电子票只读展示”与“查看电子票”术语，不支持核销动作；电子票二维码必须以返回的 `qrPayload` 字符串在浏览器端通过本地依赖渲染，禁止发送到外部第三方服务器。

## 4. 路由 URL 参数规范与会话防篡改

- **订单确认页传参**：选座进入确认页统一使用重复的 `seatId` 参数：
  `/orders/confirm?showId=...&seatId=1&seatId=2&movieId=...&cinemaId=...`
- **读取方式**：
  ```ts
  const seatIds = searchParams.getAll('seatId');
  ```
  严禁使用 `seatIds=1,2` 等非标准表达；
- **重载核验**：确认页重载时，必须按 URL 参数重新获取最新场次与座位图数据，仅对仍处于 `AVAILABLE` 状态的座位进行结算展示，绝把 URL 传入的 `seatId` 盲目当做已成功锁座结果。

## 5. 外部依赖边界 (C 与 D 模块协作)

- **C 认证与路由依赖**：C 已经合入统一 RequireAuth、安全 returnUrl、认证 Provider 和 401 单飞处理能力。A 不负责配置 `.umirc.ts` 或定义第二套路由守卫；购票选座与订单确认的路由表注册及 Auth 保护统一由 Owner C 负责配置；
- **D 替代场次依赖**：后台 `GET /api/v1/orders/{orderNo}/alternative-shows` 返回候选对象包含 `movieId: string`。前端对该接口在第一批中仅作接口契约与 Mock 定义，暂不进行页面调用，必须等待后台分支合入 `dev` 后进行 rebase 再行接入；页面禁止从订单、标题或其他状态推断 `movieId`。

## 6. 原型图引用与视觉遵循

- A 的原型图存放在 `D:\Programming\妙语购票\CineWise-Docs\系分文档\前端\A负责前端展示图\`，仅决定页面层级和信息关系；
- 严禁实现原型中已过时的：服务费、优惠券、AI 推荐座位、Agent 聊天侧栏、餐饮与路线推荐、真实支付渠道；
- 样式层面复用 C 的 `UserLayout`、样式 tokens、Ant Design 与 antd-mobile 适配方案。
