## 背景与依据

实现依据为 C 分支基线 `origin/dev@34c69c1` 的 `AgentController`、Agent API DTO、公共认证与请求层，以及已通过 B PR #98 合入的卡片 OpenSpec、事件输出、校验器、全部 C 正式夹具和测试断言。前端测试直接导入 `backend/src/test/resources/fixtures/agent/c/` 中的正式夹具；确认卡仍只读降级。批量清空以 `clearedCount` 为准，`stream.reset` 以 `payload.watermark` 为准。

## 模块结构

- `modules/agent/contract.ts`：从 `unknown` 校验会话、消息、运行和事件 DTO。
- `modules/agent/api.ts`：普通 REST；页面不直接请求网络。
- `modules/agent/sse.ts`：获取公共 CSRF 信息，发送 POST JSON，解析 SSE 分块并处理 HTTP/401。
- `modules/agent/projection.ts`：纯 reducer，负责事件归属、去重、计划版本和安全内容投影。
- `modules/agent/recovery.ts`：查询原运行、会话历史和 reset 整体替换。
- `modules/agent/useAgentWorkspace.ts`：唯一业务 Hook，持有会话、活动请求和恢复流程。
- `features/agent-workspace`：桌面与移动视图，仅共享同一个 Hook 状态。
- `pages/assistant`：读取路由参数并组合工作区。

## 类型和接口

REST 路径固定为当前 `AgentController` 的 7 个能力：创建/列出会话、消息历史、单个/批量清空、运行查询、运行取消。分页使用 `PageResult<T>`。所有服务端 ID 必须是非空字符串；`eventId`、`lastEventId`、`watermark` 还必须是非负十进制字符串，比较时按去除前导零后的长度和字典序处理，不转成 JavaScript `number`。

POST SSE 请求体只有 `clientRequestId/content/context.entry`，携带 Cookie、CSRF Header 和可选 `Last-Event-ID`。客户端解析 `id`、`event`、多行 `data` 和冒号心跳；未知字段可以存在，但只有白名单字段通过校验后才能进入 reducer。

认证窗口后续提供 JWT Cookie 操作续期时，本模块不增加刷新接口、不读取 JWT、不发送定时续期请求。REST、初次 POST SSE 和断线重连继续由浏览器携带当前 Cookie；SSE 心跳只重置无事件计时，不视为续期请求。最长登录时间后的 401 仍走公共单次清理并由路由守卫安全跳转登录。

## 状态和并发

工作区运行状态为 `IDLE | CONNECTING | STREAMING | COMPLETED | FAILED | RESULT_UNKNOWN | CANCELLED`。一个 Hook 只保存一个活动 `AbortController`；发送期间、结果未知期间和活动流存在时拒绝第二次提交。切换会话、页面卸载、401 和退出登录都会中止活动请求，旧请求通过连接序号和 sessionId 双重检查，不能写入新页面。

## 事件投影

事件先校验固定字段，再校验当前 sessionId/runId。首个合法事件确定 runId；之后其他运行事件被忽略。重复或更旧 eventId、旧 planVersion、非法 payload 都不推进游标。未知 `eventType` 或未知 `payload.type` 按 B 断言显示固定占位并推进游标，不读取原始 JSON、HTML、URL 或组件名；已知类型缺字段则保留当前视图、显示固定错误且不推进游标。

`TEXT/QUESTION/MOVIE_CARD/PLAN_CARD/PROGRESS/ERROR` 使用 `dev` 内正式夹具定义的白名单字段。推荐卡只显示事件实际提供的 ID、来源、数据时间、有效期和降级状态；缺少 `showId/price/startTime/expired/purchaseEligible/missingFactors` 时保持缺失，不从文案或其他接口补齐。过期状态仅由 `expired` 或 `expiresAt` 判断，降级状态仅由 `degraded` 判断。确认卡及确认结果只显示固定只读说明，不读取 `actionId/displayLines`，不生成确认请求或业务按钮。失败事件只追加安全错误，不清空已经展示的成功内容。

`LOCATION_PERMISSION` 只展示 `NOT_REQUESTED/GRANTED/DENIED/EXPIRED` 和手动输入提示。浏览器位置值只能在当前页面操作内存中短暂存在；本 change 不保存位置、不提交位置结果，也不把位置授权混入确认动作。

## 断流与恢复

- 首个合法事件前断流：进入 `RESULT_UNKNOWN`，不自动重发消息。
- 已取得 runId 后断流或 20 秒没有事件/心跳：中止旧流，GET 原运行；活动状态按原 lastEventId 建立恢复流，终态用快照和历史整体替换。
- 恢复 POST 仍使用同一个 clientRequestId 和 content 只为请求服务端按游标重放；客户端不会生成新 ID，也不会把它作为新的用户提交。若服务端当前实现无法区分续传，真实代理联调前不得把此项标记完成。
- `stream.reset`：先验证事件 ID 与 `payload.watermark` 完全一致，再并行 GET 运行详情与消息历史，整体替换当前投影；下一次请求头使用 reset 的会话水位线，即使运行详情 `lastEventId` 更小也不能替代。水位线不一致时停止并显示固定安全错误。

## 认证和安全

路由使用现有 `RequireAuth` 和 `createLoginRedirect`。首页只负责导航，匿名状态下不调用 Agent API。REST 和 SSE 401 复用全局单次未认证处理；SSE 客户端不读取 Cookie/JWT。页面用 React 文本节点展示内容，不使用危险 HTML，不展示 payload 原文、模型过程、系统提示、工具参数、第三方完整响应、凭证、精确位置、完整订单或堆栈。

首页已经输入但尚未认证的草稿只放在当前 SPA 模块内存中，登录回跳后读取一次并清除；不进入 URL、`localStorage` 或 `sessionStorage`，刷新或关闭页面后不保留。匿名阶段只保存这段未提交草稿，不创建会话、运行、消息或 SSE。

## 响应式方案

`>=1024px` 显示会话侧栏和主对话区；更窄视口使用主对话区和会话抽屉。响应式只改变视图组件，不重新创建 Hook，因此 sessionId、runId、消息、占位卡片和游标不会丢失。移动按钮和输入控件最小触控尺寸 44px。

## 测试和回退

前端单测直接导入 `dev` 内全部正式 Agent C 夹具，并以其实际 JSON 验证 DTO 和投影；不依赖远端 Git 对象、临时路径或复制夹具。测试覆盖 DTO、六类 payload、位置状态、未知/缺字段、SSE、事件归属/去重/旧计划、卡片恢复、结果未知、20 秒恢复和 reset `43/42`。回退只需删除新增路由和前端 Agent 模块，不涉及后端或数据库。
