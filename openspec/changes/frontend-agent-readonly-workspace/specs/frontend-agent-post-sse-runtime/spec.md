## Purpose

定义 Agent POST SSE 的安全发送、事件校验、单活动请求、状态投影和断流恢复。

## ADDED Requirements

### Requirement: POST SSE 必须支持 JSON、Cookie、CSRF 和可取消流
系统 SHALL 使用独立 fetch 型客户端发送 `clientRequestId/content/context.entry`，携带 Cookie、CSRF Header 和可选 `Last-Event-ID`，解析 SSE `id/event/data` 与无 ID 心跳，并通过 AbortController 在卸载、切换会话和登出时关闭。系统 MUST NOT 使用 EventSource、WebSocket 或轮询替代。

#### Scenario: 数据跨网络分块
- **WHEN** 一个事件的字段和 JSON 跨多个读取分块
- **THEN** 客户端等待完整空行后解析一次事件
- **AND** 多事件同分块也分别投递

### Requirement: 同一工作区只能存在一个活动请求
系统 SHALL 使用 `IDLE`、`CONNECTING`、`STREAMING`、`COMPLETED`、`FAILED`、`RESULT_UNKNOWN`、`CANCELLED` 表示运行状态。活动流、提交中和结果未知期间 MUST 拒绝重复发送。

#### Scenario: 用户重复提交
- **WHEN** 同一工作区已有活动请求
- **THEN** 第二次提交不创建 fetch 请求
- **AND** 原请求状态和 clientRequestId 保持不变

### Requirement: 事件必须校验归属、字符串游标和计划版本
系统 SHALL 从 unknown 校验 `sessionId`、`runId`、`eventId`、`eventType` 和 `payload`。所有 ID、`eventId`、`lastEventId`、`watermark` MUST 保持字符串，不使用 Number、parseInt 或数值加一。其他会话/运行、重复或更旧事件、旧 planVersion 和非法事件 MUST 被忽略且不得推进游标。

#### Scenario: 超大十进制事件 ID
- **WHEN** eventId 为 `9007199254740993`
- **THEN** 前端按十进制字符串正确比较并保存
- **AND** 不发生精度丢失

#### Scenario: 非法事件
- **WHEN** payload 为空值或 ID/归属/JSON 不合法
- **THEN** reducer 保持原页面和游标
- **AND** 不显示 payload 原文

#### Scenario: 未知事件或卡片类型
- **WHEN** 外层字段有效但 `eventType` 或 `payload.type` 不在当前白名单
- **THEN** reducer 显示固定安全占位并推进到该事件游标
- **AND** 不显示原始 JSON、HTML、URL、工具参数或组件名

#### Scenario: 已知卡片缺少必填字段
- **WHEN** `TEXT/QUESTION/MOVIE_CARD/PLAN_CARD/PROGRESS/ERROR` 缺少对应必填字段或字段类型错误
- **THEN** reducer 保留当前内容并显示固定安全错误
- **AND** 不推进游标

### Requirement: 断流必须查询原运行且不得自动重发写操作
系统 SHALL 在已有 runId 时先 GET 原运行再按原游标恢复；首个合法事件前断流 MUST 进入 `RESULT_UNKNOWN` 且不自动重发原消息。20 秒没有事件或心跳 MUST 执行恢复检查。消息提交、取消和其他写操作不得因为断线自动重试。

#### Scenario: 已取得 runId 后断流
- **WHEN** 活动连接断开且已有 runId
- **THEN** 前端查询原运行并按原游标恢复或按终态重建
- **AND** 不生成新的 clientRequestId

#### Scenario: 首事件前断流
- **WHEN** 请求已发出但没有合法事件提供 runId
- **THEN** 页面进入 RESULT_UNKNOWN
- **AND** 不自动建立新的消息提交

### Requirement: stream.reset 必须使用会话水位线整体重建
系统 SHALL 先验证 `eventId === payload.watermark`，再 GET 运行详情和消息历史并整体替换页面投影，后续 `Last-Event-ID` MUST 使用 reset 的会话水位线。运行详情 `lastEventId` 不得替代会话水位线；不一致时必须停止恢复并显示固定安全错误。

#### Scenario: reset 水位线与运行游标不同
- **WHEN** reset eventId/watermark 为 `43` 且运行 lastEventId 为 `42`
- **THEN** 页面重建后继续使用 `43`
- **AND** 不取数值最大值

#### Scenario: reset 水位线不一致
- **WHEN** eventId 与 payload.watermark 不相等
- **THEN** 前端停止恢复并显示安全错误
- **AND** 不查询、重连或重发消息
