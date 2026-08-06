## Why

C 需要直接展示出行任务详情和建议，现有响应仍含内部 JSON，且任务更新接口不能在写入成功后依赖外部摘要再决定成功与否。

## What Changes

- 新增 D 的任务详情聚合 DTO，只通过 A/D 公开 Application Port 读取订单、影片和影院摘要。
- 将建议 REST 响应固定为类型化 `weather` 和 `advice[]`；旧 JSON 仅兼容保留。
- 保持提醒时间更新响应为已提交任务的最小 DTO，不在写入后补查外部摘要。
- 增加 `207004`、OpenAPI 示例、固定夹具和 C 页面状态测试。

## Non-Goals

- 不改变支付事件、任务创建、迁移、邮件投递、路线、餐饮或任何 A/B/C 代码。

## Owners

- D 负责实现、夹具和验证。
- A 提供既有 `TravelOrderSummaryQueryPort`；C 消费固定 DTO 和夹具。
