# agent-card-event-payload

## 背景与目标

当前 Agent SSE 卡片只给出零散 `messageType`，外层缺少 `planId`，C 无法只靠单条事件校验、渲染、去重和续传。本 change 统一卡片事件外层和六类 payload，并给出确认结果接口的可编码样例。

## 范围

- B 负责 SSE 外层 `planId` 输出、类型校验器、JSON 夹具和后端单元测试。
- 定义 `TEXT`、`QUESTION`、`MOVIE_CARD`、`PLAN_CARD`、`PROGRESS`、`ERROR` payload。
- 定义既有 `POST /api/v1/agent/actions/{actionId}/confirm` 的公开请求、响应和恢复规则。

## 非范围

- 不新增或修改 `agent_action`、写工具、建单、退票、数据库和迁移。
- 不实现位置定位、路线、前端组件或多工具编排。

## 验收

C 仅凭事件和 payload 可完成白名单校验、卡片渲染、`eventId` 去重和 `stream.reset` 后续传；确认请求和结果可直接按夹具编码。
