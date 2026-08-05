## Context

现有 `password-login-and-session` 已提供账号查询、JWT Cookie、CSRF、当前用户和登录审计，但明确排除了验证码和注册。认证设计已经确定验证码、邀请码、邀请码使用记录的表字段，以及注册接口、事务和错误码；V011 已发布并通过 GitHub Actions 的一次性 MySQL 8.4 验证，当前仍缺少服务器首个邀请码受控初始化、共享 Redis 和正式 SMTP 收信验证。

## Goals / Non-Goals

**Goals:**

- 完成 `REGISTER/LOGIN` 验证码发送、`LOGIN` 验证码登录和 `REGISTER` 验证码注册。
- 复用现有用户、Cookie、Token、CSRF、错误响应和审计实现。
- 对重复发送、并发消费、失败尝试、Redis 故障和邮件结果未知给出确定处理。

**Non-Goals:**

- 不实现密码重置、邀请码管理 API 或管理员验证码登录。
- 不实现 D 的 `VIEWING_REMINDER` 模板、通知日志或重试任务。
- 不自行分配或执行 Flyway 迁移，不把 Demo 验证码作为生产默认值。

## Decisions

### 1. 验证码规则使用独立配置

默认生成 6 位数字，使用 `SecureRandom`；有效期 300 秒、发送冷却 60 秒、最大失败次数 5。部署可以在安全范围内调整时间和次数，但不能配置固定生产验证码。摘要使用独立必填 `AUTH_VERIFICATION_HASH_SECRET` 计算 HMAC-SHA-256，不复用 JWT 或登录审计密钥；数据库以 `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` 保存小写十六进制摘要，并通过 CHECK 拒绝其他格式。

### 2. 发送限流先于账号可用性判断

`VerificationCodeRateLimiter` 只接收规范化邮箱摘要、IP 摘要和用途，不接收邮箱明文。Redis Adapter 使用原子 `SET NX` 和 TTL 建立邮箱用途冷却；同窗口重复请求读取剩余 TTL 并返回成功，不创建记录。IP 频率上限作为配置化固定窗口实现；Redis 故障统一映射为邮件能力不可用并失败关闭。

### 3. 未知账号使用统一成功语义

取得发送许可后，Application Service 再判断用途是否允许投递。`LOGIN` 只向存在、正常、已验证的 `USER` 发送；`REGISTER` 只向尚未注册的邮箱发送。不允许投递时仍返回固定成功数据，且不创建验证码。这样接口状态和耗时不直接暴露账号存在性。

### 4. 生成、持久化、外部发送分开

应用服务先生成验证码和摘要，再调用短事务 `VerificationCodeIssueTransaction`：使同邮箱同用途的旧 `UNUSED` 记录失效并插入新记录。事务提交后调用 `VerificationEmailSender`，外部 SMTP 不进入数据库事务。明确失败时调用独立事务使记录失效；结果未知保持记录和冷却，避免自动重发。

### 5. 消费使用数据库条件更新

登录先按邮箱和 `LOGIN` 查询最新有效记录。摘要不匹配时通过条件更新增加 `attempt_count`，第 5 次同时改为 `INVALID`；匹配时通过 `status=UNUSED AND expire_time>now AND attempt_count<max` 条件更新为 `USED`。只有影响一行才继续签发 Token。用户账号状态和角色在消费前后都由认证应用服务检查，管理员不允许从该入口登录。

### 6. SMTP Adapter 只通过配置启用

邮件端口返回 `SENT/FAILED/UNKNOWN`。SMTP Adapter 使用 Spring Boot Mail 的 `JavaMailSender` 发送纯文本认证模板，主题和正文固定，不接受前端模板；地址、认证、SSL/STARTTLS、启动连接检查和超时由 `spring.mail.*` 环境配置提供。465 SMTPS 使用 `SMTP_SSL_ENABLED=true` 并关闭 STARTTLS；587 STARTTLS 使用 `SMTP_STARTTLS_ENABLED=true`。未启用 SMTP 时使用失败关闭 Adapter，接口返回 `301001`。代码和测试不打印验证码。

