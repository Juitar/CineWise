# 设计

## 模块与接口边界

`frontend/src/modules/travel` 定义公开 DTO、运行时校验、API 和 Hook。普通 REST 只使用公共 `apiRequest<T>()`；页面只读取路由参数、组合视图和调用 Hook，不直接发请求。

本次复用 D 已合入 `dev` 的接口：

- `GET /api/v1/travel/tasks/{taskId}`
- `GET /api/v1/travel/tasks/by-order/{orderId}`
- `GET /api/v1/travel/tasks/{taskId}/advice`
- `POST /api/v1/travel/tasks/{taskId}/advice/refresh`
- `PUT /api/v1/travel/tasks/{taskId}/reminder`

所有业务 ID 保持字符串。任务和建议响应在进入 Hook 前校验必填字段、时间、版本和类型化建议，不解析兼容期的 `weatherJson`、`adviceJson`。

## 页面和入口

路由使用 `/travel/:taskId` 并复用 `RequireAuth`。订单详情只在 `PAID` 状态显示“查看出行建议”；点击后用订单响应中的可信 `orderId` 调用按订单查询接口，成功后导航到任务页。404/207001 显示尚未建立或不可访问，不猜测 `taskId`，也不创建任务。

首页删除 `PlanRecommendation` 及其写死的杭州影院、路线、距离和餐饮入口。Agent 正式卡片和后续定位流程不属于本 change。

## 状态与恢复

Hook 首次并行读取任务和建议，并在 taskId 变化或卸载时取消旧请求，防止旧响应覆盖新页面。

提醒更新提交 `{triggerAt, version}`，同时发送 `If-Match: "{version}"`。提交中禁用按钮；成功后用最小更新响应替换 `triggerAt`、`status` 和 `version`。网络失败、超时或响应无法确认时进入 `RESULT_UNKNOWN`，只允许重新 GET 同一个 `taskId` 和建议，不重发 PUT。

刷新建议提交中禁用按钮。429/107001、409/207002/207003、503/207004 直接映射为固定提示，保留已展示建议且不自动重发。

任务 `CANCELLED` 或建议 `isExpired=true` 时只读展示，不允许刷新或更新提醒。`available=false` 表示建议尚未生成，页面可由用户主动刷新。

## 展示和隐私

页面展示影院地址、提醒时间、天气、通用建议、来源、数据时间、有效期、降级和过期状态。服务端没有返回的路线、距离、预计出发时间和餐饮不显示。页面不申请定位、不写 URL 或存储，不使用浏览器通知。

## 测试

- 契约测试直接消费 D 的固定 JSON 夹具，覆盖任务、取消任务、正常天气、天气不可用、Demo、过期和未生成。
- API 与 Hook 测试覆盖请求路径、`If-Match`、重复提交、结果未知恢复、404、409、429、503 和旧请求取消。
- 页面与订单入口测试覆盖加载、空数据、只读、降级、错误、移动端和导航。
- 执行 `pnpm check`、定向 Playwright、`openspec validate travel-frontend-real-advice --strict` 和 `git diff --check`。
