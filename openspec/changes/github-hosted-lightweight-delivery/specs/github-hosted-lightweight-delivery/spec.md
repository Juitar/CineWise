## Purpose

规定公开 CineWise 仓库的 GitHub Actions 校验与演示部署顺序，使 PR 获得快速反馈，同时把耗时测试和部署限制在受信任的 `dev` 分支。

## ADDED Requirements

### Requirement: PR 快速检查使用 GitHub 官方 Runner
对于 `pull_request` 事件，系统 SHALL 在 GitHub 官方 `ubuntu-latest` Runner 上执行后端编译、单元测试、代码规范与静态检查，以及前端格式、Lint、类型检查、单元测试和生产构建。

#### Scenario: 外部贡献者创建 PR
- **WHEN** 公开仓库收到一个 PR
- **THEN** PR 工作流在 `ubuntu-latest` 上运行，且不读取部署 Secrets、不使用 `pull_request_target`、不使用自建 Runner

#### Scenario: PR 只改后端代码
- **WHEN** PR 仅包含后端或后端工作流相关文件
- **THEN** 后端 `verify` 运行，前端快速检查按路径识别结果跳过

### Requirement: dev 分支按顺序执行重测试和部署
当受监控的应用文件进入 `dev` 时，系统 SHALL 先执行 MySQL 集成测试，再执行 Redis 集成测试，再执行三项前端浏览器测试；三项均成功后才允许部署 job 执行。

#### Scenario: MySQL 集成测试失败
- **WHEN** `dev` 触发的 MySQL 集成测试失败
- **THEN** Redis、前端浏览器测试和部署均不得执行

#### Scenario: 所有重测试成功
- **WHEN** MySQL、Redis 和前端浏览器测试依次成功
- **THEN** 部署 job 使用 `demo` Environment 的 Secrets 部署触发该工作流的精确 commit SHA

### Requirement: 独立重测试工作流仅供手动诊断
系统 SHALL 仅在手动触发时运行独立的 MySQL 或 Redis 集成测试工作流，避免 PR 和 `dev` 推送重复运行相同重测试。

#### Scenario: PR 修改后端代码
- **WHEN** PR 包含后端代码
- **THEN** 独立 MySQL 和 Redis 工作流不自动启动

### Requirement: 部署失败必须恢复上一版本
部署 job SHALL 在 Compose 构建或健康检查失败后，重新部署部署前的 commit，并将本次工作流标记为失败。

#### Scenario: 新版本健康检查失败
- **WHEN** 新版本的 `docker compose up --wait` 失败
- **THEN** 系统恢复部署前 commit 并以失败状态结束工作流
