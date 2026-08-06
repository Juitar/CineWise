## 1. 已完成的基础能力

- [x] 1.1 D 出行任务、天气快照和刷新 Application 基础能力（Owner：D；已有单测和集成测试）
- [x] 1.2 本人校验、取消状态和 `207001/207002/207003/107001`（Owner：D；已有服务测试）
- [x] 1.3 天气 Provider、Demo 降级和通用交通建议生成（Owner：D；已有 Provider/服务测试）
- [x] 1.4 路线 Application 设计保留但不接入真实 Provider（Owner：D；本期不联调）

## 2. 本次公开契约交付

- [x] 2.1 新增任务详情聚合 DTO，调用 `TravelOrderSummaryQueryPort`、`ContentPurchaseQueryPort`、`ContentSummaryQueryPort`，覆盖取消任务和依赖不可用（Owner：D；Application 单测）
- [x] 2.2 将 REST 详情响应改为订单、影片、影院嵌套对象，禁止敏感票务和位置字段（Owner：D；Controller 映射和编译检查）
- [x] 2.3 将建议响应改为 `TravelAdviceResponse`，保留兼容 JSON 但页面夹具不依赖（Owner：D；正常/天气缺失/过期/未生成夹具）
- [x] 2.4 固定 `207004` 依赖不可用错误码，并验证五类业务错误映射（Owner：D；服务单测和现有刷新集成测试）
- [x] 2.5 添加详情、取消详情和建议状态固定 JSON 夹具（Owner：D；夹具路径核对）
- [x] 2.6 添加 OpenAPI 响应说明和示例（Owner：D；编译检查）

## 3. 文档和验证

- [x] 3.1 同步 proposal、design、`travel-task-reminder` 和 `travel-advice-query`，删除餐饮目标、接口和夹具要求（Owner：D；严格校验）
- [ ] 3.2 执行 `backend\\mvnw.cmd verify`（Owner：D；记录实际结果）
- [x] 3.3 执行 `openspec validate travel-reminder-experience --strict` 和 `git diff --check`（Owner：D；记录实际结果）

## 4. 明确不在本期

- [ ] 4.1 邮件提醒、餐饮实现和餐饮夹具（Owner：D；延期）
- [ ] 4.2 真实路线 Provider、支付、订单、内容、认证和前端页面（Owner：相应 Owner；本期不改）
