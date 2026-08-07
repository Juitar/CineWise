# 设计

## 组件边界
在 `frontend/src/features/agent-workspace/cards/` 提供统一入口 `AgentDisplayItemView`。入口按 `AgentDisplayItem.kind` 选择小组件，工作区只负责列表、网络状态和确认回调。卡片组件不请求 API，不读取 `AgentEvent` 或原始 payload。

`projection.ts` 继续作为安全边界：只把已通过协议校验的标题、正文、字段、只读选项、已校验选座地址和确认状态输出为 `AgentDisplayItem`。所有 ID 保持字符串；不从正文推导价格、时间、可购状态或 URL。

## 类型和展示
- `assistant-text`、`user-text`：消息气泡，只展示纯文本。
- `question`：问题标题、说明和只读选项；当前没有回答接口，选项不可点击提交。
- `movie-card`、`plan-card`：展示标题、候选信息、来源、数据时间、有效期和降级/过期状态。
- `business-intent`：只使用投影已校验的 `selectSeatsPath` 提供选座入口。
- `progress`：展示当前步骤和状态。
- `error`：展示固定安全错误内容。
- `completed`、`card-placeholder` 或未来未知 kind：显示固定安全状态，不读取额外数据。

确认卡是 `plan-card` 的受控变体。组件只调用工作区传入的确认或拒绝回调，现有 Hook 继续负责互斥、接口调用和结果未知恢复。

## 安全和兼容
React 仅以文本节点渲染内容，不使用 `dangerouslySetInnerHTML`。组件不接受原始 payload、任意链接、userId、内部运行主键、幂等键或订单字段。未知类型固定显示“卡片暂不可用”。

桌面和移动端使用相同 DOM 和回调；CSS 媒体查询只调整宽度、栅格和操作区排列。原有 SSE 断线恢复、权限守卫和写操作不自动重试规则不变。

## 测试
- 投影单测：验证问题选项白名单、危险值不进入展示模型、未知类型降级。
- 组件单测：覆盖所有 kind、字段、选项、选座链接、确认按钮状态、危险文本转义和未知占位。
- Playwright：桌面和移动视口验证类型化推荐卡及历史确认卡。
- 运行 `pnpm check`、相关 Playwright、OpenSpec strict 和 `git diff --check`。
