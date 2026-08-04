# CineWise 演示环境持续部署指南

本文说明 `dev` 分支到演示服务器的自动部署。它不改变团队的 Git 协作方式：普通功能仍从个人分支通过 PR 合入 `dev`；只有不改变运行行为的小型文档或协作规则维护，以及 A 已审核的隔离 Flyway 迁移及直接关联证据，才可以按 `AGENTS.md` 直接提交到 `dev`。

## 1. 触发边界

`.github/workflows/demo-deploy.yml` 在以下内容进入 `dev` 时触发：

- 后端代码或构建文件；
- 前端代码或构建文件；
- `compose.yaml`、`.env.example` 或 CD 工作流自身。

仅修改以下内容不会自动部署应用：

- `docs/**`、`openspec/**` 和普通 Markdown；
- `backend/src/main/resources/db/migration/**` 中的 Flyway SQL；
- `docs/database-migrations/**` 中的迁移证据。

Flyway SQL 进入 `dev` 不代表数据库已经执行。数据库迁移仍由 A 按 `docs/DATABASE_MIGRATION_REVIEW.md` 独立审核、验证和受控发布，CD 始终要求 `FLYWAY_ENABLED=false`，也拒绝 `SEED_ENABLED=true`。

## 2. 部署流程

```text
代码进入 dev
  → 后端 mvnw verify
  → 前端 pnpm check、开发服务器 E2E 与生产 Nginx 镜像冒烟
  → 使用 GitHub demo Environment 连接服务器
  → 获取本次 workflow 对应的精确 commit SHA
  → Docker Compose 构建并等待全部健康检查
  → 成功：保留新版本
  → 失败：重新构建并恢复上一个已部署 commit，工作流保持失败
```

CD 不使用服务器目录中“当前最新”的不确定代码，而是部署触发工作流的精确 Git SHA。部署组禁止并发执行，后到的部署会等待前一轮结束。

## 3. GitHub Environment 与 Secrets

在仓库 Settings → Environments 创建 `demo`，按需要启用审批规则，并配置：

| Secret | 必填 | 说明 |
| --- | :---: | --- |
| `DEPLOY_HOST` | 是 | 演示服务器主机名或 IP |
| `DEPLOY_PORT` | 否 | SSH 端口；为空时使用 22 |
| `DEPLOY_USER` | 是 | 仅用于 CineWise 部署的最小权限系统账号 |
| `DEPLOY_SSH_KEY` | 是 | 对应部署账号的私钥；不得复用个人或 root 私钥 |
| `DEPLOY_KNOWN_HOSTS` | 是 | 已人工核对指纹的服务器 known_hosts 记录 |
| `DEPLOY_PATH` | 是 | 服务器上的 CineWise 仓库绝对路径 |

生成 `DEPLOY_KNOWN_HOSTS` 时先由 A 通过可信渠道核对服务器指纹，再保存对应记录；不要在工作流中临时 `ssh-keyscan` 并无条件信任结果。

## 4. 服务器前置条件

服务器必须满足：

1. Linux、Git、Docker Engine 和支持 `--wait` 的 Docker Compose v2 已安装。
2. `DEPLOY_PATH` 已克隆 CineWise 仓库，`origin` 允许部署账号只读获取 `dev`。
3. SSH 部署账号可以在不使用 root 的情况下运行该项目的 Docker Compose。
4. 仓库根目录存在被 Git 忽略的 `.env`，真实凭据只保存在服务器。
5. 应用服务器能通过私网或固定公网 `/32` 白名单访问基础服务 ECS 的 MySQL、Redis 和可选 MinIO API；应用 Compose 不运行重复基础服务。
6. `.env` 使用演示环境配置，且至少满足：

```dotenv
SPRING_PROFILES_ACTIVE=demo
MYSQL_HOST=基础服务ECS地址
REDIS_HOST=基础服务ECS地址
REDIS_PORT=6379
REDIS_PASSWORD=共享Redis密码
FLYWAY_ENABLED=false
SEED_ENABLED=false
```

启用对象存储时再填写 `MINIO_ENDPOINT`、最小权限 `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 和 `MINIO_BUCKET`。`MINIO_ENDPOINT` 必须是 API 地址而不是 9001 Console 地址；不得使用 MinIO root 管理员凭据。

首次准备服务器时，由 A 手工执行并确认：

```bash
cd /absolute/path/to/CineWise
git status --short
docker compose version
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 240
docker compose ps
```

成功后再启用 GitHub `demo` Environment 自动部署。

## 5. 发布与恢复规则

- 部署目录存在已跟踪文件修改时，CD 拒绝覆盖；先由 A 查明来源。
- 应用部署不自动执行 Flyway、种子、数据库修数、备份或权限变更。
- 应用 Compose 不负责共享 MySQL、Redis 或 MinIO 的启动、停止与恢复；基础服务故障应在基础服务 ECS 单独处理。
- 新版本构建或健康检查失败时，CD 切回部署前 Git SHA 并重新构建旧版本。
- 自动恢复成功不代表新版本成功；GitHub 工作流仍以失败结束，必须修复后重新部署。
- 数据库已经发生不兼容变化时，应用回滚可能不足，必须使用对应迁移发布方案和备份恢复流程。
- 需要重跑同一提交时，从 GitHub Actions 对 `Demo Deploy` 使用 `Run workflow`，并确保选择 `dev`。

## 6. 上线后检查

部署成功后至少检查：

```bash
cd /absolute/path/to/CineWise
docker compose ps
docker compose logs --tail 100 backend
docker compose logs --tail 100 frontend
```

浏览器再完成首页、登录、场次查询和本次变更涉及的关键业务冒烟。自动健康检查只证明容器可用，不能替代业务验收。

生产 Nginx 冒烟会在质量门中自动验证：`/movies` 可回退到不缓存的 SPA 入口，缺失 JS/CSS 返回 404，`/api/**` 到达后端，以及入口引用资源的状态和 MIME 正确。静态资源长期缓存必须等 C 开启并确认 Umi `hash: true` 产物后再配置；非哈希资源不得设置 `immutable` 长缓存。
