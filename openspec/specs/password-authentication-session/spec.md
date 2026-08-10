# password-authentication-session Specification

## Purpose
TBD - created by archiving change password-login-and-session. Update Purpose after archive.
## Requirements
### Requirement: 用户邮箱密码登录
系统 SHALL 提供 `POST /api/v1/auth/login/password`，接收 `clientRequestId`、`email` 和 `password`。成功响应 SHALL 写入认证 Cookie，并返回与 `/api/v1/auth/me` 相同结构的 `CurrentUser`。

#### Scenario: 正常用户登录成功
- **WHEN** 状态为 `NORMAL` 的 `USER` 使用规范化邮箱和正确密码提交唯一 `clientRequestId`
- **THEN** 系统返回 `code=0` 和 `CurrentUser(role=USER)`，并写入短期认证 Cookie

#### Scenario: 凭据错误不泄露账号信息
- **WHEN** 邮箱不存在、密码错误或账号凭据不可用
- **THEN** 系统返回 HTTP 401 和错误码 `201001`，并使用统一安全文案

#### Scenario: 登录请求参数非法
- **WHEN** `clientRequestId`、邮箱或密码缺失或格式不合法
- **THEN** 系统返回 HTTP 400 和错误码 `101001`，且响应和日志均不包含密码

### Requirement: 管理员邮箱密码登录
系统 SHALL 提供 `POST /api/v1/admin/auth/login`，只允许共用用户表中角色为 `ADMIN` 且状态正常的账号建立管理员会话，不接受 `account` 或 `username` 字段。

#### Scenario: 管理员登录成功
- **WHEN** 状态为 `NORMAL` 的 `ADMIN` 使用正确邮箱和密码登录
- **THEN** 系统返回 `CurrentUser(role=ADMIN)` 并写入认证 Cookie

#### Scenario: 普通用户不能从管理入口提升角色
- **WHEN** `USER` 使用正确邮箱和密码调用管理员登录接口
- **THEN** 系统拒绝请求且不会签发 `ADMIN` 身份

### Requirement: 当前用户响应不泄露完整邮箱
认证 REST 接口 SHALL 返回字符串 `id`、`role`、`nickname`、`emailMasked`、`emailVerified`、`status` 和 `privacyPolicyVersion`。`id` SHALL 为雪花 ID 的十进制字符串，响应不得包含完整邮箱、密码散列、JWT 或 `tokenVersion`。

#### Scenario: 获取当前用户
- **WHEN** 浏览器携带有效认证 Cookie 调用 `GET /api/v1/auth/me`
- **THEN** 系统返回当前账号的脱敏身份摘要，且字段与登录成功响应一致

#### Scenario: 会话无效
- **WHEN** Cookie 缺失、JWT 过期、账号状态异常或 JWT 中 `tokenVersion` 与数据库不一致
- **THEN** 系统返回 HTTP 401 和错误码 `201006`，且不返回旧用户摘要

### Requirement: 登录不自动产生新的隐私同意
密码登录 SHALL 只校验账号凭据和状态，并返回账号已经记录的 `privacyPolicyVersion`。登录请求不得包含隐私同意字段，也不得修改 `privacy_policy_version` 或 `privacy_accepted_at`。

#### Scenario: 已注册账号再次登录
- **WHEN** 账号在注册时已经显式同意隐私政策并完成密码登录
- **THEN** 系统沿用原有同意版本和时间，不把本次登录记录成一次新的同意

#### Scenario: 隐私政策版本更新
- **WHEN** 当前隐私政策版本高于账号已记录版本
- **THEN** 系统不得通过登录自动升级账号的同意版本，后续必须由独立隐私确认用例取得用户主动确认

### Requirement: JWT Cookie 与服务端身份校验
JWT SHALL 只写入 `HttpOnly` Cookie，最小声明仅包含 `sub`、`role`、`tokenVersion`、`iat`、`exp` 和 `jti`。正式环境 Cookie SHALL 使用 `Secure`、`SameSite=Lax` 和 `Path=/`；本地 HTTP 只能通过环境配置关闭 `Secure`。每个受保护请求 SHALL 重新校验账号状态和数据库 `tokenVersion`。

#### Scenario: 前端无法读取 JWT
- **WHEN** 登录成功并建立会话
- **THEN** 浏览器自动携带 Cookie，前端 JavaScript 不读取、保存、拼接或打印 JWT

#### Scenario: 旧版本 JWT 被拒绝
- **WHEN** 账号的 `tokenVersion` 已增加后再次使用旧 JWT
- **THEN** 系统返回 HTTP 401 和错误码 `201006`

