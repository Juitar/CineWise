## Decisions

### 任务详情

`GET /api/v1/travel/tasks/{taskId}` 先校验本人任务，再依次读取 A 的订单摘要、D 的影片摘要和影院摘要。依赖异常、缺失记录或非法 ID 一律转换为 D 的 `207004`/503；不透传内部错误。取消任务仍返回 `CANCELLED` 详情。

### 提醒时间更新

`PUT /api/v1/travel/tasks/{taskId}/reminder` 只返回已提交的 `taskId/orderId/status/triggerAt/version`。更新成功后不读取订单或内容摘要，避免依赖短暂不可用时客户端收到 503 却无法安全按原版本重试。

### 建议降级

有天气对象时，`advice` 可以同时包含 `WEATHER` 和 `TRANSPORT`；天气不可用时 `weather=null` 且只保留 `TRANSPORT`。降级原因由 `degraded/fallbackType` 表达。Demo 天气已有明确天气对象，因此保留两类建议并标识 Demo。

### 测试

MockMvc 用固定夹具覆盖正常、天气不可用、Demo、过期、未生成五种建议状态，并验证写接口不再访问详情聚合。
