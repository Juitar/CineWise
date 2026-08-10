# frontend-travel-current-route Specification

## Purpose
TBD - created by archiving change frontend-travel-current-route. Update Purpose after archive.
## Requirements
### Requirement: 路线规划必须由用户主动确认触发

系统 MUST 只在用户确认本次坐标将发送给路线服务并点击“规划路线”后，调用一次 `navigator.geolocation.getCurrentPosition()`；页面加载、任务加载和建议刷新不得申请定位。

#### Scenario: 用户未确认共享

- **WHEN** 用户没有勾选本次位置共享确认
- **THEN** 规划按钮不可提交，系统不申请浏览器定位，也不调用路线接口

#### Scenario: 用户确认并主动规划

- **WHEN** 用户选择驾车或步行、确认共享并点击规划路线
- **THEN** 系统申请一次浏览器定位，并且定位或请求期间禁止重复提交

### Requirement: 前端必须按 D 的正式路线接口提交一次性坐标

系统 MUST 调用 `POST /api/v1/travel/tasks/{taskId}/route`，请求体只包含浏览器原始数值 `longitude`、`latitude`、`travelMode` 和 `thirdPartySharingConfirmed=true`。首版 `travelMode` 只允许 `DRIVING`、`WALKING`。

#### Scenario: 定位和路线请求成功

- **WHEN** 浏览器返回合法坐标且 D 返回合法路线摘要
- **THEN** 页面展示交通方式、耗时、预计出发时间、来源、数据时间和有效期
- **AND** 请求中不包含 `originValue`、`cinemaArea`、地址、影院坐标、`distanceContextId` 或 `TRANSIT`

#### Scenario: 写请求超时或断网

- **WHEN** 路线 POST 超时、断网或响应格式无法确认
- **THEN** 系统显示“路线暂不可用”，不自动重发原 POST

### Requirement: 精确坐标不得离开本次请求内存

系统 MUST 只在定位回调和本次请求局部变量中使用精确坐标。坐标不得进入 React state、URL、浏览器持久化存储、日志、埋点、错误文案、Mock 快照、Agent 消息或 SSE。

#### Scenario: 请求完成、失败或页面卸载

- **WHEN** 路线请求成功、失败、取消，或者用户离开页面
- **THEN** 页面不保留坐标，URL 和浏览器存储中也不存在本次精确坐标

### Requirement: 路线不可用必须安全降级

系统 SHALL 将定位拒绝或失败、浏览器不支持、坐标非法、影院无坐标和路线服务失败统一展示为“路线暂不可用”，不得使用城市中心点、影院区域、地址文本或其他猜测位置替代。

#### Scenario: 浏览器定位不可用

- **WHEN** 用户拒绝定位、定位超时、浏览器不支持或返回非法坐标
- **THEN** 页面显示“路线暂不可用”，不调用路线接口

#### Scenario: D 返回路线不可用

- **WHEN** 路线接口返回 HTTP 503 或 `307001`
- **THEN** 页面显示“路线暂不可用”，保留影院地址、天气和通用建议

#### Scenario: 任务不存在或无权访问

- **WHEN** 路线接口返回 HTTP 404 或 `207001`
- **THEN** 页面重新查询原 `taskId`，并按既有任务页面规则隐藏不可访问的任务数据

### Requirement: 失效任务不得规划新路线

系统 MUST 在任务取消、完成、失败或建议过期时禁用路线规划，但可保留已经展示的非坐标路线摘要作为只读信息。

#### Scenario: 页面进入只读状态

- **WHEN** 任务状态为 `CANCELLED`、`COMPLETED`、`FAILED` 或建议 `isExpired=true`
- **THEN** 页面不申请定位、不提交路线请求，并明确显示现有页面只读提示

