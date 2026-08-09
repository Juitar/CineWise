## 触发与起点选择

路线卡片在用户点击后展示两种互斥起点：`CURRENT_LOCATION` 与 `MANUAL_PLACE`。

- 当前定位仅在用户确认共享并点击规划时调用 `navigator.geolocation`；HTTP、不支持、拒绝或超时均不自动重试，并提示可改用手动地点。
- 手动地点要求用户输入最多 200 个字符的地点文本，并确认“该地点将发送给高德用于本次路线规划”。
- 起点方式、地点文本和浏览器坐标只存在页面局部状态与当前 HTTP 请求，离开页面、成功、失败或取消后清除；不进入 URL、存储、日志、埋点、缓存、订单、任务快照、Agent 或 SSE。

## 提议的公开 API 契约（待 D/C 确认）

继续使用 `POST /api/v1/travel/tasks/{taskId}/route`，请求改为显式判别联合：

```json
{
  "originType": "MANUAL_PLACE",
  "placeText": "长沙市雨花区万家丽中路某小区",
  "travelMode": "WALKING",
  "thirdPartySharingConfirmed": true
}
```

浏览器定位使用 `originType: "CURRENT_LOCATION"` 并保留 `longitude`、`latitude`。同一请求中不得同时携带地点文本与坐标。

后端按“应用服务预检 → 起点解析 → 应用服务执行”顺序调用公开 Application API：

- Controller 先调用 `BasicRouteService.prepareMyRoute()`，在不持有起点的前提下依次确认任务归属、影院终点、第三方共享确认和出行方式；
- 仅预检成功后，`CURRENT_LOCATION` 才调用 `BrowserUserLocationAdapter.fromBrowser()`，`MANUAL_PLACE` 才调用 `UserLocationAdapter.fromPlaceText()`；
- 最后把只存在当前调用栈的 `ResolvedGeoPoint` 与预检上下文交给 `BasicRouteService.planPreparedMyRoute()`。

地点编码结果为 0 或多个候选时，不返回坐标或候选原文；返回稳定“地点无法唯一确定”错误。`CITY`、`DISTRICT` 结果返回稳定“地点粒度不足”错误。具体错误码由 D 分配并由 C 同步到错误映射。

## 权限、隐私和恢复

路线请求是一次性写操作。请求中、结果未知或网络失败时禁用重复点击，不自动重发；用户可重新输入地点后主动再次提交。后端不记录坐标/地点文本，响应仍只返回路线摘要。

所有起点方式均须在调用高德前确认第三方共享。未确认共享、无权或不存在任务、缺少影院终点或非法出行方式时，系统必须在地点解析前失败，不调用 `UserLocationAdapter.fromPlaceText()`，也不得向高德发送地点。当前定位失败不影响手动路线；手动编码失败不回退到设备定位。

## 验证

- D：唯一地址/POI、空结果、多结果、城市/区县粒度、未确认共享、越权任务、Provider 失败与隐私扫描。
- C：HTTP 环境下定位失败后可切换手动输入；手动成功、歧义、粒度不足、422/503、重复点击与移动端。
- 联调：确认路线 POST 不含地点文本或坐标的日志、缓存、响应与 Agent/SSE 记录。
