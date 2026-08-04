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

- [ ] 2.1 定义后续支付、电子票、订单管理、退票以及 `AlternativeShow`（包含 `movieId: string`）等 DTO 与 Mock。（Owner: A）
- [ ] 2.2 待后端替代场次等 PR 正式合入 dev 并 rebase 最新 dev 后，实现 Mock 模拟支付 `/payments/:orderNo`、支付只读轮询 `/payments/:orderNo/result`、电子票只读展示 `/tickets/:ticketId` 及订单详情页面。（Owner: A）
- [ ] 2.3 实现个人订单查询 `/orders` 与退票确认 / 替代场次 `/orders/:orderNo/refund` 页面。（Owner: A）
