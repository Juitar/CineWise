## Purpose

补充 C 可消费的出行任务详情和安全的提醒时间更新响应。

## MODIFIED Requirements

### Requirement: 本人任务详情和提醒时间更新必须使用不同响应边界

系统 SHALL 让 `GET /api/v1/travel/tasks/{taskId}` 返回任务、订单、影片和影院摘要；详情依赖不可用时返回 HTTP 503/`207004`。`PUT /api/v1/travel/tasks/{taskId}/reminder` 成功后 SHALL 只返回已提交任务的 `taskId/orderId/status/triggerAt/version`，不再读取详情摘要。

#### Scenario: 更新成功后摘要不可用

- **GIVEN** 提醒时间和版本条件更新已成功提交
- **WHEN** 订单或内容摘要随后不可用
- **THEN** 更新接口仍返回 HTTP 200 和新版本任务最小 DTO
- **AND** 客户端不因 503 而用旧版本重复提交

#### Scenario: 查询取消任务详情

- **GIVEN** 当前用户的任务状态为 `CANCELLED`
- **WHEN** 查询任务详情
- **THEN** 返回 HTTP 200 且 `status=CANCELLED`

#### Scenario: 详情摘要依赖不可用

- **WHEN** 订单、影片或影院摘要不可用
- **THEN** 返回 HTTP 503/`207004`，不透传内部错误码
