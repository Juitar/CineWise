## Why

当前前后端只有默认拒绝的安全壳和公共请求基础，没有可供用户、管理员实际使用的密码登录、会话恢复与登出能力。角色 C 需要先完成最小登录范围，让后续选座、订单、Agent 和管理页面能够从统一认证上下文取得可信身份。

## What Changes

- 增加用户邮箱密码登录和管理员邮箱密码登录；管理员入口只允许 `ADMIN`，普通用户不能通过管理入口提升角色。
- 增加 `/api/v1/auth/me` 会话恢复与幂等登出，登录成功后前端仍以 `/auth/me` 的结果建立当前用户状态。
- 使用短期 JWT HttpOnly Cookie，服务端校验账号状态和 `tokenVersion`，前端不读取、保存或打印 JWT。
- 增加浏览器写请求所需的 CSRF Token 获取接口和公共请求层 Header 注入；具体接口和 Header 需 A/B/D 确认后再实现。
- 增加 PC Web 与移动 H5 共用业务逻辑的登录页面、安全 `returnUrl`、提交防重复和登录结果未知恢复。
- 保留现有原生 `fetch` 公共 REST 客户端，不引入 Umi request 插件或第二套请求封装。
- 认证 REST `CurrentUser` 使用字符串 `id`、脱敏邮箱和认证设计中的字段，不返回完整邮箱；内部安全上下文继续使用 `userId/role/tokenVersion`。
- 本变更不实现邮箱验证码登录、验证码发送、注册、密码重置和相关页面；登录页不把隐私同意作为密码登录字段。
- 本变更只提出 `sys_user`、`sys_login_log` 迁移需求和审查材料，不自行分配 Flyway 版本、生成最终迁移 SQL 或执行数据库迁移。

## Capabilities

### New Capabilities

- `password-authentication-session`: 用户/管理员密码登录、JWT Cookie、CSRF、当前用户恢复、登出、角色校验和认证错误语义。
- `responsive-login-experience`: PC/移动登录页面、公共请求调用、安全回跳、并发提交控制和登录结果未知恢复。

### Modified Capabilities

无。

## Impact

- 后端：`auth/api`、`auth/application`、`auth/domain`、`auth/infrastructure/security`、`auth/infrastructure/persistence`，以及现有默认拒绝安全链。
- 前端：`modules/auth`、`shared/auth`、`shared/api`、登录页路由和响应式登录视图；现有 `apiRequest` 对外调用方式保持不变。
- 公共接口：新增密码登录、管理员登录、当前用户、登出和 CSRF Token 获取接口；CSRF 接口与 Header 会影响 A/B/D 后续浏览器写请求，编码前需要相关 Owner 审查。
- 数据库：需要 `sys_user`、`sys_login_log`；C 确认字段，A 分配版本、审核或生成最终 SQL、授权并在空 MySQL 验证。
- 部署：正式环境必须使用 HTTPS 和 `Secure` Cookie；本地 HTTP 仅允许通过环境配置关闭 `Secure`，不得成为生产默认值。
- 文档：认证设计、前端应用设计中残留的 Umi `request` 描述，以及后端总系分中 `CurrentUser` 的 `userId/email` 描述需要后续按已确认接口修正。
