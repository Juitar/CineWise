# A 到 D 的订单摘要 Port 夹具

`order-summary-success.json` 是 `TravelOrderSummaryQueryPort` 成功返回值的固定字段示例，并非 REST 响应体。

| 场景 | A 内部异常语义 | D 对 C 页面处理 |
| --- | --- | --- |
| 非法 `orderId` | `100001` | D 定义自身接口错误，不透传 A 错误码 |
| 不存在或非本人 | `205001` | D 在任务归属校验后定义自身详情语义 |
| A 查询不可用 | `305001` | D 统一映射为自己的 HTTP 503 |

所有业务 ID 必须是无前导零的正十进制字符串；`showStartTime` 为 ISO 8601 且带 `+08:00` 偏移。
