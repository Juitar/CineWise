## Purpose

让进入 `dev` 的应用变更在通过质量门后自动部署到演示服务器，同时禁止自动数据库迁移并在应用部署失败时恢复上一版本。

## ADDED Requirements

### Requirement: 应用变更通过质量门后部署

系统 SHALL 在后端、前端或部署配置进入 `dev` 时执行后端 MySQL 和 Redis 集成质量门，只有全部成功后才部署触发工作流的精确 Git 提交。前端构建、检查和 E2E 由独立前端 CI 负责，Demo Deploy 不得重复运行生产浏览器测试。

#### Scenario: PR 合并应用代码

- **WHEN** 个人分支 PR 合并后使 `dev` 的应用代码发生变化
- **THEN** 系统完成后端 MySQL 与 Redis 集成验证，并在全部成功时部署该提交

#### Scenario: 质量门失败

- **WHEN** 后端 MySQL 或 Redis 集成验证失败
- **THEN** 系统不连接演示服务器执行部署

#### Scenario: 独立前端 E2E 失败

- **WHEN** 独立前端 CI 的生产 E2E 失败
- **THEN** Demo Deploy 不重复执行该 E2E，且不因该 Job 阻断部署

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

### Requirement: 公共 Compose 显式传递后端运行配置

系统 SHALL 保持一份公共应用 Compose，并显式将环境文件中的认证 Cookie、认证演示种子、票务时限和内容同步运行开关传递给 backend；不得依赖容器未声明的环境变量或成员私有 Compose 覆盖。

#### Scenario: HTTP 演示入口使用非 Secure Cookie

- **WHEN** 环境文件将 `AUTH_COOKIE_SECURE` 配置为 `false` 且应用通过 HTTP 演示入口运行
- **THEN** backend 容器收到同一值，认证与 CSRF Cookie 不带 `Secure` 属性

#### Scenario: HTTPS 演示入口使用 Secure Cookie

- **WHEN** 环境文件将 `AUTH_COOKIE_SECURE` 配置为 `true` 且应用通过 HTTPS 入口运行
- **THEN** backend 容器收到同一值，认证与 CSRF Cookie 带 `Secure` 属性

### Requirement: 本地与服务器使用不同无密钥环境模板

系统 SHALL 提供本地与服务器环境模板，但不得复制 Dockerfile 或完整 Compose 服务定义。模板不得包含真实密码、JWT、MinIO Key、SSH 私钥或服务器地址。

#### Scenario: 本地 SSH 隧道联调

- **WHEN** 开发者基于本地模板配置应用
- **THEN** MySQL 和 Redis 默认指向 `host.docker.internal` 的本地隧道端口，浏览器使用 `http://localhost:8000`

#### Scenario: 服务器连接共享基础服务

- **WHEN** 部署人员基于服务器模板配置应用
- **THEN** MySQL、Redis 与可选 MinIO 使用基础服务 ECS 的私网或受白名单保护的地址，应用 Compose 不创建第二套基础服务

### Requirement: 部署失败恢复上一应用版本

系统 SHALL 记录部署前 Git SHA，使用 Compose 健康检查验证新版本，并在构建或健康检查失败时恢复上一提交。

#### Scenario: 新后端无法通过健康检查

- **WHEN** 新提交的 Compose 服务未在超时内全部健康
- **THEN** 系统重新检出部署前 SHA、恢复旧服务并让本次工作流保持失败

### Requirement: 生产前端正确区分页面路由、静态资源和 API

系统 SHALL 让生产 Nginx 将 `/api/**` 明确代理到后端，让不存在的脚本、样式、图片和字体返回 404，并仅对非静态的前端页面路由回退到不缓存的 `index.html`。

#### Scenario: 刷新前端嵌套路由

- **WHEN** 浏览器直接访问 `/movies`
- **THEN** Nginx 返回 200 `text/html` 的 `index.html`，且响应包含 `Cache-Control: no-store`

#### Scenario: 请求不存在的静态资源

- **WHEN** 浏览器请求不存在的 `/missing.js` 或 `/missing.css`
- **THEN** Nginx 返回 404，不得返回 `index.html` 或其他 `text/html` 响应

#### Scenario: 请求后端 API

- **WHEN** 浏览器请求任意 `/api/**` 地址
- **THEN** Nginx 将请求转发到后端，不得由静态资源规则或 SPA 回退处理

#### Scenario: 加载生产入口资源

- **WHEN** 浏览器打开生产镜像中的 `index.html`
- **THEN** 入口引用的本地脚本和样式全部返回 200 及与文件类型相符的 MIME，并能在浏览器中启动应用

#### Scenario: 缓存带内容哈希的静态资源

- **WHEN** 浏览器请求文件名包含至少 8 位内容哈希且实际存在的生产静态资源
- **THEN** Nginx 返回一年 `immutable` 缓存，而 `index.html`、`/api/**` 和缺失资源不得获得该长期缓存
