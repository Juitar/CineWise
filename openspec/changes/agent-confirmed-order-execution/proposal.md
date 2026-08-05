## Why

现有 Agent 已能保存会话、运行和只读计划，但不能把用户的明确确认安全地转换为一次建单。若确认卡、参数、用户或运行版本没有由服务端绑定，重复点击、断线和写结果未知都可能导致重复建单或错误展示结果。

本 change 只补齐 Agent 的一次性确认动作，以及确认通过后调用 A 提供的“创建订单”类型化能力所需的边界和恢复规则。

## What Changes

- 新增 B 自有确认动作的领域模型、状态规则、参数摘要、归属与有效期校验；确认动作只能由服务端根据已校验的建单 Command 创建。
- 新增 `POST /api/v1/agent/actions/{actionId}/confirm`。请求体只接受 `confirmed`，不接收用户、金额、订单、座位、工具参数、参数摘要或确认凭证。
- 规定确认拒绝、过期、参数变化、计划版本变化、重复确认、运行结束、越权和业务数据失效时不得调用写工具。
- 规定确认竞争、结果未知和断线恢复：同一 action 只能产生一个稳定幂等标识；超时、网络或 SSE 中断只按原标识查询，不自动重发建单。
- A 已确认目标公开入口为 `CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)`；底层原子建单和按当前用户加 `clientRequestId` 的恢复查询已在 `OrderApplicationService`，但 B 不得直接依赖。A 将在独立 change 提供 `com.miaoyu.ticket.order.api` 的 Tool、Command、`AgentOrderResult` 和原请求查询入口；B 只经该类型化 Tool 调用，并提供公开 `AgentActionAuthorizationPort` 供 A 校验 action。A 的 Agent Tool 授权失败统一映射 `205004`。
- A 已确认 V011 已发布；原计划分配给邀请码种子的 V012 已取消，并正式将 V012 分配给 `agent_action`。B 现在只提交 `V012__create_agent_action_table.sql` 草稿给 A 静态审查；A 审查和明确授权前不得执行、合入共享 `dev` 或连接任何数据库。

## Capabilities

### New Capabilities

- `agent-confirmed-order-action`: 服务端创建、展示、确认、拒绝和恢复一次性建单确认动作。
- `agent-confirmed-order-execution`: 确认通过后的单次建单适配、结果未知恢复、并发防重与安全 SSE 结果。

### Modified Capabilities

- `agent-post-sse-interaction`: 增加服务端受控确认卡及确认结果事件，不暴露原始 Command、幂等键或完整订单数据。
- `agent-session-run-persistence`: 为确认动作与运行状态关系增加持久化、CAS 和恢复约束；实际迁移须由 A 分配版本并验证。

## Impact

- B：`agent` 的 domain、application、api、persistence 端口、SSE 事件、Mock/夹具和测试。
- A：已确认建单 Tool、DTO、调用身份、稳定键、结果查询、结果未知语义和 V012 分配；待 A 静态审查 SQL 并安排共享数据库验证。
- C：已确认确认卡和结果事件的前端展示、CSRF 请求行为与断线恢复消费；本 change 不实现前端。
- 数据库：计划新增 B 拥有的 `agent_action`，V012 草稿仅供 A 静态审查，未经 A 明确授权不得执行。涉及该表、CAS、唯一约束、并发确认和恢复时，必须在 GitHub Actions 的一次性 MySQL 8.4 `cinewise_agent_it` 中验证，H2 不替代该验证。
