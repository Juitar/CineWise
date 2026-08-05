## Context

现有 `password-login-and-session` 已提供账号查询、JWT Cookie、CSRF、当前用户和登录审计，但明确排除了验证码和注册。认证设计已经确定验证码、邀请码、邀请码使用记录的表字段，以及注册接口、事务和错误码；当前缺少对应迁移版本、首个邀请码数据迁移和正式 SMTP 环境配置。

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

邮件端口返回 `SENT/FAILED/UNKNOWN`。SMTP Adapter 使用 Spring Boot Mail 的 `JavaMailSender` 发送纯文本认证模板，主题和正文固定，不接受前端模板；地址、认证和超时由 `spring.mail.*` 环境配置提供。未启用 SMTP 时使用失败关闭 Adapter，接口返回 `301001`。代码和测试不打印验证码。

### 7. 数据迁移由 A 分配版本

`sys_email_verify_code` 使用设计已确认的 11 个字段，补充 `status` 仅允许 `UNUSED/USED/INVALID`、`attempt_count` 在 0～5、摘要为 64 位小写十六进制、使用时间与状态一致的 CHECK。索引为 `idx_verify_lookup(email,purpose,status,expire_time)` 和用于短生命周期清理的 `idx_verify_expire(expire_time)`。V006 的 `chk_sys_login_log_type` 当前只允许 `PASSWORD/ADMIN_PASSWORD`，新迁移必须在同一条 `ALTER TABLE` 中删除并重建该约束，加入 `EMAIL_CODE`；不得修改 V006。C 提交字段与约束申请；A 分配 V009 之后的实际版本、生成或审核 SQL，并决定是否授权空 MySQL 8.4 验证。

### 8. 注册事务同时消费验证码和邀请码

`RegistrationTransaction` 先检查相同 `clientRequestId` 的既有邀请码使用记录。若存在，只有邮箱、BCrypt 密码、邀请码摘要、隐私政策版本和非空昵称都与原账号匹配时才返回原用户；请求 ID 不能单独换取会话。

新注册在同一个事务中执行：规范化并检查邮箱未占用 → 消费 `REGISTER` 验证码 → 读取并条件扣减有效邀请码 → 生成雪花用户 ID → BCrypt 保存密码 → 创建 `USER/NORMAL/emailVerified=true` 账号 → 写邀请码使用记录。任一步失败都回滚验证码、邀请码和账号写入。数据库邮箱唯一键、邀请码 `version` 条件更新以及 `client_request_id/user_id` 唯一键处理并发。

### 9. 隐私政策和昵称由服务端复核

新增 `cinewise.auth.registration.current-privacy-policy-version`，请求必须显式传 `privacyAccepted=true` 且版本完全相等，否则返回 `201008`。昵称可空；有值时去除首尾空白且不超过 64 字符，空值使用 `用户` 加用户 ID 末 6 位生成，不从邮箱推导昵称。

### 10. 邀请码使用独立 HMAC 密钥

邀请码不保存明文，使用必填 `AUTH_INVITE_HASH_SECRET` 计算 HMAC-SHA-256 后按唯一索引查询。摘要同样使用 `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` 和小写十六进制 CHECK。首个培训邀请码由 A 在结构迁移后的独立数据迁移写入预计算摘要、有效期和次数；正式数据迁移使用“摘要不存在才插入”，不得用会重置 `used_count` 的 `ON DUPLICATE KEY UPDATE`。代码、OpenSpec、日志和 Git 不保存邀请码明文或摘要密钥。

## Risks / Trade-offs

- [迁移未发布] → 代码不能部署到会调用验证码或注册接口的环境；先完成不依赖真实表的单元、Controller Mock 和静态检查，真实 MySQL 测试保持未完成。
- [SMTP 未配置] → 默认失败关闭并返回 `301001`；真实收信只在 C/A 配置测试域名、发件人和密钥后验证。
- [外部发送后进程中断] → 结果按未知处理，保留验证码和冷却，不自动重发；用户可检查邮箱并在窗口结束后主动请求。
- [固定窗口 IP 限流误伤共享出口] → 上限配置保守，指标只记录摘要和计数；达到上限返回 `101002`，不泄露具体邮箱。

## Migration Request

- OpenSpec change：`openspec/changes/email-code-authentication/`
- 领域 Owner：C
- 涉及表：新增 `sys_email_verify_code`、`sys_registration_invite`、`sys_registration_invite_use`；向前修改 `sys_login_log` 的 `chk_sys_login_log_type`
- 申请版本：等待 A 分配；不得自行使用 V010
- 验证码字段：`id,email,purpose,code_hash,status,send_time,expire_time,used_time,attempt_count,create_time,update_time`
- 邀请码字段：`id,code_hash,status,max_uses,used_count,valid_from,expire_time,version,create_time,update_time`
- 使用记录字段：`id,invite_id,user_id,client_request_id,used_at,create_time`
- 摘要列：验证码和邀请码的 `code_hash` 均为 `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin`，CHECK 只允许 64 位小写十六进制
- 索引：按认证总系分 T02、T03、T04；补充 `idx_verify_expire(expire_time)`；邮箱、邀请码摘要、用户和 clientRequestId 的唯一/查询规则不得删减
- CHECK：用途白名单、状态白名单、尝试次数 0～5、摘要格式、`USED` 必须有 `used_time` 且其他状态为空
- 兼容修改：`chk_sys_login_log_type` 从 `PASSWORD/ADMIN_PASSWORD` 增加 `EMAIL_CODE`，在同一条 `ALTER TABLE` 中 DROP 和 ADD，不得修改 V006
- 数据迁移：结构迁移之后另建首个培训邀请码数据迁移，只保存预计算摘要，不保存明文；使用“摘要不存在才插入”，不得重置既有使用次数
- 验证：冷却重复发送、错误尝试上限、过期、一次性消费、邮箱唯一竞争、邀请码最后一次竞争、注册回滚、字符集和重复 migrate

## Verification

- 领域/应用测试：生成格式、摘要稳定与隔离、重复发送、未知账号、状态/角色、失败次数、一次性消费、注册重放和事务回滚。
- API 测试：CSRF、DTO、注册/登录成功响应、Cookie、错误码、OpenAPI 和敏感字段扫描。
- 适配器测试：Redis 原子冷却、失败关闭、SMTP 明确失败/结果未知映射。
- `backend/mvnw.cmd verify`、OpenSpec 严格校验和 `git diff --check`。
- A 分配迁移并授权后，补空 MySQL 8.4、真实 Redis 和真实测试邮箱冒烟。
