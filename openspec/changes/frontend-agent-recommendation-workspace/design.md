# 实现设计

## 页面和路由

新增 `/recommendations` 与 `/recommendations/:sessionId`，均使用 `RequireAuth`。无会话 ID 时复用 `useAgentSessionBootstrap` 创建会话，并替换为带 ID 的路由。`/assistant` 及其会话路由保持不变。

首页不承载 Agent 消息输入。桌面端和移动端使用语义化链接进入 `/recommendations`，由现有登录守卫和会话创建页面继续处理认证与会话初始化。首页不写入待提交草稿，不触发消息提交或 SSE；`entryDraft` 兼容模块和工作区读取逻辑保持不变，避免扩大本次修改范围。

## 组件与状态

扩展现有 `AgentWorkspace`，增加推荐工作区模式和路由前缀。组件内部仍只调用一次 `useAgentWorkspace(sessionId)`：

- 最新的非确认 `plan-card` 投影作为中间方案区数据源；
- 选择状态只保存在页面内，并绑定卡片 `key` 与数组序号；新 `PLAN_CARD` 到达后重新选择第一项；
- 点击方案发送“我选择了当前最新推荐中的第 N 个方案，请解释这个方案”；
- 二次输入与快捷调整在发送前加上同一序号引用，不新增请求字段，不回传完整方案或内部工具参数；
- 终态运行后的新消息保留会话级 `lastEventId` 作为增量游标，清除上一运行的 `runId` 和 `planVersion`，由新流首个有效事件重新绑定；活动运行、断线恢复和确认恢复仍保持原绑定，不能借此接收其他运行事件；
- 右侧方案卡使用摘要模式，其余 QUESTION、确认卡、进度、错误和业务意图继续使用统一卡片投影。

## 字段与安全

中间区只读取投影后的 `AgentPlanDisplay`：

- 展示 `planType/movieName/cinemaName/startTime/currency/price/rating/reasons/source/dataAt/expiresAt/expired/purchaseEligible`；
- `showId` 仅作为 React 稳定键的一部分，不显示、不用于自行构造业务路由；
- 本轮不展示 `distanceMeters`，不生成地图、路线、餐饮、座位、影厅或不存在的海报字段；
- 只有投影产生的 `selectSeatsPath` 才能渲染“去选座”。

## 响应式和恢复

桌面端由 `UserLayout` 提供全局左导航，页面内为中间方案区和右侧对话区。移动端按方案区、对话区单列排列。两种视图共享同一 Hook，因此 SSE 游标、断线恢复、历史消息、确认防重和取消规则不分叉。

## 测试

- 组件测试覆盖真实方案字段、选择高亮、选中后的消息、二次调整、卡片更新、无方案和 `SELECT_SEATS`。
- 页面测试覆盖会话创建后跳转至 recommendations 路由。
- 首页测试覆盖桌面端和移动端入口均指向 `/recommendations`，且不再展示首页 Agent 输入、发送按钮和静态推荐词。
- 执行前端类型检查、相关 Vitest、构建、OpenSpec 严格校验和 `git diff --check`。
