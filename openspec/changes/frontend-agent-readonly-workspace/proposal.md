## Why

最新 `origin/dev@34c69c1` 已提供本人 Agent 会话、消息历史、运行查询、取消和 POST SSE 接口，但用户端还没有受登录保护的 Agent 工作区。B 的卡片事件协议已通过 PR #98 合入 `dev`（合入提交 `019716f`）；C 本次直接使用正式夹具实现并复验只读卡片投影。

## What Changes

- 新增 `/assistant` 与 `/assistant/:sessionId` 登录保护路由，复用现有安全 `returnUrl`，匿名用户不会创建会话或运行。
- 新增 Agent DTO 校验、REST 模块 API/Hook、fetch 型 POST SSE 客户端、事件投影、单活动请求状态机和断流恢复。
- 所有业务 ID、事件游标和 reset 水位线保持字符串；事件校验失败、归属不符、重复或旧计划事件不更新页面和游标。
- `stream.reset` 只接受 `eventId === payload.watermark`，按运行详情和消息历史整体重建，并继续使用会话水位线。
- 新增桌面会话侧栏和移动抽屉工作区，共享 API、DTO、Hook、reducer 和恢复逻辑。
- 按 `dev` 内正式 JSON 和测试断言展示 `TEXT`、`QUESTION`、`MOVIE_CARD`、`PLAN_CARD`、`PROGRESS`、`ERROR`，未知类型固定降级，已知类型不完整时保留原视图且不推进游标。
- 位置授权只展示 `locationAuthorization` 的安全状态，不保存或提交精确位置。
- 确认结果只作历史恢复和状态说明；不调用 `POST /api/v1/agent/actions/{actionId}/confirm`，不生成确认、拒绝、购票或支付按钮，不实现建单、支付、退票或其他写工具；不修改后端、数据库及 A/B/D 模块。

## Capabilities

### New Capabilities

- `frontend-agent-session-workspace`：登录用户管理本人会话、恢复历史并在桌面和移动布局中继续对话。
- `frontend-agent-post-sse-runtime`：通过 POST SSE 安全消费事件、取消运行并按游标恢复。
- `frontend-agent-safe-projection`：只展示当前协议可以确认的类型化卡片、文本、进度、状态和安全占位。

### Modified Capabilities

无。

## Impact

- Owner 为 C；修改 `frontend/src/modules/agent`、`frontend/src/features/agent-workspace`、`frontend/src/pages/assistant`、前端路由及其测试。
- 普通 REST 继续经过 `apiRequest<T>()`；POST SSE 复用公共 CSRF 和 401 处理能力，但独立解析流。
- 当前 Controller/DTO/夹具以 `clearedCount` 和 `payload.watermark` 为准；不兼容设计文档中的旧示例字段。
- B 的卡片协议和固定夹具已随 PR #98 合入 `dev`；当前 C 代码和测试直接导入正式夹具，不依赖远端分支路径或临时 worktree。正式推荐夹具未提供价格、开场时间、`expired`、`purchaseEligible`、`missingFactors` 等字段，C 不补造这些内容。
- B、C 仍需在真实 Cookie 和发布代理下联调；认证操作续期合入后也需补充真实 Cookie 复验。
- 验收为定向测试、`pnpm check`、生产构建浏览器测试、OpenSpec 严格校验和 Git 范围检查通过；不提交、不推送、不归档。
