# 设计

## 模块和接口

`frontend/src/modules/travel` 增加路线请求与响应类型、运行时校验、API 和独立 `useTravelRoute` Hook。页面不直接调用网络。请求复用公共 `apiRequest<T>()`，由公共层处理 Cookie、CSRF 和 401；公共层不会自动重放写请求。

正式接口为 `POST /api/v1/travel/tasks/{taskId}/route`。请求体固定为 `{longitude, latitude, travelMode, thirdPartySharingConfirmed}`，其中 `travelMode` 首版只允许 `DRIVING | WALKING`。响应复用 D 的 `BasicRouteResult`：`provider`、`travelMode`、`durationMinutes`、`suggestedDepartureAt`、`source`、`dataTime`、`expiresAt`、`isExpired`、`degraded`、`fallbackType`。

## 一次性定位和隐私

`requestCurrentCoordinates()` 只在用户点击后的 Hook 调用栈中调用 `navigator.geolocation.getCurrentPosition()`。经纬度先检查为有限数值且处于合法范围，但不截断或四舍五入；D 负责校验原始范围并规范到 6 位。

坐标只作为函数局部变量传给一次 POST，不写 React state、URL、localStorage、sessionStorage、IndexedDB、日志或错误文本。Hook state 只保存定位/请求阶段、路线摘要和固定错误文案。卸载时 AbortController 取消仍在等待的 HTTP 响应，并忽略迟到的定位回调。

## 页面状态

页面展示第三方共享说明、交通方式和确认框。未确认时按钮禁用；点击后进入定位中，再进入规划中，这两个阶段均禁止重复提交。成功后展示路线摘要。新请求失败时清除旧摘要并统一显示“路线暂不可用”。

浏览器不支持定位、拒绝、超时、返回非法坐标，以及 HTTP 422/107002、503/307001、网络失败、超时或响应校验失败均使用固定路线不可用提示。401 继续使用公共登录处理。404/207001 触发原任务重新查询，使页面按既有规则隐藏不可访问的任务数据。

任务为 `CANCELLED`、`COMPLETED`、`FAILED` 或建议过期时不允许规划路线。页面卸载、任务切换或重新规划时清理旧路线摘要和本地确认状态。

## 测试

- API/契约：正式路径、精确请求字段、响应校验、禁止旧字段。
- Hook：成功、未确认、拒绝、失败、非法坐标、重复点击、HTTP 错误、超时/断网不重发、卸载忽略迟到结果。
- 页面：共享说明、方式选择、只读、摘要、固定错误文案和任务重新查询。
- Playwright：桌面与移动授权成功、拒绝授权、请求体和 URL/存储隐私断言。
