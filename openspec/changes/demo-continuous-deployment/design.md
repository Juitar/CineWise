## Context

CineWise 以 `dev` 为日常集成分支，普通功能通过个人分支 PR 合入；只有不改变运行行为的小型文档或协作规则维护，以及 A 已审核的隔离 Flyway 迁移及直接关联证据，可以按 `AGENTS.md` 直接进入 `dev`。项目已有后端、前端 Dockerfile、根 Compose 和健康检查，但没有固定的自动部署步骤。

## Decisions

### 1. 只部署应用相关变更

CD 使用路径过滤，仅在后端、前端或部署配置变化时触发。仅文档、OpenSpec、迁移证据或 Flyway SQL 的提交不会重建应用。若同一提交同时包含应用代码和 SQL，应用路径仍会触发 CD，但 Flyway 始终关闭，数据库必须先由 A 按发布顺序处理。

### 2. CD 自带质量门

现有前后端 CI 是两个独立工作流，无法通过同一 `needs` 建立可靠部署依赖。因此 CD 内并行重复执行后端 `verify` 和前端 `check/E2E`，只有两者成功才进入部署。重复验证增加时间，但避免部署与独立 CI 竞态。

### 3. 通过 GitHub Environment 注入 SSH 配置

服务器、账号、私钥、known_hosts 和部署目录只存放在 `demo` Environment secrets。工作流不使用 root、不临时接受未知主机指纹，也不接收数据库密码。

### 4. 部署精确提交并保护服务器修改

服务器部署目录必须是干净 Git 工作区。工作流获取 `github.sha`，通过 `git fetch` 后以 detached HEAD 检出该提交，避免在执行期间新的 `dev` push 改变实际部署版本。被 Git 忽略的 `.env` 保持不变。

### 5. 共享基础服务与应用 Compose 边界

组长确认演示环境采用两台 ECS：基础服务 ECS 统一运行 MySQL、Redis 和可选 MinIO，应用 ECS 运行后端与前端。应用 Compose 因此不再创建 Redis 或持有 Redis 数据卷，而是要求 `.env` 显式提供 `REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD`。基础服务端口只对白名单应用 ECS 开放；同 VPC 时优先使用私网地址，否则使用固定公网 `/32` 白名单。

MinIO 仍是可选能力。Compose 将四个 MinIO 变量透传给后端，为后续对象存储客户端接入保留统一入口，但空值不得阻断当前核心应用。CI 中一次性 Docker Redis 只用于隔离集成测试，不属于演示部署，不受此次调整影响。

### 6. Compose 健康检查和应用级回滚

使用 `docker compose up --wait` 等待后端和前端健康检查。后端容器使用整体 `/actuator/health` 验证必需的共享 MySQL、Redis 连接；失败时检出部署前 SHA 并重新构建旧版本，即使恢复成功，工作流仍失败并保留告警。该回滚只覆盖应用版本，不替代基础服务恢复、数据库备份或迁移恢复。

### 7. 禁止 CD 执行数据库变更

部署前检查服务器 `.env`，发现 `FLYWAY_ENABLED` 或 `SEED_ENABLED` 开启即拒绝部署。Flyway SQL 版本分配、空库验证和共享库发布继续由 A 的独立受控流程完成。

## Risks / Trade-offs

- 服务器构建依赖网络和 Docker 缓存：首次部署较慢；通过 Compose 构建缓存降低后续耗时。
- 旧版本回滚仍需重新构建：服务器必须保留依赖下载能力，失败时由 A 手工恢复。
- SSH 和服务器是单点：使用最小权限账号、Environment 审批和严格 known_hosts 降低风险。
- 应用与破坏性迁移不兼容时不能只回滚应用：此类变更必须另行制定联合发布和数据库恢复方案。

## Migration Plan

1. A 在应用服务器准备仓库、Docker Compose、只读 Git 凭据和连接共享基础服务的 `.env`。
2. A 在基础服务 ECS 仅向应用服务器白名单开放 MySQL、Redis 和 MinIO API 端口，并使用应用级账号验证连接。
3. 在 GitHub 创建 `demo` Environment，配置 Secrets 并核对主机指纹。
4. 手工执行一次 Compose 构建和健康检查，确认服务器基线可用。
5. 合并 CD 工作流后，使用 `workflow_dispatch` 在 `dev` 做首次受控部署。
6. 验证自动部署、路径过滤和失败恢复后，保留后续 `dev` 应用变更自动触发。
