# 任务

## 1. 契约

- [x] 1.1 C 以 D 已合入的 Controller 和固定夹具确认任务、建议、提醒更新及错误码；验证：契约测试直接读取 `backend/src/test/resources/fixtures/travel/c`。

## 2. 前端模块与页面

- [x] 2.1 C 新增 `modules/travel` 的 DTO 校验、API 和 Hook；验证：任务、建议、刷新和提醒更新全部经过公共请求层。
- [x] 2.2 C 新增 `/travel/:taskId` 页面；验证：展示真实天气、建议、提醒、来源和有效期，不显示静态路线、距离或餐饮。
- [x] 2.3 C 从订单详情按服务端任务查询进入页面，并移除首页静态演示入口；验证：任务不存在、无权限和未建立任务均不猜测或创建任务。

## 3. 写入与恢复

- [x] 3.1 C 实现提醒时间更新、重复提交保护和 `If-Match` 版本校验；验证：成功后使用响应更新 `triggerAt` 和 `version`。
- [x] 3.2 C 实现刷新建议与写入结果未知恢复；验证：超时或断网不重发，只按原 `taskId` 查询恢复。

## 4. 验证

- [x] 4.1 C 补齐模块、页面、订单入口和响应式测试；验证：加载、空数据、失效、降级、404、409、429、503、重复点击和移动端均覆盖。
- [x] 4.2 C 执行 `pnpm check`、相关 Playwright、OpenSpec 严格校验和 `git diff --check`；验证：记录通过、失败、跳过和未验证项。

## 验证记录

- `pnpm check`：通过；104 个测试文件、535 个测试通过，格式、lint、类型检查和生产构建通过。
- `pnpm exec playwright test e2e/travel-advice.spec.ts`：桌面端和移动端共 4 个用例全部通过；Windows 下 Umi 子进程未自动退出，外层命令在 240 秒后被终止，不影响已完成的用例结果。
- `openspec validate travel-frontend-real-advice --strict`：通过。
- `git diff --check`：通过。
- 跳过：后端 Maven 测试，本次没有修改后端代码。
- 未验证：真实 D 服务联调，需要部署或本地启动 D 服务后使用真实订单验证。
