# 设计

## 依据顺序

本次判断按以下顺序执行：最新 B 后端类型和校验器、`fixtures/agent/c` 固定 JSON、Controller/SSE 实际映射、已合入 OpenSpec 和前端解析。旧 PRD 名称只用于检查覆盖关系，不能单独作为新增字段依据。

## 旧名称到最新协议的映射

| PRD 名称             | 最新协议                                         | 当前处理                                       |
| -------------------- | ------------------------------------------------ | ---------------------------------------------- |
| `TEXT`               | `TEXT`                                           | PR #156 已实现类型化展示                       |
| `QUESTION`           | `QUESTION`                                       | PR #172 已实现交互卡片                         |
| `MOVIE_CARD`         | `MOVIE_CARD`                                     | PR #156 已实现类型化展示                       |
| `PLAN_CARD`          | `PLAN_CARD`                                      | PR #172 已实现方案卡；统一确认继续复用现有分支 |
| `TRAVEL_ADVICE_CARD` | 无正式 B 类型                                    | 等 B 提供正式协议                              |
| `ROUTE_CARD`         | 无正式 B 类型                                    | 等 B 提供正式协议                              |
| `BUSINESS_INTENT`    | `BUSINESS_INTENT`                                | PR #156 已实现安全业务入口                     |
| `ORDER_CONFIRM`      | `PLAN_CARD + actionId + actionType=CREATE_ORDER` | 已由统一确认卡覆盖，不新增枚举                 |
| `REFUND_CONFIRM`     | 无正式 B 退票 action                             | 等 B 决定统一确认卡协议并提供夹具              |
| `PROGRESS`           | `PROGRESS`                                       | PR #156 已实现类型化展示                       |
| `ERROR`              | `ERROR`                                          | PR #156 已实现安全错误展示                     |

## 当前正式协议

B 的卡片校验白名单和 C 的 `AgentCardPayloadType` 当前一致：`TEXT`、`QUESTION`、`MOVIE_CARD`、`PLAN_CARD`、`BUSINESS_INTENT`、`PROGRESS`、`ERROR`。确认卡不是第八类 payload，而是带 `actionId` 的 `PLAN_CARD` 受控变体。

建单固定夹具使用 `type=PLAN_CARD`、`actionType=CREATE_ORDER`。前端只把 `actionId` 留在模块内部，请求 `POST /api/v1/agent/actions/{actionId}/confirm` 时只发送 `{confirmed}`；执行中、结果未知和终态不再次提交。

## 缺少的 B 协议

以下三项已经负责人确认不阻塞当前“多轮问答 → 推荐 → 建单确认 → 结果恢复”联调。B 后续分别开 change，C 当前不增加前端类型、夹具或渲染分支。

### 出行建议卡

B 需要同时提供：

- 正式类型/白名单值和公开 payload 类型；
- 只含天气、通用建议、来源、数据时间、有效期和降级信息的字段表；
- 合法、缺字段、未知字段、危险 HTML、超大字符串 ID、过期和降级固定夹具；
- 卡片生成、持久化、Controller/SSE 输出映射和后端测试。

不得包含 `userId`、精确位置、手动地点、坐标、原始工具参数、未声明 URL 或模型原始推理。

D 的 `getTravelAdvice` 当前先作为 B 可调用的结构化只读 Tool，不等同于 Agent 卡片协议。B 后续一次性补事件类型、DTO、后端生成、SSE、脱敏字段、固定夹具和前端解析。

### 路线卡

B 需要同时提供正式类型、固定字段、固定夹具和 SSE 映射。公开字段只允许安全摘要、来源、数据时间、有效期和降级状态；不得包含精确起点、坐标、路线折线、途经点或地图几何。D 的路线页面 DTO 不能直接替代 B 的 Agent 卡片协议。

### 退票确认

当前 `AgentConfirmationActionType` 只有 `CREATE_ORDER`。B 需要明确退票继续使用 `PLAN_CARD + actionId`，并提供正式 `actionType`、脱敏 `displayLines`、固定卡片夹具、确认结果夹具、action 创建/执行/SSE 映射和恢复测试。若 B 决定独立 `REFUND_CONFIRM`，也必须先提供同等证据，C 不提前兼容。

退票确认先等待 A 提供确认后执行的退票 Application API。接口明确后，B 再单独补 actionType、脱敏卡片、确认结果和恢复测试。

## 当前主流程和历史 runId 恢复

当前主流程继续使用现有 `PLAN_CARD + actionId + CREATE_ORDER`。确认请求超时、5xx 或返回 `RESULT_UNKNOWN` 时，C 不重发 POST，而是重新拉取当前会话历史消息，按原 `actionId` 找到确认卡消息，读取消息顶层 UUID `runId`，再调用 `GET /api/v1/agent/runs/{runId}` 重建投影。

浏览器测试必须证明确认 POST 只发送一次、历史消息至少重新查询一次、运行查询使用历史消息的 UUID `runId`，并最终展示服务端快照状态。该测试属于当前主流程验收，不依赖出行、路线或退票协议。

本地浏览器测试使用固定 HTTP 响应验证前端恢复流程，不代替真实 Cookie 和发布代理联调。当前独立 worktree 没有 `.env`、演示账号或可登录联调入口，因此真实代理联调继续由 `frontend-agent-readonly-workspace` 的任务 5.5 记录，不能在本 change 冒充完成。

## PR #160 推荐协议与 PR #172 交互卡同步

最新 B `AgentCardPayloadResponse.PlanCard` 和固定 `plan-card.json` 已正式提供 `schemaVersion`、`algorithmVersion`、`plans`、`missingFactors`、`relaxationSuggestion`、`usedProfile`、`source`、`dataAt`、`expiresAt`、`degraded` 和 `expired`。每个方案项正式提供影片/影院/场次引用、影片名、影院名、价格、币种、开场时间、评分、排序分、推荐理由、来源、时效、可购状态和可选距离摘要。

C 对普通 `PLAN_CARD` 使用字段白名单严格校验：正式字段必须全部出现，允许为空的字段只能为协议声明的 null，未声明字段直接拒绝。PR #172 的组件继续只接收安全投影；页面展示影片名、影院名、价格与币种、开场时间、推荐理由、来源、时效和可购/过期状态，不展示业务 ID、排序分、内部证据、工具参数、精确位置或模型推理。

确认卡仍走现有 `PLAN_CARD + actionId + CREATE_ORDER` 分支，不套用普通推荐卡字段要求。旧 B 夹具不再作为普通 `PLAN_CARD` 的兼容输入。

## 后续前端实现边界

协议齐全后，C 才能扩展严格联合类型、运行时校验、安全投影、`AgentDisplayItemView` 注册、桌面/移动样式和测试。组件只消费安全投影，不接收原始 payload。

已知类型缺字段、出现未知字段或字段类型错误时拒绝事件且不推进游标；未知类型显示固定安全占位。重复 `eventId`、旧 `planVersion` 和 `stream.reset` 继续沿用现有恢复规则，SSE 重连不得重发写请求。

## 测试计划

每个未来正式类型至少覆盖合法固定夹具、缺字段、未知字段、危险 HTML、超大字符串 ID、过期、降级、旧计划、重复事件、`stream.reset`、桌面与移动展示、未知类型降级和非法已知类型不推进游标。

未来退票确认还需覆盖确认、拒绝、重复点击、网络超时、`RESULT_UNKNOWN`、403/404、409/422 和 SSE 重连不重发写请求。
