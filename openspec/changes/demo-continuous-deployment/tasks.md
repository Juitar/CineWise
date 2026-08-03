## 1. 契约与安全边界

- [x] 1.1 A 确认普通功能仍通过个人分支 PR 合入 `dev`，仅不改变运行行为的小型维护和已审核隔离迁移适用直提规则。
- [x] 1.2 A 确认 CD 自动部署演示服务器，但不执行 Flyway、种子或数据库修数。

## 2. CI/CD 实现

- [x] 2.1 确认现有前后端 CI 已覆盖 `dev` push，作为 CD 的集成基线。
- [x] 2.2 新增应用路径过滤、前后端质量门和 GitHub `demo` Environment SSH 配置。
- [x] 2.3 部署精确 Git SHA，拒绝覆盖服务器本地修改，并等待 Compose 健康检查。
- [x] 2.4 部署失败时恢复上一 Git SHA，保持失败结果供 A 处理。
- [x] 2.5 拒绝 `FLYWAY_ENABLED=true` 或 `SEED_ENABLED=true` 的自动部署。

## 3. 文档与验证

- [x] 3.1 记录触发边界、Secrets、服务器准备、发布和恢复步骤。
- [x] 3.2 工作流 YAML 解析、`docker compose config --quiet` 和 OpenSpec 严格校验均通过。
- [ ] 3.3 A 配置真实 `demo` Environment，并在服务器完成首次 `workflow_dispatch` 部署和业务冒烟。
