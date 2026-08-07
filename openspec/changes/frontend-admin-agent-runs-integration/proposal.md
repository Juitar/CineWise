# Proposal: frontend-admin-agent-runs-integration

## 背景

开工基线为 `origin/dev@ec9dbaa280e6a8128dda8b39ddb6f4cc18daed3a`。开发期间 B 的 PR #170 head `8e72199c496d364ec2c078eeb8c2242762cbbaad` 合入 `dev`，本分支随后快进到合并提交 `3ba1d084041994fd0c210978127cd9935b9868e5`。管理端 Agent 轨迹页面已经有列表和详情骨架，但类型和页面状态仍按旧接口假设实现；实际接口会返回 `WAITING_LOCATION`，并允许计划和节点审计字段为 `null`。

## 目标

- 让 C 的 `admin-agent` DTO、请求参数和运行时校验严格消费 PR #170 的列表、详情和两个固定 JSON 夹具。
- 支持 `WAITING_LOCATION`、四个既有状态和未知状态，计划尚未生成时使用明确中文说明。
- 补齐列表、详情、权限、错误、刷新、分页、筛选空态、竞态、窄屏和可空字段页面状态。
- 继续复用公共 `apiRequest<T>()`、公共错误模型和公共页面状态组件。
- 保证页面只展示 B 返回的脱敏白名单字段。

## 非目标

- 不修改 B 的 Controller、Service、Repository、Mapper、后端 DTO、权限规则或夹具。
- 不把 PR #170 的后端提交复制到本分支。
- 不修改或重新标记已归档的 `frontend-admin-workbench`。
- 不用 Mock 或组件测试代替真实 HTTP、ADMIN/USER 权限和部署环境验收。

## Owner 与协作

C 负责前端 DTO、模块 API、查询 Hook、管理端轨迹页面和消费者测试。B 负责 PR #170 合并、接口部署和后端权限正确性。真实联调由 B 提供可运行接口后，C 使用 ADMIN、USER 和匿名身份完成验收。

## 验收结果

- PR #170 两个固定夹具可以直接通过前端运行时映射，业务 ID 始终保持字符串。
- `WAITING_LOCATION` 且 `planId/planVersion=null` 时页面显示“等待定位后生成计划”，不出现 `undefined`、`null`、`NaN`，也不显示为失败。
- 未知运行或节点状态安全显示原始状态，不导致页面崩溃。
- 列表和详情覆盖首次加载、空数据、失败、401、403、详情 404、5xx/traceId、筛选空态、翻页、刷新、重复详情请求和窄屏。
- 自动测试、`pnpm check`、OpenSpec 严格校验和 Git 检查通过。
- PR #170 已合入后仍需完成真实 HTTP、权限和部署验收；自动测试不能代替这些验收。
