# 妙语购票交易闭环前端开发实施计划 (ticketing-purchase-frontend)

在 C 已构建的 Umi 4 / React 前端壳层（安全路由、`UserLayout`、样式 token 与基础 `apiRequest`）和 A 已经通过验证的后台 `ticketing-transaction-flow` 契约基础之上，我们在新拉取的 Git Worktree（`D:\Programming\妙语购票\CineWise-ticketing-purchase-frontend`，分支 `feat/ticketing-purchase-frontend`）落地 A 负责的固定购票闭环前端代码。

按照当前迭代计划，**第一批次重点落地购票主线**：
`场次选择 → 座位选择 → 订单确认与建单 → 建单响应丢失恢复`
（后续第二批次待对应后端 PR 融入 `dev` 且 rebase 之后，落地 Mock 支付、电子票只读展示、订单列表/详情、取消与退票/替代场次回流）。

---

## User Review Required

> [!IMPORTANT]
> **Git 工作区隔离要求已完全遵照执行**
> 已新建独立 Worktree：`D:\Programming\妙语购票\CineWise-ticketing-purchase-frontend`，开发分支为 `feat/ticketing-purchase-frontend`（基于最新 `origin/dev`），对 `dev` 分支或其他工作树没有干扰。

> [!IMPORTANT]
> **关于网络异常与超时恢复的绝对底线 (RESULT_UNKNOWN 严格分类)**
> 在写操作（`POST /api/v1/orders` 等）遇到网络断开、超时、代理层 502/504（根据 `ApiError.status` 显式识别）或响应包无法确认时，分类为 `RESULT_UNKNOWN`；400/401/403/404/409/422 及服务端明确返回的业务错误不得进入 RESULT_UNKNOWN。
> 前端在遇到 `RESULT_UNKNOWN` 时 **严禁自动重新发送 POST 请求**，必须沿用首次生成且在重置前保持稳定的 `clientRequestId` / `Idempotency-Key` 触发等幂查询补偿机制（建单未知查询 `/by-request/{clientRequestId}`）。

> [!WARNING]
> **外部依赖宣告：C 认证与 D 替代场次**
> - **C 认证依赖**：当前 `dev` 尚未提供完整的认证 Provider、受保护路由守卫和 401 单飞跳转能力。A 明确将其记录为外部依赖，不得自行实现 JWT、Cookie、CurrentUserProvider 或第二套路由守卫；页面仅依托公共 `ApiError` 呈现未登录状态并保留受控 `returnUrl`；受保护路由在合并前必须经过 C 审查。
> - **D 替代场次依赖**：后台 `GET /api/v1/orders/{orderNo}/alternative-shows` 返回候选对象包含 `movieId: string`。前端在契约合入 `dev` 前第一批暂不依赖此接口，仅定义类型与 Mock，禁止凭猜测推断 `movieId`。

> [!TIP]
> **订单确认页多座参数格式规范**
> 选座从 `/shows/:showId/seats` 跳转向 `/orders/confirm` 时，必须使用重复的 `seatId` 参数：
> `/orders/confirm?showId=...&seatId=1&seatId=2&movieId=...&cinemaId=...`
> 在订单确认页中统一使用 `const seatIds = searchParams.getAll('seatId');` 读取；禁止使用 `seatIds=1,2` 格式。确认页重载后必须按参数重新拉取座位图并仅显示仍为 `AVAILABLE` 的选座。

---

## Open Questions

