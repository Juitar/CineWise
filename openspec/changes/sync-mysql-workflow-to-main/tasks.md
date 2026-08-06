## 1. 工作流同步

- [x] 1.1 将 `backend-mysql-integration.yml` 从 `dev` 当前版本同步到 `main`。
- [x] 1.2 保持 `workflow_dispatch`、一次性 MySQL 8.4 服务和 CI 专用凭据，不加入 PR 自动触发或部署步骤。

## 2. 验证

- [ ] 2.1 在合并后从 Actions 选择 PR #86 当前目标 ref `71e1fc4`（或其保留分支）运行 MySQL 8.4 集成，并记录运行编号。
- [x] 2.2 运行 OpenSpec 严格校验和 `git diff --check`。
