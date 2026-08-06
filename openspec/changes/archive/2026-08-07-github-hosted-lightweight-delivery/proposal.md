## Why

仓库已公开，日常 CI 不再受私有仓库 GitHub Actions 分钟额度限制。继续让公开 PR 使用 ESC 自建 Runner，既比 GitHub 官方 Runner 慢，也会让不受信任的 PR 代码接触团队服务器。

## What Changes

- PR 的后端和前端快速检查固定在 GitHub 官方 `ubuntu-latest` 上运行，且不运行数据库、Redis、浏览器 E2E 或部署。
- 应用代码进入 `dev` 后，在 GitHub 官方 Runner 上依次运行 MySQL、Redis、前端浏览器测试；全部通过后才执行现有 SSH 部署、Compose 健康检查和失败回退。
- MySQL 与 Redis 独立工作流仅保留手动诊断入口，避免同一重测试被重复触发。
- 部署指南改为 GitHub 官方 Runner 方案，删除 ESC、自建 Runner 标签和自救提交说明。
- 后端工作流统一升级到 `actions/setup-java@v5`。

## Capabilities

### New Capabilities

- github-hosted-lightweight-delivery

### Modified Capabilities

- 无。

## Impact

- 影响 `.github/workflows/` 下 5 个工作流和 `docs/demo-deployment-guide.md`。
- 不修改业务 API、数据库迁移、应用代码或演示服务器上的 Docker Compose 配置。
- 部署仍只从 `dev` 读取 GitHub `demo` Environment 的部署 Secrets。
