## Context

生产浏览器测试的全局初始化仅负责启动 `dist/` 静态文件服务，不会创建生产产物。GitHub Actions 的每个 job 使用独立环境，PR 快检查生成的 `dist/` 不会出现在 `dev` 的 CD job 中。

## Goals / Non-Goals

**Goals:**

- 只补生产浏览器测试必需的构建步骤。
- 保持 MySQL、Redis、浏览器测试和部署的既有串行顺序。

**Non-Goals:**

- 不恢复前端完整 `check`。
- 不通过缓存或跨工作流产物传递复用 PR 的构建目录。

## Decisions

在 `frontend-e2e` job 的依赖安装后执行 `pnpm build`。该命令只生成 E2E 所需的 `dist/`，不会重新执行格式、Lint、类型检查或单元测试。

不使用 PR 产物：PR 与合入 `dev` 的 commit 不同，且跨工作流传递会增加权限和产物过期处理。

## Risks / Trade-offs

- [CD 多一次前端构建] → 这是独立 Runner 中运行生产浏览器测试的必要准备，且不重复完整前端检查。
- [构建失败阻止部署] → 符合生产浏览器测试无法验证不存在产物的预期。
