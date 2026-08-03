## Purpose

让进入 `dev` 的应用变更在通过质量门后自动部署到演示服务器，同时禁止自动数据库迁移并在应用部署失败时恢复上一版本。

## ADDED Requirements

### Requirement: 应用变更通过质量门后部署

系统 SHALL 在后端、前端或部署配置进入 `dev` 时执行后端和前端质量门，只有全部成功后才部署触发工作流的精确 Git 提交。

#### Scenario: PR 合并应用代码

- **WHEN** 个人分支 PR 合并后使 `dev` 的应用代码发生变化
- **THEN** 系统完成后端与前端验证，并在全部成功时部署该提交

#### Scenario: 质量门失败

- **WHEN** 后端验证、前端检查或 E2E 任一失败
- **THEN** 系统不连接演示服务器执行部署

### Requirement: 非应用变更不触发部署

系统 SHALL 忽略仅包含文档、OpenSpec、迁移记录或 Flyway SQL 的 `dev` 更新。

#### Scenario: A 直提迁移 SQL

- **WHEN** 提交只修改 `backend/src/main/resources/db/migration/**`
- **THEN** 系统不自动部署应用，也不执行数据库迁移

### Requirement: 部署凭据不进入仓库

系统 SHALL 从 GitHub `demo` Environment 取得 SSH 主机、账号、私钥、known_hosts 和部署目录，不得把这些值写入仓库或日志。

#### Scenario: 部署配置缺失

- **WHEN** 任一必填 Environment secret 缺失
- **THEN** 部署在连接服务器前失败并指出缺少的配置名称

### Requirement: CD 不执行数据库变更

系统 SHALL 在部署前确认 Flyway 和种子初始化关闭，不得执行迁移 SQL、数据库修数或权限变更。

#### Scenario: 服务器误开启 Flyway

- **WHEN** 服务器 `.env` 将 `FLYWAY_ENABLED` 配置为真
- **THEN** 系统拒绝部署且不启动新应用版本

### Requirement: 应用复用受控共享基础服务

系统 SHALL 要求演示服务器通过被 Git 忽略的 `.env` 显式配置共享 MySQL 和 Redis 连接，不得在应用 Compose 中重复创建 MySQL、Redis 或 MinIO；可选 MinIO 配置 SHALL 透传给后端且未启用对象存储时不得阻断核心应用启动。

#### Scenario: 部署服务器连接共享 Redis

- **WHEN** 演示服务器使用有效的 `REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD` 部署应用
- **THEN** Compose 不创建本地 Redis 容器，后端连接指定的共享 Redis，并由应用健康检查验证连接结果

#### Scenario: 共享 Redis 配置缺失

- **WHEN** 演示服务器 `.env` 缺少 Redis 主机或密码
- **THEN** Compose 在构建或启动应用前明确失败，不使用 `localhost` 或容器服务名作为隐式回退

#### Scenario: MinIO 尚未启用

- **WHEN** MinIO 环境变量为空且当前模块未启用对象存储客户端
- **THEN** 后端核心查询、交易和健康检查仍可启动

### Requirement: 部署失败恢复上一应用版本

系统 SHALL 记录部署前 Git SHA，使用 Compose 健康检查验证新版本，并在构建或健康检查失败时恢复上一提交。

#### Scenario: 新后端无法通过健康检查

- **WHEN** 新提交的 Compose 服务未在超时内全部健康
- **THEN** 系统重新检出部署前 SHA、恢复旧服务并让本次工作流保持失败