> [!NOTE]
> 1. ID 与金额始终使用 `string`，幂等键使用 `crypto.randomUUID()` 且单次用户确认只生成一次；
> 2. 原型图路径 `D:\Programming\妙语购票\CineWise-Docs\系分文档\前端\A负责前端展示图\` 仅做信息关系指导，禁止实现过时元素（服务费、优惠券、AI 推荐座位、Agent 聊天侧栏、餐饮与路线推荐、真实支付渠道）；
> 3. 所有电子票相关页面一律使用“电子票只读展示”、“查看电子票”，二维码只凭后台 `qrPayload` 在前端浏览器本地渲染。

---

## Proposed Changes (第一批实施范围)

我们在 `D:\Programming\妙语购票\CineWise-ticketing-purchase-frontend/frontend/src/` 下按标准模块与职责分层新增文件：

### 1. 领域模型与基础 API 层 (modules)

处理后端的 API 调用、统一数据类型与异常恢复，绝不直接操作其他模块私有数据：

#### [NEW] [api.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/modules/ticketing/api.ts)
- 提供场次查询 `GET /api/v1/shows` 与座位图 `GET /api/v1/shows/{showId}/seats` 真实接口调用。
#### [NEW] [types.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/modules/ticketing/types.ts)
- 定义 ShowSummary、SeatMapResponse 及 SeatItem TypeScript 类型定义（ID 与金额字段全声明为 `string`）。
#### [NEW] [hooks.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/modules/ticketing/hooks.ts)
- 提供 `useShows` 与 `useSeatMap` 响应式数据获取钩子，包含正常、空状态及非 200 异常捕获。

---

#### [NEW] [api.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/modules/order/api.ts)
- 第一批实现建单 `POST /api/v1/orders` 与按 request 查询 `GET /api/v1/orders/by-request/{clientRequestId}`。
#### [NEW] [types.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/modules/order/types.ts)
- 定义 CreateOrderRequest、OrderResponse 及第二批所需的 AlternativeShow（带 `movieId: string`）等类型定义。
#### [NEW] [recovery.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/modules/order/recovery.ts)
- 实现订单防重与读补偿核心模块：针对 RESULT_UNKNOWN 调用 `recoverCreatedOrder` 查询原建单结果，写有清晰中文 JSDoc 说明不发第二笔 POST 写入的原因。
#### [NEW] [hooks.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/modules/order/hooks.ts)
- 封装个人建单钩子 `useCreateOrder`，处理 204001 座位冲突清理与 RESULT_UNKNOWN 恢复。

---

### 2. 交互组件层 (features)

提供支持 PC / H5 自适应渲染的复杂交互业务功能组件：

#### [NEW] [SeatMap.tsx](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/features/seat-map/SeatMap.tsx)
- 实现银幕视角定位、选座网格展示、6 座最多约束及 `AVAILABLE / LOCKED / SOLD / selected` 样式切换。
#### [NEW] [index.css](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/features/seat-map/index.css)
- 为座位图编写移动可滚触控支持（`>=44px`）与可选 CSS 变量修饰。

---

### 3. 页面路由层 (pages & routing - 第一批购票主线)

只负责路由参数读取、页面组装和步骤编排：

#### [NEW] [index.tsx](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/pages/shows/index.tsx)
- 场次选择页 `/shows?movieId=&cinemaId=`：按时间呈现场次选项，显示“当前影片和影院暂无可售场次”空结果。
#### [NEW] [index.tsx](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/pages/seats/index.tsx)
- 座位选择页 `/shows/:showId/seats`：集成座位图组件并以重复 `seatId` 参数方式把选中座导航进入订单确认页。
#### [NEW] [index.tsx](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/src/pages/orders/confirm/index.tsx)
- 订单确认页 `/orders/confirm`：二次拉取并核实所选座位最新可用状态，使用 `crypto.randomUUID()` 产生稳健的单次提交键发起建单，冲突返回座位图重选。

---

#### [MODIFY] [.umirc.ts](file:///D:/Programming/妙语购票/CineWise-ticketing-purchase-frontend/frontend/.umirc.ts)
- 在 Umi `routes` 下的 `UserLayout` 分支内注册上述场次、选座和订单确认业务页面路径。

---

## Verification Plan

### Automated Tests
我们在 `D:\Programming\妙语购票\CineWise-ticketing-purchase-frontend/frontend` 目录下运行以下检查：
1. **类型与语法静态审查**：
   ```bash
   pnpm check
   ```
   检查第一批新增的 TypeScript 代码零类型或样式检查警告。
2. **单元与夹具测试**：
   ```bash
   pnpm test
   ```
   执行或添加基础工具函数的单测测试。
3. **Playwright E2E 自动化端到端测试**：
   新增第一批 E2E 测试文件 `frontend/e2e/ticketing-purchase-p1.spec.ts`，运行：
   ```bash
   pnpm e2e
   ```
   覆盖第一批核心场景：
   - 从 `/shows` 到选座并成功创建待支付订单；
   - 座位冲突 (204001) 后提示并重新进入有效选座；
   - 遭遇 RESULT_UNKNOWN 后自动调用 `by-request/{clientRequestId}` 完成等幂无重试恢复。

### Manual Verification
- 通过前端工作树运行和自查，确认 UI 严格遵循 `UserLayout`、样式 tokens 且未引入原型中禁止的过期业务规则。