### 7. V011 只负责结构和前向 CHECK

`sys_email_verify_code` 使用设计已确认的 11 个字段，补充正数雪花 ID、`status` 仅允许 `UNUSED/USED/INVALID`、`attempt_count` 在 0～5、摘要为 64 位小写十六进制、`USED` 的 `used_time` 位于 `send_time` 至 `expire_time` 区间且其他状态为空的 CHECK。索引为 `idx_verify_lookup(email,purpose,status,expire_time)` 和用于短生命周期清理的 `idx_verify_expire(expire_time)`。邀请码表主键以及使用记录的 `id/invite_id/user_id` 同样必须为正数。V006 的 `chk_sys_login_log_type` 当前只允许 `PASSWORD/ADMIN_PASSWORD`，`V011__create_auth_email_code_and_registration_tables.sql` 必须在同一条 `ALTER TABLE` 中删除并重建该约束，加入 `EMAIL_CODE`；不得修改 V006。V011 不包含邀请码种子或其他业务初始化状态；A 审核正式 SQL，并决定是否授权空 MySQL 8.4 验证。

### 8. 注册事务同时消费验证码和邀请码

`RegistrationTransaction` 先检查相同 `clientRequestId` 的既有邀请码使用记录。若存在，只有邮箱、BCrypt 密码、邀请码摘要、隐私政策版本和非空昵称都与原账号匹配时才返回原用户；请求 ID 不能单独换取会话。

新注册在同一个事务中执行：规范化并检查邮箱未占用 → 消费 `REGISTER` 验证码 → 读取并条件扣减有效邀请码 → 生成雪花用户 ID → BCrypt 保存密码 → 创建 `USER/NORMAL/emailVerified=true` 账号 → 写邀请码使用记录。任一步失败都回滚验证码、邀请码和账号写入。数据库邮箱唯一键、邀请码 `version` 条件更新以及 `client_request_id/user_id` 唯一键处理并发。

### 9. 隐私政策和昵称由服务端复核

新增 `cinewise.auth.registration.current-privacy-policy-version`，请求必须显式传 `privacyAccepted=true` 且版本完全相等，否则返回 `201008`。昵称可空；有值时去除首尾空白且不超过 64 字符，空值使用 `用户` 加用户 ID 末 6 位生成，不从邮箱推导昵称。

### 10. 邀请码使用独立 HMAC 密钥和受控初始化

邀请码不保存明文，使用必填 `AUTH_INVITE_HASH_SECRET` 计算 HMAC-SHA-256 后按唯一索引查询。摘要同样使用 `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` 和小写十六进制 CHECK。首个培训邀请码不进入 Flyway：首次初始化前由 C 在 Git 忽略的环境配置中提供开关、明文邀请码、次数和有效期，Initializer 使用 `BusinessIdGenerator` 生成雪花 ID、计算摘要并按摘要不存在条件插入。初始化默认关闭；配置缺失或非法时拒绝执行；重复启动发现相同摘要时不新增、不修改、不重置 `used_count`。日志、Git 和普通响应不保存或输出邀请码明文、摘要密钥或完整摘要。

### 11. 前端注册复用认证模块和会话恢复

`pages/register` 只负责受控表单和页面跳转；`modules/auth` 定义验证码与注册 DTO、调用公共 `apiRequest<T>()`，并通过 Hook 管理发送冷却、重复提交、错误码和结果未知恢复。验证码发送固定提交 `purpose=REGISTER`。注册成功或响应丢失后都查询 `/auth/me`，只有查询到当前用户才更新全局身份并跳转；不得自动重发包含密码、验证码或邀请码的注册请求。敏感输入只保存在页面内存，提交完成或进入结果未知状态后清除。

当前隐私政策版本按已确认认证配置使用 `2026-08-03`。前端显式提交该版本和 `privacyAccepted=true`，后端继续做最终校验；后续公共隐私政策查询接口落地时再改为读取服务端版本，不在本次扩展接口范围。

### 12. 前端邮箱验证码登录复用统一登录页

