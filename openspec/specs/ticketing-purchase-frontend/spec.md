# ticketing-purchase-frontend Specification

## Purpose
定义 A 负责的购票交易前端闭环：场次与选座、幂等建单、Mock 支付及结果恢复、电子票、本人订单、取消、退票和替代场次回流；认证与公共路由复用 C 提供的统一能力。
## Requirements
### Requirement: 场次列表与座位选择页面与交互

系统 SHALL 提供 `/shows?movieId={movieId}&cinemaId={cinemaId}` 和 `/shows/:showId/seats?movieId=&cinemaId=` 页面。页面 SHALL 通过 `modules/ticketing` 查询对应影院与影片下的可售场次及座位图数据；支持最多选择 6 个 `AVAILABLE` 状态座位，且对 `LOCKED` 与 `SOLD` 座位禁用勾选。

#### Scenario: 场次列表正常查询与空数据状态
- GIVEN 用户携带有效的 `movieId` 与 `cinemaId` 访问 `/shows` 页面
- WHEN 服务端返回未过期且处于 `ON_SALE` 的场次列表
- THEN 页面按开始时间与日期排序展示可供选座的场次入口
- AND 当场次数组为空时显示“当前影片和影院暂无可售场次”空状态，不得补全演示数据

#### Scenario: 座位图选择上限控制与状态渲染
- GIVEN 用户进入 `/shows/:showId/seats` 座位选择页
- WHEN 用户选择座位
- THEN `AVAILABLE` 座位为可选，点击后高亮为选定状态
- AND 最多允许勾选 6 个座位，尝试选择第 7 个座位时提示达到限制
- AND 页面刷新时从 URL 提取业务 ID 重新拉取服务端真实状态，不依赖上一页内存状态

### Requirement: 订单确认与写操作幂等恢复

系统 SHALL 提供 `/orders/confirm?showId=...&seatId=1&seatId=2&movieId=...&cinemaId=...` 页面。所有参数通过 URL 传递，读取座位 ID 时使用 `searchParams.getAll('seatId')`，禁止使用英文逗号拼接。当提交建单请求 `POST /api/v1/orders` 时，页面使用 `crypto.randomUUID()` 生成且仅生成一次稳定的 `clientRequestId` 和 `Idempotency-Key`。写操作网络异常时按 RESULT_UNKNOWN 分类识别（网络断开、超时、502/504），绝不自动重新发起 POST 请求，仅通过 `GET /api/v1/orders/by-request/{clientRequestId}` 查询补偿。

#### Scenario: 提交建单请求并进入待支付
- GIVEN 用户选取合法座位进入订单确认页（通过 `searchParams.getAll('seatId')` 读取所选座位）
- WHEN 页面重载或初始进入
- THEN 必须向服务端查询场次与座位图，仅恢复仍为 `AVAILABLE` 的选择，不得把 URL 中的 `seatId` 当作已锁座结果
- AND 确认创建订单时请求体仅包含 `{ showId, seatIds, clientRequestId }`
- AND 成功后以返回的 `orderNo`、`totalAmount` 和 `expireTime` 为准跳转模拟支付页

#### Scenario: 建单响应未知与 clientRequestId 读补偿
- GIVEN 用户发起建单 POST 请求后网络中断、超时或代理层 502/504 导致 RESULT_UNKNOWN
- WHEN 前端触发恢复机制
- THEN 系统按原稳定 `clientRequestId` 执行 `GET /api/v1/orders/by-request/{clientRequestId}` 查询
- AND 若返回订单存在且为本人订单，则直接恢复并进入该订单支付流程；禁止发起第二笔建单 POST 请求

#### Scenario: 座位已被抢先锁定后的冲突反馈
- GIVEN 提交建单时服务端返回 `204001` 座位冲突错误码
- WHEN 前端接收到该业务错误
- THEN 自动清理失效的座位选中状态并提示“座位不可锁定，请重新选择”，同时刷新座位图

### Requirement: 模拟支付与支付结果读轮询

系统 SHALL 提供 `/payments/:orderNo` 和 `/payments/:orderNo/result` 页面。六位数字密码仅短暂保存在组件局部内存中，完成格式校验并准备发起支付请求时立即清空。密码不得进入请求体、Header、URL、日志、埋点、localStorage、sessionStorage、错误对象或测试快照；支付 POST 接口 `POST /api/v1/orders/{orderNo}/payments` 请求体为空且仅带稳定的 `Idempotency-Key`。

#### Scenario: 模拟支付安全脱敏与发起
- GIVEN 待支付状态订单的模拟支付页
- WHEN 用户输入六位数字模拟密码并提交
- THEN 表单对六位数字进行浏览器端校验并在发请求前立即清除密码内存
- AND 发起请求 `POST /api/v1/orders/{orderNo}/payments` 请求体必为空，携带稳定 `Idempotency-Key`

