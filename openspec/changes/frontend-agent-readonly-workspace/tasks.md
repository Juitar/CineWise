## 1. 规划和当前接口确认

- [x] 1.1 Owner：C；从最新 `origin/dev` 核对 Controller、DTO、认证公共层和全部 Agent C 夹具，记录 `clearedCount`、`payload.watermark` 与旧文档示例的差异；验证：OpenSpec 不包含双字段猜测。
- [x] 1.2 Owner：C；更新 proposal、spec、design、tasks，将本次范围收紧到 C 可独立实现的只读工作区；验证：`openspec validate frontend-agent-readonly-workspace --strict`。

## 2. Agent 合同和流客户端

- [x] 2.1 Owner：C；实现会话、消息、运行和事件 DTO 校验，所有业务 ID、游标和水位线保持字符串；验证：合法、未知字段、非法 ID、超大十进制 ID、空 payload 测试。
- [x] 2.2 Owner：C；实现普通 REST API 和模块 Hook；验证：创建/列表/历史/单个清空/批量清空/运行查询/取消及 401、403、404、409、网络错误测试。
- [x] 2.3 Owner：C；实现 fetch 型 POST SSE、公共 CSRF/401 复用、分块解析、心跳和 AbortController；验证：跨分块、多事件、心跳、非法 JSON、HTTP、取消和卸载测试。
- [x] 2.4 Owner：C；实现事件归属、重复事件、字符串游标、旧计划过滤和安全投影；验证：其他会话/运行、重复事件、旧计划和已展示成功内容保留测试。

## 3. 状态和恢复

- [x] 3.1 Owner：C；实现七种工作区状态和单活动请求限制；验证：正常完成、运行失败、主动取消、重复提交、会话切换和登出测试。
- [x] 3.2 Owner：C；实现首事件前结果未知、已有 runId 查询恢复和 20 秒无事件恢复；验证：不生成新 clientRequestId、不自动重发写操作。
- [x] 3.3 Owner：C；实现 `stream.reset` 整体重建；验证：水位线一致、`43/42` 继续用 `43`、不一致时停止且不发恢复请求。

## 4. 路由和响应式工作区

- [x] 4.1 Owner：C；注册 `/assistant`、`/assistant/:sessionId` 和安全登录回跳，接入首页 Agent 输入；验证：匿名不创建会话，登录后返回原页面。
- [x] 4.2 Owner：C；实现共享 Hook 的 PC 会话侧栏、主对话区和移动会话抽屉；验证：响应式切换不丢 sessionId、runId、消息、占位和游标。
- [x] 4.3 Owner：C；实现文本、追问、进度、完成、安全错误和未知/不完整卡片占位；验证：不渲染危险 HTML，不出现伪造价格、场次、路线和业务按钮。
- [x] 4.4 Owner：C；按已合入 `dev` 的 B PR #98 实现六类 payload、位置授权状态、卡片刷新恢复和确认结果只读边界；验证：直接导入正式 C 夹具，未知类型推进游标、已知缺字段不推进，且不存在确认请求或业务按钮。

## 5. 验证和后续依赖

- [x] 5.1 Owner：C；运行 Agent 前端定向测试、`pnpm check` 和生产构建浏览器测试；验证：记录通过、失败和跳过数。
- [x] 5.2 Owner：C；运行 OpenSpec 严格校验、`git diff --check`、状态和变更范围检查；验证：未修改其他 worktree、A/B/D、后端和数据库。
- [x] 5.3 Owner：B；B 已通过 PR #98 合入 `dev`（合入提交 `019716f`），并提供卡片事件协议和固定夹具；验证：C 已直接导入全部正式夹具并完整重跑定向测试。
- [x] 5.4 Owner：C；已在 `origin/dev@34c69c1` 同步并复验 `MOVIE_CARD`、`PLAN_CARD`、位置问题、未知/缺字段和卡片恢复；验证：不依赖远端 Git 对象、临时 worktree 或未合入文件。
- [ ] 5.5 Owner：B、C；在真实 Cookie 和发布代理下完成 SSE 联调；验证：分块、心跳、CSRF、取消、断流、游标和 reset 均可观察，当前保持未完成。
- [ ] 5.6 Owner：C；认证续期修改合入最新 `dev` 后验证 Agent REST、初次 POST SSE、断线重连和最长登录时间 401；验证：浏览器接收并继续携带新 Cookie，续期不重发消息、确认或工具调用，当前保持未完成。
