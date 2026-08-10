# Design: frontend-admin-agent-runs-integration

## 基线和依赖

- 前端分支已快进到 `origin/dev@3ba1d084041994fd0c210978127cd9935b9868e5`。
- B 的 PR #170 head 为 `8e72199c496d364ec2c078eeb8c2242762cbbaad`，已由 `3ba1d084` 合入 `dev`。
- PR #170 提供 `GET /api/v1/admin/agent-runs`、`GET /api/v1/admin/agent-runs/{runId}`、ADMIN/USER 权限测试和两个固定 JSON 夹具。
- 已归档 `frontend-admin-workbench` 只作为历史依据，不修改其任务记录。

## DTO 和运行时校验

`modules/admin-agent/types.ts` 直接表达 PR #170 的可空性。列表和详情共用 `AdminAgentRunSummary`，其中补齐 `sessionId`。节点固定保留 `nodeId/nodeType/status/attemptCount`，其余按 B DTO 接受 `null`。

`mappers.ts` 作为接口边界：

- 必填字符串必须非空；业务 ID 不转为数字。
- 可空字符串和整数只接受正确类型或 `null`，不把错误类型静默变成 `null`。
- 时间字符串校验 ISO 8601，但不在 DTO 层改写时区。
- 返回新对象并只拣选白名单字段，因此原始 payload、工具参数和其他敏感字段不会进入页面状态。

`contract.test.ts` 直接导入仓库内 PR #170 的两个固定夹具，不复制或修改 B 的文件。提供方后续修改夹具时，前端消费者测试会在同一次检查中验证实际数据。

## 状态和展示

状态筛选固定支持五个已知运行状态；返回 DTO 的 `status` 保持开放字符串以兼容新增状态。

- `WAITING_LOCATION`：显示“等待定位”，使用等待样式。
- `RUNNING/COMPLETED/FAILED/CANCELLED`：使用现有中文和颜色。
- 未知状态：显示原始状态；为空已在 mapper 层拒绝。
- 等待定位且无计划：显示“等待定位后生成计划”。
- 其他无计划记录：显示“尚未生成计划”。

所有可空字段通过集中格式化函数显示中文占位，避免 `undefined/null/NaN`。

## 查询、竞态和恢复

列表继续使用公共 `apiRequest<T>()`。公共客户端已跳过 `null/undefined` 查询值；模块在调用前再清理空白字符串，确保不会生成空筛选。

列表 Hook 在内存中保留当前成功数据用于刷新失败恢复，查询条件变化、翻页和手动刷新时取消旧请求并使用序号丢弃迟到响应。页面在刷新请求进行中使用公共 `PageLoading` 骨架屏覆盖旧列表；首次失败使用公共 `PageError`；已有数据刷新失败使用 `PageRefreshErrorNotice` 并恢复旧列表。

详情 Hook 在 `runId` 为空、变化或重新加载时先清空 `data/error`，然后发请求。Drawer 关闭通过 `runId=null` 立即清理；请求序号和 AbortController 共同防止上一条详情回填。

## 页面状态

页面复用 `PageLoading`、`PageEmpty`、`PageError`、`PageForbidden`、`PageNotFound` 和 `PageRefreshErrorNotice`：

- 列表首次加载、成功空态、筛选空态、403、一般失败分别显示独立状态。
- 已有列表刷新期间只显示骨架屏，不显示刷新提示条或旧列表；刷新失败后恢复旧列表和失败提示。
- 401 由公共客户端和 `RequireAdmin` 处理，页面测试验证不会渲染业务数据。
- 详情 404 使用固定文案“运行记录不存在或已失效”；403 使用无权限状态；5xx 显示 traceId 和重新加载。
- 分页仍写入 URL；筛选变更回到第一页。
- 表格继续横向滚动，筛选区域在 1024px 和 768px 以下变为两列、单列；Drawer 使用响应式宽度，避免窄屏固定 820px。

## 测试方案

- `api.test.ts`：六个请求参数、空值清理、ISO 时间、字符串 runId 编码、401/403/404/5xx 与 traceId。
- `contract.test.ts`：两个 PR #170 固定夹具、可空字段、字符串 ID、白名单和敏感字段丢弃。
- mapper/query/hook 测试：`WAITING_LOCATION`、未知状态、可空类型、旧响应丢弃、关闭与重新加载。
- 页面测试：首次加载、空列表、筛选空态、刷新骨架屏、刷新失败恢复、翻页、401、403、详情 404、5xx、traceId、窄屏、未知状态和敏感内容。
- 完整执行 `pnpm check`、`git diff --check`、`openspec validate frontend-admin-agent-runs-integration --strict`。

## 真实联调与回退

PR #170 已合入本分支基线。临时 worktree 的后端自动测试只说明代码可运行，不能代替部署环境真实 HTTP 验收。真实验收仍需要 ADMIN、USER、匿名身份和不存在 runId；页面或接口出现问题时回退本前端分支，不修改 B 的实现。