`pages/login` 在同一路由内提供密码登录和邮箱验证码登录切换，不新增管理员验证码入口。验证码方式通过 `modules/auth` 固定发送 `purpose=LOGIN`，使用服务端返回的冷却秒数控制发送按钮；发送结果未知时启动保护冷却并提示用户先检查邮箱，不自动再次发送。

验证码登录提交 `clientRequestId/email/code`，成功后由 `AuthProvider` 查询 `/auth/me` 更新全局身份并沿用安全回跳。登录 POST 超时或断网时只查询 `/auth/me`；恢复仍失败时保留结果未知入口，恢复为匿名时清除验证码并允许用户重新获取。切换登录方式时清除密码或验证码，不在浏览器持久化敏感输入。

## Risks / Trade-offs

- [部署数据库未确认] → 上线前仍需确认目标环境的 `flyway_schema_history` 已包含 V011；不能用一次性 CI 数据库代替共享或生产数据库确认。
- [首个邀请码未初始化] → 注册接口不会有可用邀请码；V011 验证并发布后，在首次开放注册前使用 Git 忽略的环境配置执行一次受控初始化，成功后关闭初始化开关。
- [SMTP 未配置] → 默认失败关闭并返回 `301001`；真实收信只在 C/A 配置测试域名、发件人和密钥后验证。
- [外部发送后进程中断] → 结果按未知处理，保留验证码和冷却，不自动重发；用户可检查邮箱并在窗口结束后主动请求。
- [固定窗口 IP 限流误伤共享出口] → 上限配置保守，指标只记录摘要和计数；达到上限返回 `101002`，不泄露具体邮箱。

## Migration Request

- OpenSpec change：`openspec/changes/email-code-authentication/`
- 领域 Owner：C
- 涉及表：新增 `sys_email_verify_code`、`sys_registration_invite`、`sys_registration_invite_use`；向前修改 `sys_login_log` 的 `chk_sys_login_log_type`
- 结构迁移：`V011__create_auth_email_code_and_registration_tables.sql`
- 数据迁移：无；邀请码不得通过 Flyway 种子迁移初始化
- 验证码字段：`id,email,purpose,code_hash,status,send_time,expire_time,used_time,attempt_count,create_time,update_time`
- 邀请码字段：`id,code_hash,status,max_uses,used_count,valid_from,expire_time,version,create_time,update_time`
- 使用记录字段：`id,invite_id,user_id,client_request_id,used_at,create_time`
- 摘要列：验证码和邀请码的 `code_hash` 均为 `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin`，CHECK 只允许 64 位小写十六进制
- 索引：按认证总系分 T02、T03、T04；补充 `idx_verify_expire(expire_time)`；邮箱、邀请码摘要、用户和 clientRequestId 的唯一/查询规则不得删减
- CHECK：主键和逻辑关联 ID 为正数、用途白名单、状态白名单、尝试次数 0～5、摘要格式、`USED` 的 `used_time` 位于发送至过期区间且其他状态为空
- 兼容修改：`chk_sys_login_log_type` 从 `PASSWORD/ADMIN_PASSWORD` 增加 `EMAIL_CODE`，在同一条 `ALTER TABLE` 中 DROP 和 ADD，不得修改 V006
- 首次初始化：V011 发布后通过 Git 忽略的环境配置受控执行；按摘要不存在才插入，使用 `BusinessIdGenerator` 生成 ID，不得重置既有使用次数
- 验证：冷却重复发送、错误尝试上限、过期、一次性消费、邮箱唯一竞争、邀请码最后一次竞争、注册回滚、字符集和重复 migrate

## Verification

- 领域/应用测试：生成格式、摘要稳定与隔离、重复发送、未知账号、状态/角色、失败次数、一次性消费、注册重放和事务回滚。
- API 测试：CSRF、DTO、注册/登录成功响应、Cookie、错误码、OpenAPI 和敏感字段扫描。
- 适配器测试：Redis 原子冷却、失败关闭、SMTP 明确失败/结果未知映射。
- `backend/mvnw.cmd verify`、OpenSpec 严格校验和 `git diff --check`。
- A 分配迁移并授权后，补空 MySQL 8.4、真实 Redis 和真实测试邮箱冒烟。
