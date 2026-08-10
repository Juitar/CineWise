## Context

第一阶段已通过临时纯文档 PR #35 验证：前后端必需检查正常创建并成功，重型步骤全部跳过。完整前端验证仍需运行 Playwright，其中浏览器二进制位于 Linux 默认目录 `~/.cache/ms-playwright`，适合在 GitHub 托管 Runner 间复用。

当前 Maven Actions 缓存、pnpm 缓存、workflow concurrency 和 timeout 已存在；后端 Dockerfile 也已使用 BuildKit Maven cache mount。部署镜像由演示服务器通过 SSH 执行 `docker compose build`，GitHub Runner 的 Buildx GHA cache 无法直接供该服务器使用，因此不纳入本次变更。

## Decisions

### 1. 使用官方 actions/cache 保存 Playwright 默认目录

两个前端验证 job 都在依赖安装后、浏览器安装前执行 `actions/cache@v4`，缓存 `~/.cache/ms-playwright`。缓存键为：

```text
${runner.os}-playwright-chromium-${hashFiles('frontend/pnpm-lock.yaml')}
```

操作系统隔离不同浏览器产物，锁文件哈希保证 Playwright 版本或依赖图变化后不会错误复用旧缓存。本次不使用宽泛 restore key，避免跨锁文件恢复不匹配的浏览器版本。

### 2. 保留安装命令作为完整性与系统依赖检查

缓存步骤不替代 `pnpm exec playwright install --with-deps chromium`。缓存命中时 Playwright 复用已存在的浏览器；缓存未命中或内容不足时仍由原命令下载。Linux 系统依赖继续由 Playwright 安装流程处理，不缓存系统包目录。

### 3. 不改变质量门与部署架构

现有 Format、Lint、TypeScript、单元测试、生产构建、E2E 和生产 Nginx 冒烟全部保留。Demo Deploy 仍在 GitHub 验证后通过 SSH 连接演示服务器构建并部署，不引入镜像仓库或 GitHub 侧 Buildx 构建。

## Risks / Trade-offs

- 浏览器缓存通常为数百 MB，首次运行仍需下载并上传；收益从后续相同锁文件的运行开始体现。
- 任意锁文件变化都会产生新缓存，命中策略偏保守，但能避免 Playwright 版本错配。
- `install --with-deps` 仍可能花费系统包检查时间，本次只消除可复用的浏览器二进制下载。

## Validation

1. 使用 Prettier 和 actionlint 校验两份 workflow。
2. 严格校验 OpenSpec，并检查 Git diff 和敏感内容。
3. PR 首次完整前端验证应显示 cache miss、安装成功且全部测试通过。
4. 在相同分支重新运行或追加不改变锁文件的提交，确认 cache hit 且全部测试仍通过。
