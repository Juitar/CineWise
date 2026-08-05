## Why

现有 Agent 已能保存会话、运行和只读计划，但不能把用户的明确确认安全地转换为一次建单。若确认卡、参数、用户或运行版本没有由服务端绑定，重复点击、断线和写结果未知都可能导致重复建单或错误展示结果。

本 change 只补齐 Agent 的一次性确认动作，以及确认通过后调用 A 提供的“创建订单”类型化能力所需的边界和恢复规则。

## What Changes

- 新增 B 自有确认动作的领域模型、状态规则、参数摘要、归属与有效期校验；确认动作只能由服务端根据已校验的建单 Command 创建。
- 新增 `POST /api/v1/agent/actions/{actionId}/confirm`。请求体只接受 `confirmed`，不接收用户、金额、订单、座位、工具参数、参数摘要或确认凭证。
- 规定确认拒绝、过期、参数变化、计划版本变化、重复确认、运行结束、越权和业务数据失效时不得调用写工具。
- 规定确认竞争、结果未知和断线恢复：同一 action 只能产生一个稳定幂等标识；超时、网络或 SSE 中断只按原标识查询，不自动重发建单。
- A 已确认提供 `CreateOrderTool.execute(ToolContext, CreateOrderForAgentCommand)`；B 只经该类型化 Tool 调用，并提供公开 `AgentActionAuthorizationPort` 供 A 校验 action。A 的 Agent Tool 授权失败统一映射 `205004`。
- `agent_action` 的迁移版本尚未正式分配。`origin/dev` 已有 V010，V011、V012 的归属、SQL 和发布顺序必须先由 C 明确并由 A 确认；只有随后由 A 正式分配 V013、静态审查字段后，B 才能提交 SQL 草案。本 change 当前不新增或执行迁移。

## Capabilities

### New Capabilities

- `agent-confirmed-order-action`: 服务端创建、展示、确认、拒绝和恢复一次性建单确认动作。
- `agent-confirmed-order-execution`: 确认通过后的单次建单适配、结果未知恢复、并发防重与安全 SSE 结果。

### Modified Capabilities

- `agent-post-sse-interaction`: 增加服务端受控确认卡及确认结果事件，不暴露原始 Command、幂等键或完整订单数据。
- `agent-session-run-persistence`: 为确认动作与运行状态关系增加持久化、CAS 和恢复约束；实际迁移须由 A 分配版本并验证。

## Impact

- B：`agent` 的 domain、application、api、persistence 端口、SSE 事件、Mock/夹具和测试。
- A：已确认建单 Tool、DTO、调用身份、稳定键、结果查询和结果未知语义；待 C 明确 V011/V012 后，正式分配 V013、审查 SQL，并安排共享数据库验证。
- C：已确认确认卡和结果事件的前端展示、CSRF 请求行为与断线恢复消费；另待 C 明确 V011/V012 各自的正式归属、SQL 范围和发布顺序。本 change 不实现前端。
- 数据库：计划新增 B 拥有的 `agent_action`，但在 C 确认 V011/V012 顺序、A 正式分配 V013 并完成字段静态审查前不得写 SQL 或执行迁移。涉及该表、CAS、唯一约束、并发确认和恢复时，必须在 GitHub Actions 的一次性 MySQL 8.4 `cinewise_agent_it` 中验证，H2 不替代该验证。
