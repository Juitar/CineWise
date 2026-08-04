## Why

前端 PR 验证和 Demo Deploy 都会执行 `playwright install --with-deps chromium`。GitHub 托管 Runner 每次从空环境启动，虽然 pnpm 依赖已有缓存，但 Chromium 浏览器二进制仍可能重复从 CDN 下载，增加完整前端验证和部署前质量门耗时。

## What Changes

- 在 `Frontend Verify` 的完整前端验证中恢复和保存 Linux Playwright Chromium 浏览器缓存。
- 在 `Demo Deploy` 的前端验证中使用相同缓存路径和缓存键，使 `dev` 上的重复运行能够复用浏览器二进制。
- 保留现有 `playwright install --with-deps chromium`，继续校验浏览器和系统依赖；缓存未命中时按原流程下载。
- 使用 Runner 操作系统和前端锁文件哈希隔离缓存，依赖版本变化后自动创建新缓存。

## Capabilities

### New Capabilities

- `ci-playwright-cache`: 在不减少浏览器测试的前提下复用 Playwright Chromium 二进制。

### Modified Capabilities

无。

## Impact

- `.github/workflows/frontend-verify.yml`：完整前端验证增加官方 `actions/cache` 浏览器缓存步骤。
- `.github/workflows/demo-deploy.yml`：部署前前端验证增加相同浏览器缓存步骤。
- 不修改前端测试、生产构建、后端、数据库、API、权限、部署目标或服务器构建方式。
