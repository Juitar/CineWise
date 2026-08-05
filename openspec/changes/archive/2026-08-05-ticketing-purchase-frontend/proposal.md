# Proposal: ticketing-purchase-frontend

## 背景

在 C 已经构建的 Umi 4 / React 前端应用壳层（公共路由、UserLayout、主题样式、公共 `apiRequest` 封装）和 A 已通过 MySQL 8.4 验证的 `ticketing-transaction-flow` 后端 REST 契约基础之上，本 Change 旨在扩展并落地 A 负责的固定购票交易闭环前端页面与业务模块。即使 Agent 智能对话与出行推荐模块不可用，用户也能通过固定 Web / H5 页面流畅完成“场次选择 → 座位选择 → 订单确认与建单 → Mock 支付 → 支付结果轮询 → 电子票只读展示 → 订单管理与退票/替代场次”全链路流程。

## 目标

- 建立 `modules/ticketing` 和 `modules/order` 业务模块，封装对后端 REST API 的调用、通用类型定义（ID 始终为 `string`，金额为两位小数字符串）、状态 Hook 以及严格的写入幂等与结果恢复机制（`recovery.ts`）。
- 实现 `features/seat-map` 与 `features/payment-status` 核心业务组件，支持 PC / 移动端响应式布局、最多 6 座互斥选座、可用/已锁定/已售出状态展示以及高交互可用性。
- 组合并遵循 A 负责的核心页面路径规范（路由表注册及 RequireAuth 权限配置由 Owner C 统一负责，A 不修改 `.umirc.ts`）：
  - `/shows?movieId={movieId}&cinemaId={cinemaId}`：场次选择页
  - `/shows/:showId/seats?movieId=&cinemaId=`：座位选择页
  - `/orders/confirm?showId=&seatId=1&seatId=2&movieId=&cinemaId=`：订单确认页（使用重复 seatId 参数，通过 `searchParams.getAll('seatId')` 读取）
  - `/payments/:orderNo`：Mock 模拟支付页
  - `/payments/:orderNo/result`：支付结果与读轮询页
  - `/tickets/:ticketId`：电子票只读展示页
  - `/orders` 与 `/orders/:orderNo`：本人订单列表与详情（含未支付订单取消操作）
  - `/orders/:orderNo/refund`：退票与同影片替代场次查询页
- 落实前端极度严格的异常分类与网络幂等恢复策略：
  - 明确 RESULT_UNKNOWN 分类为：写请求网络断开、写请求超时、代理层 502/504、写请求已发送但响应包无法确认；400/401/403/404/409/422 及服务端业务错误不进入 RESULT_UNKNOWN。
  - 建单、支付、取消、退票等写入（POST）在遇到 RESULT_UNKNOWN 时，绝不自动重新发起新的 POST 请求，仅调用后端等幂查询接口（如 `GET /api/v1/orders/by-request/{clientRequestId}`）恢复真实结果。
  - 模拟支付六位数字密码仅短暂保存在组件局部内存中，完成格式校验并准备发起支付请求时立即清空。密码不得进入请求体、Header、URL、日志、埋点、localStorage、sessionStorage、错误对象或测试快照；支付 POST 请求体必为空。
- 通过 TypeScript 严格模式检查、规范 E2E 测试及消费后端标准夹具完成全链路验证。

## 非目标

- 不创建第二套 React / Vite / Umi 工程或第二套 HTTP 客户端（仅使用 `frontend/src/shared/api/client.ts` 中的 `apiRequest`）。
- 不实现 JWT 解析、Cookie 操作、登录注册页面、CSRF 或 SecurityContext；C 已经合入统一 RequireAuth、安全 returnUrl 与 401 统一重定向能力，A 页面不再写自行判断或去登录重定向逻辑；
- 不改写 C 维护的全局布局、主题 Token、公共导航及 `UserLayout` 核心结构。
- 不实现 B 的 Agent 工作区、SSE、推荐座位或确认动作会话管理。
- 不实现 D 的影片 / 影院持久化存储、内容推荐、观影出行提醒等功能。
- 不修改或扩展后端已定义好的 Controller、DTO、以及 Flyway 迁移脚本。
- 不得在前端推断 movieId；对 `GET /api/v1/orders/{orderNo}/alternative-shows` 候选场次包含的 `movieId: string`，前端在后端契约未合并到 dev 前第一批暂不依赖该接口，仅定义类型与 Mock。

## Owner 与协作

- **A（负责人）**：负责开发与维护前端的场次、选座、订单、支付、电子票和退票页面与对应 `modules` / `features`，严格以 A 模块后端 API 契约为唯一权威数据源。
- **C（外部依赖）**：提供 `UserLayout`、样式 tokens、公共 HTTP 请求封装、统一认证 Provider、401 单飞处理以及页面路由表注册和 RequireAuth 配置；
- **D（外部依赖）**：A 的选座及场次展示复用通过查询参数携带的公开描述数据，不读取 D 的私有状态。
