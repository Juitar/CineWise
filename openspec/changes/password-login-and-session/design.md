## Context

当前后端只有默认拒绝的 `SecuritySkeletonConfiguration`、`CurrentUserAccessor` 公共端口和认证包占位；前端已有唯一原生 `fetch` REST 客户端、双组件库和响应式布局基础。认证表尚无 Flyway 迁移，且 `DATABASE_MIGRATION_REVIEW.md` 要求 C 先确认字段、A 分配版本并负责最终 SQL 与空 MySQL 验证。

前端文档存在两类需要在本变更中明确的旧描述：部分文档仍要求 Umi `request`，但前端主设计已记录撤回 request 插件并使用原生 `fetch`；后端总系分仍使用 `CurrentUser.userId/email`，而较新的认证设计要求 REST 返回 `id/emailMasked`。

## Goals / Non-Goals

**Goals:**

- 在现有单一安全链中加入密码认证、JWT Cookie、CSRF、角色规则和 `CurrentUserAccessor` 实现。
- 让 PC/移动登录页复用同一认证 API、状态 Hook、错误恢复和安全回跳工具。
- 保证旧 JWT 在登出、账号禁用或 `tokenVersion` 变化后失效，且前端无法读取 JWT。
- 先把 `sys_user`、`sys_login_log` 的字段和验证材料交给 A，不绕过迁移审批。

**Non-Goals:**

- 不实现邮箱验证码、注册、邀请码、重置密码、邮件 Provider、账号注销和个人资料编辑。
- 不创建 Flyway 版本、不生成或执行最终迁移 SQL、不向共享数据库写入演示账号。
- 不修改 A/B/D 业务 DTO，不为 REST 再建立 Umi request 或其他请求封装。

## Decisions

### 1. 保留唯一原生 fetch REST 客户端

继续使用 `shared/api/apiRequest`，在其内部增加 CSRF Token 获取、写请求 Header 和 401 单次处理扩展。这样不引入会附带无关插件的 `@umijs/plugins`，也不改变 A/B/D 后续模块的调用方式。Agent POST SSE 仍使用独立公共 SSE 客户端，不复用普通 REST 响应解包。

### 2. 单一 Spring Security 过滤链

直接替换默认拒绝安全壳中的授权规则并插入 JWT Cookie 过滤器，不新建第二条顺序不明确的过滤链。公开路径只包含健康检查、开发文档、公开场次查询、CSRF 获取和登录入口；管理员业务路径要求 `ADMIN`，其他受保护路径要求有效身份。

### 3. JWT 使用标准 Spring Security JOSE 能力

增加 Spring Security OAuth2 JOSE 依赖，使用框架 `JwtEncoder/JwtDecoder` 生成和校验 HMAC JWT，避免自行拼接或实现签名算法。密钥只来自 `JWT_SECRET`，没有可用于生产的默认值；最小长度在配置启动时校验。

JWT 默认有效期 30 分钟，可通过认证配置调整。Cookie 名为 `cinewise_access_token`，正式环境默认 `Secure=true`、`HttpOnly=true`、`SameSite=Lax`、`Path=/`。dev 环境只有显式配置才允许 `Secure=false`。

### 4. 数据库状态决定会话是否仍有效

JWT 过滤器解析 `sub/role/tokenVersion/jti` 后，通过认证 Repository 读取 `sys_user` 的状态、角色和当前 `token_version`。任何不一致都不建立安全上下文。Redis 以后可减少读取压力，但不能替代数据库检查；本变更不因 Redis 不可用而跳过会话失效校验。

登出在事务中增加 `token_version`，随后清除 Cookie，因此会使该账号所有旧 JWT 失效。该取舍牺牲多设备独立登出，但满足当前设计中 Redis 不可用时旧会话仍必须失效的要求；未来若要只退出当前设备，需要新增可持久验证的 `jti` 失效设计，不能只依赖易丢失的缓存。

### 5. CSRF Token 通过显式接口交给公共请求层

后端使用 Cookie 型 CSRF Token Repository，并提供 `GET /api/v1/auth/csrf` 强制生成 Token，统一响应返回 `token` 和固定 Header 名 `X-XSRF-TOKEN`。CSRF Cookie 使用 `HttpOnly`、`SameSite=Lax`、`Path=/`，正式环境使用 `Secure`；浏览器自动携带 Cookie，前端不解析 Cookie，而是把接口返回的 Token 保存在运行内存并加入写请求 Header。Spring Security 在进入 Controller 前比较 Cookie 中的期望 Token 与 Header，缺失或不匹配统一返回 HTTP 403 和 `201009`。

首次写请求前公共请求层只合并一次并发 CSRF 获取。登录成功后清除匿名阶段 Token，前端在 `/auth/me` 恢复身份后清除内存 Token；登出成功后同时清除认证 Cookie、CSRF Cookie 和前端内存 Token。下一次写请求重新调用 CSRF 接口。

收到 HTTP 403 和 `201009` 时，公共层清除旧 Token 并获取新 Token，但不自动重放原写请求；页面必须让用户重新确认。收到 `201007` 等权限 403 时保留当前 Token 和登录态，直接展示无权限。这样能够区分“CSRF 已失效”和“角色没有权限”，也不会因自动重放产生重复业务写入。CSRF 接口与 Header 是公共浏览器规则，必须由受影响 Owner 审查后进入实现。

