## 1. OpenSpec 与范围

- [x] 1.1 C 根据最新 `origin/dev`、认证实现和前端设计确认最小版本只使用 `CurrentUser` 与退出接口。（验证：proposal、spec 和 design 范围核对）
- [x] 1.2 C 完成 OpenSpec 严格校验。（验证：`openspec validate frontend-profile-minimum --strict`）

## 2. 个人中心实现

- [x] 2.1 C 新增共享退出 Hook，并让桌面顶部菜单和个人中心复用提交状态、退出调用和登录页跳转。（验证：组件测试）
- [x] 2.2 C 重写 `/profile`，展示当前用户真实账号摘要和隐私说明入口。（验证：个人中心组件测试）
- [x] 2.3 C 删除写死的订单、观影记录、画像和无实际动作的设置内容，完成 PC/移动响应式样式。（验证：组件断言和两种视口浏览器测试）

## 3. 测试与交付

- [x] 3.1 C 补充个人中心组件测试，覆盖资料展示、退出防重复和退出失败后的页面处理。（验证：定向 Vitest）
- [x] 3.2 C 更新认证浏览器测试，让桌面和移动端都从个人中心完成退出。（验证：`pnpm e2e -- e2e/auth.spec.ts`）
- [x] 3.3 C 执行 `pnpm check`、OpenSpec 严格校验和 Git 差异检查。（验证：记录实际命令结果）

## 验证记录

- `pnpm check`：通过；Vitest 11 个文件、53 个测试全部通过，Lint、类型检查和生产构建通过。
- `pnpm e2e -- e2e/auth.spec.ts`：桌面和移动共 6 个用例全部通过；Windows 下 Umi 开发服务在用例结束后未自行退出，外层命令超时终止。
- `mvn -Djava.io.tmpdir=D:\code\CineWise\.codex-tmp verify`：通过；224 个测试，0 失败，0 错误，14 跳过。
- `openspec validate frontend-profile-minimum --strict`：通过。
- `git diff --check`：通过。
