## Context

见 `proposal.md`。仓库已公开，GitHub 官方 Runner 可用于日常 CI；ESC 自建 Runner 已停用，不能被公开 PR 使用。现有部署 job 已具备 GitHub `demo` Environment、SSH 主机指纹校验、精确 SHA 部署、Compose 健康检查和失败回退。

## Goals / Non-Goals

**Goals:**

- 按 PR、`dev` 重测试、部署三个阶段分配检查，避免重复执行。
- 所有 CI 和重测试使用 `ubuntu-latest`，部署仍从 GitHub 托管环境经 SSH 到演示服务器。
- 保留重测试和部署的串行依赖，避免失败后继续部署。

**Non-Goals:**

- 不重新启用或注册 ESC Runner。
- 不变更业务测试命令、Docker Compose、部署服务器或 GitHub Environment Secrets。
- 不在 PR 中运行浏览器 E2E、MySQL、Redis 或部署。

## Decisions

### 1. 公开 PR 只使用 GitHub 官方 Runner

所有工作流的 `runs-on` 统一为 `ubuntu-latest`。公开仓库的 PR 可能来自不受信任的代码，不能在团队 ESC 上执行；官方 Runner 会在每次 job 后销毁环境。

备选方案是仅给自建 Runner 加标签限制。该方案不能阻止公开 PR 中的任意代码在服务器上执行，因此不采用。

### 2. 快检查和重测试按触发时机拆分

`backend-verify.yml` 与 `frontend-verify.yml` 继续覆盖 PR 和 `dev` 的快速检查。前端快速检查移除浏览器缓存、Chromium 安装和 E2E；MySQL、Redis 与三项浏览器测试集中在 `demo-deploy.yml` 的 `dev` 推送中，以 `needs` 串行连接。

独立 MySQL、Redis 工作流只保留 `workflow_dispatch`，供排查失败时单独运行。

### 3. 部署不重复快速检查

部署 job 仅依赖前端浏览器测试，不重新执行 Maven `verify` 或前端快速检查。`dev` 受 PR 保护时，快速检查已经在合并前完成；这样能缩短部署前等待时间。

### 4. 部署安全边界保持不变

只有 `push` 到 `dev` 的部署 job 使用 `demo` Environment Secrets。job 使用已保存的 known_hosts、精确 SHA、Flyway/Seed 开关校验、Compose 健康检查和失败回退；PR 不会接触这些信息。

## Risks / Trade-offs

- [直接推送 `dev` 会绕过合并前的 PR 快检查] → A/B 保持 `dev` 分支保护，只允许 PR 合并。
- [重测试耗时增加] → 仅在进入 `dev` 后运行，并按失败即停止的顺序串行执行。
- [GitHub Environment 未配置部署 Secrets] → 部署 job 在变量检查处失败且不会连接服务器；B/A 在合并前配置并限制 `demo` 仅允许 `dev`。

## Migration Plan

1. 合并此 PR 后，新的 PR 自动使用 GitHub 官方 Runner。
2. 下一次受监控的应用代码进入 `dev` 时，按重测试和部署顺序执行。
3. 如需回退，回退本 PR；GitHub Actions 将恢复原有工作流定义。ESC Runner 保持停止和禁用状态。
