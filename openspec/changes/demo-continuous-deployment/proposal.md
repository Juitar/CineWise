## Why

当前仓库只有前后端 CI，代码进入 `dev` 后仍需要 A 手工登录演示服务器构建和重启，容易部署错提交、遗漏健康检查或在失败后无法快速恢复。

## What Changes

- 增加 `dev` 到演示服务器的自动部署工作流。
- 部署前重新执行后端 MySQL、Redis 集成质量门，只部署触发工作流的精确 Git SHA。
- 使用 GitHub `demo` Environment secrets 建立 SSH 连接，不向仓库写入服务器或业务凭据。
- 使用 Docker Compose 构建并等待健康检查，失败时恢复部署前提交。
- 前端生产镜像验证继续由独立前端 CI 负责，避免 Nginx 把缺失脚本伪装成 HTML 200。
- 演示应用通过服务器 `.env` 连接独立基础服务 ECS 上的 MySQL、Redis 和可选 MinIO，不在应用 Compose 中重复启动基础服务。
- 保持一份公共 `compose.yaml`，显式传递后端实际使用的认证、票务时限和内容同步运行变量；以本地与服务器两份无密钥环境模板消除配置漂移。
- 文档、OpenSpec、迁移记录和仅 Flyway SQL 的变更不触发应用部署。
- CD 不执行 Flyway、种子初始化、数据库修数或共享数据库权限变更。

## Capabilities

### New Capabilities

- `demo-continuous-deployment`: `dev` 应用变更的质量门、精确版本部署、健康检查和失败恢复。

### Modified Capabilities

无。

## Impact

- `.github/workflows/`：新增演示环境 CD；复用现有 CI 已覆盖 `dev` push 的集成基线。
- `frontend/nginx.conf` 与前端 E2E：明确 API、静态资源和 SPA 路由边界，并增加生产镜像浏览器冒烟。
- `docs/`：新增服务器准备、Secrets、触发边界和恢复说明。
- `compose.yaml` 与环境模板：Redis、MinIO、认证和运行开关显式传递给后端容器；新增本地和服务器无密钥环境模板。
- 演示服务器：需要预装 Git、Docker、Compose，准备只读仓库访问和被 Git 忽略的 `.env`；不再运行重复的 Redis 容器。
- 基础服务 ECS：继续运行 MySQL、Redis 和可选 MinIO，并仅对白名单应用服务器开放对应端口。
- 数据库：无自动结构或数据变更；Flyway 保持关闭。