### 6. 认证应用服务与持久化分层

Controller 只校验请求、调用 Application Service、设置或清除 Cookie 并映射响应。密码认证、角色检查、账号状态检查、JWT 签发和登出由认证 Application Service 组织；BCrypt 校验和 JWT/Cookie 适配位于安全基础设施；MyBatis Entity、Mapper 和 Repository 实现仅位于认证基础设施。

登录日志在认证结果明确后独立写入，失败不改变登录结果。日志只保存文档允许的摘要字段，不保存完整邮箱、密码、Cookie 或 JWT。

### 7. REST 与内部当前用户类型分开

现有 `auth.application.CurrentUser(Long userId, RoleCode role, long tokenVersion)` 保持为跨模块内部身份，不直接作为 HTTP 响应。认证 API 新建 `CurrentUserResponse`，对外输出 `id` 字符串和 `emailMasked` 等字段，避免把内部 `tokenVersion` 或完整邮箱暴露给浏览器。

### 8. 登录页面按端拆视图、共用 Hook

`modules/auth` 提供 DTO、API、`usePasswordLogin` 和会话恢复；`shared/auth` 提供当前用户 Provider、401 单次处理和安全回跳；`pages` 只读取 `returnUrl` 并组合视图。桌面端使用 Ant Design，移动端使用 antd-mobile，自定义样式全部放在 CSS 文件。

当前范围不提供验证码、注册和重置密码页面，因此不渲染不可用的验证码 Tab 或会进入 404 的操作入口。用户协议和隐私政策只有在存在可访问页面时才显示链接；密码登录不提交隐私同意字段。

### 9. 演示账号由显式认证种子初始化器创建

在现有固定种子开关之外增加认证种子配置，用户和管理员邮箱、密码、昵称、隐私政策版本来自环境变量或只读 Secret。初始化器以邮箱幂等补齐账号，不覆盖已有账号的邮箱、密码、角色、状态和 `tokenVersion`，也不记录邮箱或凭据。

本地可以使用 `user@cinewise.test`、`admin@cinewise.test`；正式演示在全新数据库首次初始化前配置真实可收信的测试邮箱。环境变量变化不会静默修改已有账号，避免把已有用户身份和其他模块的 `userId` 关联改乱。

### 10. 注册同意和后续登录分开处理

注册属于后续独立变更，注册页面的隐私同意框必须默认未勾选，服务端只有收到 `privacyAccepted=true` 且版本有效时才写入 `privacy_policy_version/privacy_accepted_at`。本次密码登录只读取并返回已记录版本，不更新同意字段。

登录页可以提供协议链接和说明，但不能把“点击登录”持久化成新同意，也不能在政策升级后自动覆盖旧版本。未来政策版本变化时，需要新增显式重新确认接口和页面状态；该流程不通过修改 V006 或登录 DTO 临时实现。

## Risks / Trade-offs

- [A/B/D 尚未确认 CSRF 公共接口] → OpenSpec 先固定建议字段，任务清单把 Owner 确认作为编码前置条件；未确认前不修改安全链和公共请求层。
- [认证表迁移未完成，真实数据库无法登录] → C 只提交字段与场景材料；A 分配版本、生成或审核 SQL 并完成空 MySQL 验证后再做真实联调。
- [登出导致同账号所有设备退出] → 当前优先保证 Redis 故障时旧 JWT 也失效，并在用户提示和测试中明确；需要单设备登出时另建变更。
- [每次受保护请求读取用户表增加延迟] → 首版先保证禁用和 tokenVersion 立即生效；达到性能瓶颈后再引入不降低安全性的短期缓存。
- [本地 HTTP 与 Secure Cookie 冲突] → 生产默认始终 Secure，本地通过独立环境配置关闭并在部署测试中验证 HTTPS Cookie。
- [前端设计文档仍有旧请求方式和 DTO] → 实现只遵循本变更与当前代码；文档修正列为独立可验证任务，不在代码中保留两套兼容字段。
- [演示前才把假邮箱改成真邮箱但数据库已有账号] → 初始化器不覆盖旧账号；正式演示使用全新数据库先配置真实邮箱，已有数据库以后通过受控账号用例修改。

## Migration Plan

1. C 在本变更中确认 `sys_user`、`sys_login_log` 字段、索引、保留期和兼容要求，并向 A 提交迁移申请材料。
2. A 已分配 `V006__create_auth_user_and_login_log_tables.sql`；A 审核或生成最终 SQL，AI 仅按迁移规范做只读复核。
3. A 在独立空 MySQL 8.4 中执行迁移、重复执行、索引、约束、字符集与排序规则验证并保存证据。
4. 合并后端认证实现和前端登录功能，使用测试账号完成用户/管理员登录、刷新恢复、登出、401/403 和 CSRF 冒烟。
5. 若应用需要回滚，回滚应用版本并保留向前兼容的认证表；已经执行的迁移不改名、不修改、不逆向覆盖。

## Open Questions

- A 需要在迁移评审中确认 `sys_login_log` 是只追加日志，因此不增加无业务含义的 `update_time`。
