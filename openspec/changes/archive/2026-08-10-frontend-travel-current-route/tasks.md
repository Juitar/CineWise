## 1. 契约和计划

- [x] 1.1 C 复核 PR #191、仓库前端规范和 D 位置规则，确认首版只接入当前位置、`DRIVING/WALKING` 和无坐标路线摘要。（验证：PR 文件与最新 dev 代码检查）
- [x] 1.2 C 创建 proposal、spec、design 和 tasks，记录旧设计中的手动地点/地图不属于本次范围。（验证：`openspec validate frontend-travel-current-route --strict`）

## 2. 前端模块

- [x] 2.1 C 增加路线 DTO、运行时响应校验和正式 API。（验证：travel 契约与 API Vitest）
- [x] 2.2 C 实现一次性浏览器定位和路线 Hook，保证坐标只在局部变量中使用，重复点击和自动重发被禁止。（验证：Hook Vitest）

## 3. 页面

- [x] 3.1 C 在出行详情页增加共享说明、驾车/步行选择、确认和规划按钮。（验证：页面 Vitest）
- [x] 3.2 C 展示路线摘要并覆盖不可用、任务失效、建议过期和任务无权状态。（验证：页面 Vitest）
- [x] 3.3 C 补齐桌面与移动样式，保证触控目标不小于 44px。（验证：CSS 与 Playwright）

## 4. 验证

- [x] 4.1 C 补 API、契约、Hook、页面和 Playwright 测试，覆盖成功、拒绝、失败、重复点击、不重发和坐标隐私。（验证：定向 Vitest 与 `e2e/travel-advice.spec.ts`）
- [x] 4.2 C 执行 `pnpm check`、OpenSpec strict、`git diff --check` 和最终状态检查并记录结果。

## 验证记录

- `pnpm check`：109 个测试文件、628 个测试通过；格式、lint、类型检查、生产构建和 Nginx 隐私检查通过。
- `pnpm test -- src/modules/travel/contract.test.ts src/modules/travel/api.test.ts src/modules/travel/useTravelRoute.test.tsx src/pages/travel/index.test.tsx`：4 个测试文件、36 个测试通过。
- `pnpm e2e -- e2e/travel-advice.spec.ts`：桌面和移动共 6 个用例通过；Windows 下 Umi 子进程未自动退出，外层命令在 240 秒终止，但用例无失败。
- `openspec validate frontend-travel-current-route --strict`：通过。
- `openspec validate travel-frontend-real-advice --strict`：通过。
- `git diff --check`：通过。
- 未验证：PR #191 尚未合入本地 dev，未使用真实 D 服务和真实已支付订单联调。
