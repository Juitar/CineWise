# 设计

## 1. 接口依据

认证设计 V1.9、前端设计和后端交接矩阵均确定：验证码发送继续使用 `POST /api/v1/auth/email-codes`，用途增加 `RESET_PASSWORD`；重置接口为 `POST /api/v1/auth/password/reset`，请求字段为 `clientRequestId/email/code/newPassword`，成功数据只返回 `changed=true`。

后端总设计旧接口详情仍列出响应 `tokenVersion`，但同一文档的认证 REST 规则、后端交接矩阵、认证设计 V1.9 和前端设计均禁止向浏览器返回内部 `tokenVersion`。本 change 采用只返回 `changed=true` 的较新且更安全定义。

## 2. 验证码发送与账号隐私

`VerificationPurpose` 增加 `RESET_PASSWORD`。发送服务仍先做邮箱/IP/用途限流；仅当账号存在、状态正常、邮箱已验证且角色为 `USER` 时创建验证码并投递。其他账号状态和不存在邮箱都返回与正常请求相同的冷却时间和有效期，不创建验证码、不调用 SMTP，也不通过响应或日志说明原因。

验证码邮件继续复用当前 `VerificationEmailSender` 和同一 `JavaMailSender`。固定模板根据用途显示“重置密码”，不增加第二套 SMTP 客户端。

## 3. 密码重置事务

`PasswordResetApplicationService` 完成请求级邮箱、密码和请求标识校验；`PasswordResetTransaction` 使用单个本地事务：

1. 按标准化邮箱读取账号并校验状态、邮箱验证和 `USER` 角色；对外统一为验证码无效，避免用重置接口探测账号。
2. 用 `VerificationCodeVerifier` 按 `RESET_PASSWORD` 校验用途、有效期、状态、尝试次数和 HMAC 摘要，并条件消费。
3. BCrypt 编码新密码，按账号当前 `tokenVersion` 和版本条件更新 `password_hash`、递增 `token_version/version`。
4. 任一步失败都回滚密码与验证码消费；错误验证码的尝试次数继续通过现有独立事务记录。

数据库条件更新裁决并发；同一验证码最多一个事务成功。成功响应丢失时前端不自动重新发送验证码或密码，清除敏感输入并提示用户用新密码登录。重复提交已消费验证码返回 `201002`。

## 4. 迁移处理

已发布 V011 的 `chk_verify_purpose` 只允许 `REGISTER/LOGIN`，真实 MySQL 保存 `RESET_PASSWORD` 前必须新增向前迁移，删除并重建该 CHECK，加入 `RESET_PASSWORD`。C 只提交迁移申请，并在 `D:\tmp\cinewise-password-reset-migration-review\` 准备未编号 SQL 草案；A 分配版本、审核和执行。测试数据库使用测试专用 schema 初始化，不修改 V011。

## 5. 前端

路由 `/password/reset` 为公开页面。页面只负责组合，网络请求放在 `modules/auth/api.ts`，写状态和冷却放在 `usePasswordReset`，所有 REST 经过 `apiRequest<T>()`。

邮箱、验证码、新密码和确认密码只保存在组件内存。客户端校验邮箱、6 位验证码、现有 8～20 位且同时包含字母和数字的密码规则及两次密码一致。发送和提交遇到网络/5xx 结果未知时不自动重发；重置结果未知时清空密码和验证码并引导用户用新密码登录。明确成功后同样清空敏感输入并返回 `/login`，不调用登录接口。

页面复用认证页视觉语言，`<1024px` 使用移动单列，`>=1024px` 使用桌面卡片布局，触控目标不小于 44px。

## 6. 测试与真实环境

- 后端覆盖正常、账号隐私、用途不匹配、错误/过期/已用/超限验证码、弱密码、并发一次成功、事务回滚和旧 JWT 失效。
- 前端覆盖字段校验、冷却、401/403/429/5xx/网络失败、结果未知不重发、成功清理和浏览器存储/URL 无敏感字段。
- 真实验证只使用测试账号、测试 Redis Key 和测试邮箱；缺少配置时保持任务未勾选并明确责任人。
