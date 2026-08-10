## Why

当前认证后端只有密码登录、会话恢复和登出，用户无法发送验证码、使用验证码登录，也无法完成 PRD 要求的邀请制注册。C 需要在数据库迁移提交前一次补齐验证码发送、验证码登录和注册事务，同时复用现有 JWT Cookie、CSRF、当前用户响应和登录审计。

## What Changes

- 新增 `POST /api/v1/auth/email-codes`，本次支持 `REGISTER` 和 `LOGIN` 用途；未知账号或已注册邮箱使用统一成功语义，避免探测账号是否存在。
- 新增 `POST /api/v1/auth/login/email`，只消费 `LOGIN` 用途验证码，成功后写入与密码登录相同的 JWT HttpOnly Cookie。
- 新增 `POST /api/v1/auth/register`，在一个本地事务中消费 `REGISTER` 验证码、条件扣减邀请码、创建 `USER` 账号并记录隐私政策同意；成功后自动建立会话。
- 注册支持可选昵称；未填写时按服务端用户 ID 生成默认昵称。密码必须为 8～20 位且同时包含字母和数字。
- 重复 `clientRequestId` 只有在邮箱、密码、邀请码和隐私政策与原注册结果匹配时才返回原账号；请求 ID 不单独作为身份凭证。
- 验证码使用 6 位数字、5 分钟有效、60 秒发送冷却和最多 5 次校验；只保存 HMAC-SHA-256 摘要，不记录或返回验证码。
- 同邮箱、同用途的验证码发送通过 Redis 冷却键防重复；Redis 不可用时失败关闭，不绕过限流。
- 邮件发送通过认证模块公开端口；SMTP 地址、账号、密码和发件人只从部署环境读取，代码和日志不包含真实密钥或验证码。
- 增加 `sys_email_verify_code`、`sys_registration_invite`、`sys_registration_invite_use` 持久化端口和事务规则；V011 由 A 审查和执行，邀请码不使用 Flyway 种子迁移。
- 首个培训邀请码在首次初始化前通过 Git 忽略的环境配置受控创建，默认关闭；重复初始化不得创建第二条记录或重置 `used_count`。
- 接通前端 `/register` 页面：登录页提供注册链接，注册页发送 `REGISTER` 验证码并提交邀请制注册；成功后恢复全局会话，结果未知时只查询 `/auth/me`。
- 接通登录页邮箱验证码方式：用户可在密码登录和验证码登录之间切换，发送 `LOGIN` 验证码并提交验证码登录；结果未知时只查询 `/auth/me`。
- 不实现密码重置、邀请码管理页面/API 和 D 的观影提醒邮件。

## Capabilities

### New Capabilities

- `email-code-authentication`: 邮箱验证码发送、限流、持久化、一次性消费、邮箱验证码登录和邀请制注册。

### Modified Capabilities

无。

## Impact

- 后端：`auth/api`、`auth/application`、`auth/domain`、`auth/infrastructure/persistence`、`auth/infrastructure/rate`、`auth/infrastructure/mail` 和认证配置。
- 前端：`shared/auth`、`modules/auth`、`pages/login`、`pages/register` 及对应测试。
- 接口：新增 `/api/v1/auth/email-codes`、`/api/v1/auth/login/email` 和 `/api/v1/auth/register`；复用现有 `CurrentUserResponse`、Cookie 和 CSRF Header。
- 数据库：`V011__create_auth_email_code_and_registration_tables.sql` 只新增 `sys_email_verify_code`、`sys_registration_invite`、`sys_registration_invite_use`，并以向前迁移把 `sys_login_log.login_type` 的 CHECK 增加 `EMAIL_CODE`；不包含邀请码种子或其他业务初始化状态。A 审查 SQL 并决定空 MySQL 8.4 验证授权。
- 配置：新增验证码摘要密钥、邀请码摘要密钥、当前隐私政策版本、受控首个邀请码初始化开关与参数、SMTP Provider 开关、发件人和超时环境变量；正式环境不得使用 Demo 验证码。
- Owner：C；A 负责 V011 的 SQL 审查和受控验证，不修改 A/B/D 业务模块。
