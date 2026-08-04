## 1. 契约与安全边界

- [x] 1.1 A 确认普通功能仍通过个人分支 PR 合入 `dev`，仅不改变运行行为的小型维护和已审核隔离迁移适用直提规则。
- [x] 1.2 A 确认 CD 自动部署演示服务器，但不执行 Flyway、种子或数据库修数。
- [x] 1.3 组长确认应用 ECS 复用基础服务 ECS 的 MySQL、Redis 和可选 MinIO，应用 Compose 不重复运行 Redis。

## 2. CI/CD 实现

- [x] 2.1 确认现有前后端 CI 已覆盖 `dev` push，作为 CD 的集成基线。
- [x] 2.2 新增应用路径过滤、前后端质量门和 GitHub `demo` Environment SSH 配置。
- [x] 2.3 部署精确 Git SHA，拒绝覆盖服务器本地修改，并等待 Compose 健康检查。
- [x] 2.4 部署失败时恢复上一 Git SHA，保持失败结果供 A 处理。
- [x] 2.5 拒绝 `FLYWAY_ENABLED=true` 或 `SEED_ENABLED=true` 的自动部署。
- [x] 2.6 移除应用 Compose 的 Redis 服务，显式注入共享 Redis 和可选 MinIO 环境变量。
- [x] 2.7 后端 Dockerfile 使用服务器 BuildKit Maven cache mount，部署构建输出 plain progress；GitHub Runner 的 Maven 缓存继续只服务质量门，新提交取消同组旧工作流。
- [ ] 2.8 A 修正生产 Nginx 的 API 优先级、静态资源 404 和 SPA 回退，并把真实生产镜像 Playwright 冒烟加入前端质量门。

## 3. 文档与验证

- [x] 3.1 记录触发边界、Secrets、服务器准备、发布和恢复步骤。
- [x] 3.2 工作流 YAML 解析、`docker compose config --quiet` 和 OpenSpec 严格校验均通过。
- [ ] 3.3 A 配置真实 `demo` Environment，并在服务器完成首次 `workflow_dispatch` 部署和业务冒烟。
- [x] 3.4 同步 README、本地 Docker 指南、部署指南和环境模板，并验证缺失共享 Redis 配置时 Compose 明确失败。
- [ ] 3.5 A 验证 `/movies`、缺失 JS/CSS、`/api/**`、入口资源 MIME 与浏览器启动；C 后续确认 Umi hash 产物后再启用长期缓存。
