## Context

出行任务已经持久化，但 C 需要可直接展示的公开数据。D 只能通过已公开的 Application Port 读取跨模块摘要。

## Decisions

### 任务详情聚合

`TravelTaskQueryService.getMyTaskDetails` 先按 `CurrentUserAccessor` 查询本人任务，再调用：

1. A 的 `TravelOrderSummaryQueryPort.queryMyOrder(orderId)`，取得订单号、场次、影片和影院 ID、开场时间。
2. D 的 `ContentPurchaseQueryPort.findMovieSummaries`，取得标题、海报、来源和内容时间。
3. D 的 `ContentSummaryQueryPort.findCinemaSummaries`，取得名称、区域、地址、来源和时间。

任务不存在和跨用户访问在第一步统一为 `207001`。取消任务仍走详情聚合，状态保持 `CANCELLED`。任一依赖抛错、缺少摘要或 ID 无法解析，统一转换为 D 的 `207004`/503。

响应禁止价格、座位、库存、支付、退款、邮箱、精确位置和路线几何。

### 建议响应

REST 层把快照内部的 JSON 映射为 `WeatherResponse` 和 `AdviceItem` 数组。`weatherJson`、`adviceJson` 只保留兼容输出，固定夹具和 C 页面只使用类型化字段。没有天气时 `weather=null`，交通建议仍在 `advice`，并保留 `degraded/fallbackType`。

### 状态和错误

`207001` 为 404；取消刷新 `207002`、不可刷新或版本冲突 `207003` 为 409；五分钟内重复刷新 `107001` 为 429；摘要依赖不可用 `207004` 为 503。内部 A/D 错误码不向 REST 透传。

### 非本期能力

路线只保留原有 Application 设计和隐私约束，不接入真实 Provider；餐饮目标、接口和夹具从本 change 删除；邮件提醒不在本 DTO 交付中。

## Testing

- Application 单测覆盖取消任务详情、本人隔离、依赖不可用和类型化建议的天气缺失/过期/降级。
- MVC 集成测试覆盖成功详情、取消详情、建议响应和错误码。
- 固定 JSON 夹具与 OpenAPI 示例覆盖 C 页面所需全部成功、降级和错误场景。
