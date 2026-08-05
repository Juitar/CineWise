# Tasks: ticketing-purchase-frontend

## 第一批：购票主线（场次 -> 选座 -> 订单确认 -> 建单 -> 建单响应丢失恢复）

- [x] 1.1 建立 `modules/ticketing/types.ts` 与 `modules/ticketing/api.ts`，定义 ShowSummary、SeatMapResponse 等 DTO（ID 与金额皆为字符串），封装真实后端 `GET /api/v1/shows` 与 `GET /api/v1/shows/{showId}/seats` 调用。（Owner: A）
- [x] 1.2 建立 `modules/order/types.ts`、`modules/order/api.ts` 与 `modules/order/recovery.ts`，实现建单 `POST /api/v1/orders` 及遇到 RESULT_UNKNOWN（网络断开、超时、502/504）时经由 `GET /api/v1/orders/by-request/{clientRequestId}` 的读补偿恢复逻辑；使用准确中文注释解释为什么禁止重试 POST。（Owner: A）
- [x] 1.3 实现 `modules/ticketing/hooks.ts` (`useShows`, `useSeatMap`) 与 `modules/order/hooks.ts` (`useCreateOrder`)，覆盖 Loading / Empty / Error / 401（对接受控 returnUrl，不造第二套认证）/ 冲突 204001 / RESULT_UNKNOWN 状态。（Owner: A）
- [x] 1.4 建立 `features/seat-map/SeatMap.tsx` 与样式，实现不少于 44px 移动触控和 PC 宽屏自适应、选座互斥与最大 6 个可用座位选择限制。（Owner: A）
- [x] 1.5 实现场次选择页 `/shows?movieId=&cinemaId=`（`pages/shows/index.tsx`），展现日期分类列表与“暂无可售场次”空状态，禁止写死或补演示场次。（Owner: A）
- [x] 1.6 实现座位选择页 `/shows/:showId/seats?movieId=&cinemaId=`（`pages/seats/index.tsx`），支持选定后以重复 `seatId` 参数格式导航向订单确认页。（Owner: A）
- [x] 1.7 实现订单确认页 `/orders/confirm`（`pages/orders/confirm/index.tsx`），以 `searchParams.getAll('seatId')` 读取多座 ID，刷新重载重新拉取可用座位；使用 `crypto.randomUUID()` 且仅生成一次稳定 `clientRequestId` / `Idempotency-Key`；发生 204001 冲突提示重选，发生 RESULT_UNKNOWN 调用 `recovery.ts` 恢复。（Owner: A）
- [ ] 1.8 C在审查A的Draft PR后，于公共路由注册A页面并配置`RequireAuth`；`/shows`保持公开，`/shows/:showId/seats`与`/orders/confirm`要求登录。（Owner: C）
- [x] 1.9 新增针对消费 `backend/src/test/resources/fixtures/ticketing/c/` 中第一批关切夹具（show-list-success, seat-map-success, seat-conflict-error, create-order-success, idempotency-mismatch-error, unauthenticated-error）的单元测试或检查。（Owner: A）
- [x] 1.10 编写第一批 E2E 用例 `frontend/e2e/ticketing-purchase-p1.spec.ts`，覆盖场次筛选、正常建单、座位冲突、明确失败刷新、RESULT_UNKNOWN 查询恢复与刷新后禁止重投。（Owner: A）
- [x] 1.11 完成A可独立执行的质量检查：`pnpm check`诊断运行通过（11个测试文件、55个用例），`openspec validate ticketing-purchase-frontend --strict`与`git diff --check`通过；本机Node版本低于项目门槛产生的engine警告已记录，不作为正式环境验收。（Owner: A）
- [ ] 1.12 C完成路由注册后，A/C在无旧`.umi`或`dist`缓存、符合`package.json` Node版本要求的环境重新执行生产构建与完整`pnpm e2e`，验证三个页面可达、受保护路由登录回跳及全部购票E2E；完成前不得声明页面联调最终通过。（Owner: A、C）

## 第二批：后续迭代（支付、电子票只读展示、订单管理、退票与替代场次回流）

- [x] 2.1 完成支付、支付结果、电子票、订单列表/详情、退票确认和替代场次纯展示组件及集中 Mock；组件不直接请求接口或决定交易成功。（Owner: A）
- [x] 2.2 按后端冻结 DTO 扩展 `modules/order/types.ts` 与 `api.ts`：补齐 `PAYING`、分页订单、取消、支付、电子票、退票影响/结果和包含 `movieId` 的替代场次契约，并消费 `backend/src/test/resources/fixtures/ticketing/c/` 固定夹具。（Owner: A）
- [x] 2.3 实现订单列表/详情查询和取消 Hook；取消请求使用稳定 `Idempotency-Key`，响应未知后只查询订单详情，不自动重发取消 POST。（Owner: A）
- [x] 2.4 实现 Mock 支付 Hook 与页面容器；六位数字仅在支付展示组件内存中校验并在调用回调前清空，支付 POST 为空业务请求体；响应未知后只查询支付结果。（Owner: A）
- [x] 2.5 实现支付结果查询与有界轮询：最多 15 次且总时长不超过 30 秒，成功、待支付或订单终态立即停止；卸载页面时清理定时器，达到上限后仅允许手动查询。（Owner: A）
- [x] 2.6 实现电子票查询与只读页面；票务状态以服务端为准，二维码仅根据 `qrPayload` 在浏览器本地渲染，不发送第三方请求。（Owner: A）
- [x] 2.7 实现退票影响、退票写入、结果恢复与替代场次查询 Hook；传统页面省略 `actionId`，退款使用稳定 `clientRequestId` 和 `Idempotency-Key`，响应未知后只查询原退款。（Owner: A）
- [x] 2.8 将 `/orders`、`/orders/:orderNo`、`/payments/:orderNo`、`/payments/:orderNo/result`、`/tickets/:ticketId`、`/orders/:orderNo/refund` 页面由集中 Mock 容器替换为真实 Hook 编排，保留加载、空、失败、离线只读和 RESULT_UNKNOWN 状态。（Owner: A）
- [x] 2.9 增加 API 契约、明确失败、结果未知、稳定幂等会话、轮询终止和卸载清理测试；执行 `pnpm check`、OpenSpec strict 校验和 `git diff --check`。（Owner: A）
- [ ] 2.10 C 在公共路由表注册第二批页面并配置 `RequireAuth`；A/C 在合规 Node 环境执行真实路由、认证回跳和交易闭环 E2E。（Owner: C、A）
