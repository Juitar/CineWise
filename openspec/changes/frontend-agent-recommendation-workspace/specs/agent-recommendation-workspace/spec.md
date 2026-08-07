# Agent 推荐方案工作区规范

## ADDED Requirements

### Requirement: 正常用户进入推荐方案工作区

系统 MUST 将首页 Agent 的正常用户入口指向受登录保护的推荐方案工作区，并保留 `/assistant` 作为开发调试页。

#### Scenario: 从首页提交需求

- **WHEN** 用户在首页 Agent 输入有效需求并提交
- **THEN** 系统进入 `/recommendations`，创建会话后替换为 `/recommendations/:sessionId`，并只提交一次首页草稿

### Requirement: 真实方案与对话共用运行状态

系统 MUST 使用同一个 Agent Hook、DTO、投影和恢复逻辑驱动方案区及对话区。

#### Scenario: 收到 PLAN_CARD

- **WHEN** 当前会话收到包含一至三个方案的有效 `PLAN_CARD`
- **THEN** 中间区展示投影后的真实方案，对话区显示方案摘要，且页面不创建第二条 SSE 连接

#### Scenario: 新方案替换旧方案

- **WHEN** 会话收到更新的 `PLAN_CARD`
- **THEN** 旧方案选择不再影响新方案，页面使用最新卡片的选择状态

### Requirement: 方案区只展示白名单字段

系统 MUST 仅展示投影后的方案标签、影片、影院、场次、票价、评分、推荐理由、来源、时间、有效期和可用状态。

#### Scenario: 后端没有路线和餐饮信息

- **WHEN** `PLAN_CARD` 不包含地图、路线、附近餐饮、座位、影厅或海报字段
- **THEN** 页面不生成静态占位数据，也不显示内部 ID、工具参数或模型原文

### Requirement: 选中方案可继续对话

系统 MUST 允许用户选择当前最新推荐中的某个方案，并通过现有消息流请求解释或继续调整。

#### Scenario: 点击方案

- **WHEN** 用户点击当前最新 `PLAN_CARD` 的第 N 个方案
- **THEN** 页面高亮该方案，并通过现有消息接口发送对当前最新推荐第 N 个方案的解释请求

#### Scenario: 基于选择二次调整

- **WHEN** 用户选中方案后输入调整要求
- **THEN** 页面通过现有消息接口发送带当前方案序号语义的文本，不新增请求 DTO 或回传整张方案

#### Scenario: 终态运行后开始下一轮消息

- **WHEN** 当前运行已经结束且用户发送下一条消息
- **THEN** 工作区保留历史内容和会话级最后事件游标，清除上一运行的 `runId/planVersion` 绑定，并以新流的首个有效事件绑定新运行

### Requirement: 选座入口只来自正式业务意图

系统 MUST 只在 Agent 投影得到已校验的 `SELECT_SEATS` 本站路径时显示“去选座”。

#### Scenario: 只有 PLAN_CARD 快照

- **WHEN** 页面只有包含 `showId` 的 `PLAN_CARD`，但没有 `SELECT_SEATS`
- **THEN** 页面不得自行拼接选座路由，不显示可购票或可支付结果

#### Scenario: 收到 SELECT_SEATS

- **WHEN** 页面收到字段完整且通过校验的 `BUSINESS_INTENT SELECT_SEATS`
- **THEN** 右侧对话继续使用现有 `/shows/:showId/seats` 白名单路由
