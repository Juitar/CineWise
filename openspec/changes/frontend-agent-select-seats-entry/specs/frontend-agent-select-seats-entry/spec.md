## ADDED Requirements

### Requirement: 已确认场次可进入选座页

前端 SHALL 仅在 `card + BUSINESS_INTENT + SELECT_SEATS` 已通过外层计划字段和无前导零正十进制 `businessRef.showId` 校验时显示选座入口。

#### Scenario: 合法选座卡片

- **WHEN** 当前会话和运行收到合法 `SELECT_SEATS` 卡片
- **THEN** 前端显示“去选座”入口并导航到 `/shows/{showId}/seats`
- **AND** 路径中的 `showId` 必须 URL 编码并保留为字符串

#### Scenario: 字段缺失或非法

- **WHEN** 选座卡片缺少 `planId`、`planVersion`、`nodeId`、`businessRef.showId`，或 `showId` 不是无前导零正十进制字符串
- **THEN** 前端不显示选座入口
- **AND** 不推进事件游标

### Requirement: 入口不触发交易写操作

前端 SHALL 不从 Agent 卡片生成建单、支付、确认、锁座或座位提交请求。

#### Scenario: 用户点击选座入口

- **WHEN** 用户点击“去选座”
- **THEN** 前端只执行路由跳转
