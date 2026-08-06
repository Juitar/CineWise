## Why

C 需要稳定的出行任务详情和建议响应，当前 REST 仍暴露 D 内部 JSON，无法直接开发页面。

## What Changes

- D 新增任务详情聚合 DTO：任务、订单、影片、影院公开摘要。
- D 将天气建议映射为类型化 `TravelAdviceResponse`，旧 JSON 仅作短期兼容字段。
- D 固定公开错误码、OpenAPI 示例和 JSON 夹具。
- 保留已有路线设计，但本 change 不接入真实路线 Provider。

## Non-Goals

- 不接入邮件提醒、餐饮、真实路线 Provider、支付、订单、内容、认证和前端页面。
- 不修改 A、B、C 的代码、Controller、Repository、Mapper 或数据库表。

## Owners and Dependencies

- D 负责 REST/Application DTO、映射、错误码、测试和夹具。
- A 只通过 `TravelOrderSummaryQueryPort` 提供订单摘要；D 不访问 A 持久化层。
- D 的 `ContentPurchaseQueryPort` 和 `ContentSummaryQueryPort` 提供影片、影院摘要。
- C 负责消费新响应和夹具；本 change 不改 C 代码。

## Acceptance

- 任务不存在或非本人返回 404/`207001`；取消任务详情仍返回 `CANCELLED`。
- 订单或内容摘要不可用返回 503/`207004`，不透传内部错误。
- 建议响应提供 `weather`、`advice`、来源、时间、过期和降级字段，天气不可用时仍保留交通建议。
- `backend\\mvnw.cmd verify`、严格 OpenSpec 校验和 `git diff --check` 通过。