#### Scenario: 支付响应未知与支付只读轮询
- GIVEN 发起支付 POST 遇到网络断开、超时或 502/504 导致 RESULT_UNKNOWN
- WHEN 进入 `/payments/:orderNo/result` 支付结果页面
- THEN 系统执行固定或退避间隔的只读轮询（调用 `GET /api/v1/orders/{orderNo}/payment`），最高不超过设定的上限次数和总时长
- AND 当返回 `PAID` / `SUCCESS` 时立即结束轮询并显示成功及查看电子票入口
- AND 当返回 `PENDING_PAYMENT` 时停止轮询并允许用户主动返回支付页
- AND 当返回 `CANCELLED` / `EXPIRED` / `REFUNDED` 终态时立即结束轮询
- AND 超过轮询上限仍未确认时，显示手动“重新查询结果”按钮，绝不自动重发支付 POST 请求

### Requirement: 电子票只读展示与本人订单管理

系统 SHALL 提供 `/tickets/:ticketId` 电子票只读展示页面，以及 `/orders`、`/orders/:orderNo` 本人订单列表与详情页面。当前系统不提供检票或核销接口，电子票仅做“电子票只读展示”与“查看电子票”。

#### Scenario: 电子票只读展示与本地二维码渲染
- GIVEN 用户访问 `/tickets/:ticketId` 查看电子票
- WHEN 调用 `GET /api/v1/tickets/{ticketId}` 返回成功
- THEN 当状态为 `VALID` 时呈现有效电子票只读展示样式，其二维码必须基于返回的 `qrPayload` 在前端浏览器本地渲染，禁止发送到任何外部第三方服务器
- AND 当状态为 `REFUNDED` 时显式标注为已退票，不可继续显示为有效票

#### Scenario: 取消订单响应未知查询订单详情恢复
- GIVEN 用户对 `PENDING_PAYMENT` 订单发起取消 POST 请求遇到 RESULT_UNKNOWN
- WHEN 前端处理恢复
- THEN 必须调用 `GET /api/v1/orders/{orderNo}` 查询最新详情
- AND 若状态已变为 `CANCELLED` 则更新 UI 为已取消；绝不自动重试发送取消 POST 请求

### Requirement: 退票二次确认与同影片替代场次查询

系统 SHALL 提供 `/orders/:orderNo/refund` 退票页面。退票请求 POST `/api/v1/orders/{orderNo}/refunds` 在遇 RESULT_UNKNOWN 时按原权威接口通过 `GET /api/v1/orders/{orderNo}/refund` 查询恢复。同影片候选替代场次 `AlternativeShow` 现包含 `movieId: string`，第一批暂不依赖后台真实接口（等待该契约合入 dev 后接通），页面禁止凭猜想推断 `movieId`。

#### Scenario: 退票响应未知查询 refund 恢复
- GIVEN 退票请求 POST 发出后遭遇网络异常或超时导致 RESULT_UNKNOWN
- WHEN 前端处理退票结果
- THEN 调用只读借口 `GET /api/v1/orders/{orderNo}/refund` 检查原已确定的退款状态
- AND 绝不自动重新发起第二笔退票 POST 请求

#### Scenario: 替代场次选座回流且不信任余座快照
- GIVEN 用户在退票页或结果页查看替代场次列表 `GET /api/v1/orders/{orderNo}/alternative-shows`
- WHEN 点击某候选替代场次（含服务端返回的 `movieId`, `cinemaId`, `showId`）
- THEN 系统跳转至 `/shows/{showId}/seats?movieId={movieId}&cinemaId={cinemaId}`
- AND 进入选座页后必须重新从服务端获取最新座位状态，绝不信任替代场次列表中的余座快照

### Requirement: 个人订单详情隐藏内部场次和座位编号

系统 SHALL 在个人订单详情页隐藏场次编号和座位编号，同时 SHALL 保留用户识别订单与执行合法后续操作所需的业务信息。该展示规则 SHALL NOT 删除服务端订单数据或改变电子票的座位编号展示。

#### Scenario: 查看包含场次和座位 ID 的订单

- **GIVEN** 本人订单响应包含 `showId` 和 `seatIds`
- **WHEN** 用户打开个人订单详情
- **THEN** 页面不显示“场次编号”或“座位编号”
- **AND** 页面仍显示订单号、影片、影院、开场时间、票数、金额和订单状态对应操作

#### Scenario: 从订单详情查看电子票

- **GIVEN** 已支付订单存在有效电子票
- **WHEN** 用户从订单详情进入电子票页面
- **THEN** 电子票仍按既有契约展示服务端返回的座位编号