### Requirement: 浏览器写请求执行 CSRF 校验
系统 SHALL 提供 `GET /api/v1/auth/csrf` 供匿名或已登录浏览器取得 CSRF Token，并要求浏览器状态变更请求使用 `X-XSRF-TOKEN` Header。服务端 SHALL 使用 Cookie 中保存的期望 Token 校验 Header，缺失或不匹配时在进入 Controller 前拒绝请求。CSRF Token 不得作为 JWT 使用，也不得持久化到前端本地存储。

#### Scenario: 登录前取得 CSRF Token
- **WHEN** 匿名浏览器调用 CSRF 接口
- **THEN** 系统返回当前 Token 和 Header 名称，后续密码登录请求可以完成 CSRF 校验

#### Scenario: 缺少 CSRF Header
- **WHEN** 浏览器对受保护的状态变更接口发起请求但没有提交有效 CSRF Header
- **THEN** 系统返回 HTTP 403 和错误码 `201009`，且不执行登录、登出或其他业务写操作

#### Scenario: CSRF Token 不匹配
- **WHEN** `X-XSRF-TOKEN` 与当前 CSRF Cookie 中的期望 Token 不一致
- **THEN** 系统返回 HTTP 403 和错误码 `201009`，且不进入业务 Controller

#### Scenario: 登录成功后换新
- **WHEN** 密码登录通过 CSRF 校验并成功建立认证会话
- **THEN** 系统使匿名阶段的 CSRF Token 失效，前端清除内存 Token，并在下一次写请求前重新获取 Token

#### Scenario: 登出后换新
- **WHEN** 登出请求通过 CSRF 校验并完成会话失效
- **THEN** 系统清除认证 Cookie 和原 CSRF Token，前端清除内存 Token，后续写请求必须重新获取 Token

#### Scenario: CSRF 403 不自动重放写请求
- **WHEN** 前端收到 HTTP 403 和错误码 `201009`
- **THEN** 前端清除旧 Token 并获取新 Token，但不自动重放原写请求，由调用页面提示用户重新确认

#### Scenario: 权限 403 不刷新 CSRF Token
- **WHEN** 已登录用户收到 HTTP 403 和错误码 `201007`
- **THEN** 前端保留登录状态和当前 CSRF Token，展示无权限状态且不重试请求

### Requirement: 登出使旧会话失效
系统 SHALL 提供 `POST /api/v1/auth/logout`。登出 SHALL 幂等、清除认证 Cookie，并增加账号 `tokenVersion`，使该账号此前签发的 JWT 不能继续访问受保护接口。

#### Scenario: 已登录用户登出
- **WHEN** 已登录用户携带有效 CSRF Token 调用登出接口
- **THEN** 系统返回 `loggedOut=true`、清除 Cookie，并拒绝该账号此前签发的 JWT

#### Scenario: 重复登出
- **WHEN** 客户端在首次登出响应丢失后再次调用登出
- **THEN** 系统仍返回安全的登出结果，不恢复任何旧会话

### Requirement: 认证错误和日志不暴露敏感信息
认证失败 SHALL 使用稳定 HTTP 状态和数值错误码。登录日志 SHALL 只记录必要的用户 ID、登录类型、结果、失败码、IP 摘要、User-Agent 摘要和 traceId，不记录完整邮箱、密码、Cookie 或 JWT。

#### Scenario: 登录失败审计
- **WHEN** 密码登录明确成功或失败
- **THEN** 登录结果可通过 traceId 排查，且响应、普通日志和登录日志中没有密码、完整邮箱、Cookie 或 JWT

### Requirement: 登录日志结果一致且按有限保留期清理
`sys_login_log` SHALL 通过数据库 CHECK 保证结果一致：成功日志必须包含 `user_id` 且 `failure_code` 为空，失败日志必须包含 `failure_code`，失败时 `user_id` 可以为空。系统 SHALL 默认保留登录日志 30 天，并通过 `idx_login_cleanup_create_time(create_time)` 支持认证清理任务按到期时间物理删除。

30 天物理删除 SHALL 是“审计记录不得物理删除”通用规范仅针对 `sys_login_log` 的有限保留期例外，不得扩展到订单、支付、电子票、退款等交易审计记录。清理 SHALL 只删除超过正整数配置保留期的记录，不提供按用户或单条日志随意删除的业务入口。

#### Scenario: 成功日志字段不一致
- **WHEN** 系统尝试写入 `success=1` 但 `user_id` 为空或 `failure_code` 不为空的登录日志
- **THEN** 数据库拒绝该记录

#### Scenario: 失败日志缺少失败码
- **WHEN** 系统尝试写入 `success=0` 且 `failure_code` 为空的登录日志
- **THEN** 数据库拒绝该记录

#### Scenario: 清理超过保留期的登录日志
- **WHEN** 认证清理任务扫描超过配置保留期的 `sys_login_log` 记录
- **THEN** 系统使用 `create_time` 清理索引物理删除到期记录，且不删除保留期内记录或其他审计表数据

