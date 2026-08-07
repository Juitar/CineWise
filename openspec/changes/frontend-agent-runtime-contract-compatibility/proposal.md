# Agent 前端运行状态兼容修复

## 背景与目标

最新 `origin/dev` 的 B 后端已经返回 `WAITING_LOCATION`，但用户 Agent 工作区仍按旧类型拒绝该合法运行状态。本变更让工作区可以恢复等待位置运行，避免用户在原运行仍活动时重复发送消息。

## 范围

- Agent 用户工作区接受 `WAITING_LOCATION`，将其作为不可重复发送消息、可取消的活动运行展示。
- 补运行 DTO、Hook、页面和恢复测试。

## 非范围

- 不修改 B 的 Agent 后端、运行状态机、位置等待 REST、SSE 或数据库。
- 不实现位置采集、坐标上传、距离推荐或 D 的业务规则。
- 不修改 A 的票务交易、订单、支付或场次规则。
- 不重复修改管理端 Agent 轨迹；最新 `origin/dev` 的 PR #177 已提供 `WAITING_LOCATION`、可空字段、白名单和页面降级。
- 不修改认证、Cookie、CSRF 和发布代理配置；真实联调继续使用现有联调任务记录。

## Owner 与影响

- Owner：C。
- 受影响接口由 B 已合入：`GET /api/v1/agent/runs/{runId}`。
- 不新增或修改后端字段，不需要 A、B、D 再决定。

## 验收结果

- `WAITING_LOCATION` 运行不再触发前端 DTO 错误，页面禁止发送第二条消息并保留取消入口。
- 相关单元、组件测试、前端检查和 OpenSpec 严格校验通过。
