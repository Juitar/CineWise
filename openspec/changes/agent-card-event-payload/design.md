# 设计

## 事件与类型

`AgentRunResponse.EventSummary` 和 `AgentInteractionRuntimeService.EventView` 增加 `planId`，运行快照与 SSE 共用同一外层。`AgentCardEventValidator` 是 B 提供的固定白名单校验器：已知且完整的卡片可渲染；未知类型安全降级；已知类型缺字段拒绝。

payload 最小字段：`TEXT{text,format?}`；`QUESTION{questionId,questionKind,message,options,allowFreeText,input?,requiresConfirmation,expiresAt,locationAuthorization?}`；`MOVIE_CARD{title,movies,source,dataAt,expiresAt,degraded,fallbackType?}`；`PLAN_CARD{title,plans,source,dataAt,expiresAt,degraded}`；`PROGRESS{stage,status}`；`ERROR{code,message,retryable}`。所有文本按纯文本显示，不接受 HTML。

位置授权只表示 UI 问题和用户结果，不携带坐标、住址或地图几何。`NOT_REQUESTED/GRANTED/DENIED/EXPIRED` 由 C 的浏览器权限结果映射；`DENIED` 允许手动地点，`EXPIRED` 用新的 `questionId` 再问，重复结果按原 `questionId` 去重。

## 确认接口

接口沿用现有实现：`POST /api/v1/agent/actions/{actionId}/confirm`。请求只有 `confirmed`，因为 actionId 已绑定用户、运行、计划版本和参数摘要。响应 `status` 是 `PENDING_CONFIRMATION/EXECUTING/RESULT_UNKNOWN/SUCCEEDED/FAILED/EXPIRED/REJECTED/INVALIDATED`。本 change 仅提供夹具，不调用确认服务、不改写工具。

## 恢复和测试

C 先按十进制字符串去重 `eventId`，跳号保留等待续传；收到 `stream.reset` 后按 watermark 拉取运行快照并原子替换，再从后续 eventId 消费。旧 `planVersion` 卡片只读忽略，不覆盖当前计划。测试覆盖正常、降级、缺字段、类型错误、位置授权、确认结果和续传夹具；不涉及数据库，故不要求 MySQL CI。
