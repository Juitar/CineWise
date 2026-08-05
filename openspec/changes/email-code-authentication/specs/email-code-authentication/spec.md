## Purpose

让游客安全获取邮箱验证码，使用一次性 `LOGIN` 验证码建立用户会话，并通过 `REGISTER` 验证码和邀请码完成注册。

## ADDED Requirements

### Requirement: 验证码发送不泄露账号是否存在

系统 SHALL 通过 `POST /api/v1/auth/email-codes` 接收 `email` 和 `purpose`。本次只允许 `REGISTER`、`LOGIN`；`LOGIN` 邮箱不存在或 `REGISTER` 邮箱已经注册时 SHALL 返回与正常受理相同的 `cooldownSeconds/expiresInSeconds`，但不创建验证码或发送邮件。

#### Scenario: 为已有普通用户发送登录验证码

- **WHEN** 游客提交合法邮箱和 `purpose=LOGIN`，账号存在、正常且角色为 `USER`
- **THEN** 系统受理一次验证码投递并返回 60 秒冷却、300 秒有效期，不在响应或日志中返回验证码

#### Scenario: 登录邮箱不存在

- **WHEN** 游客为不存在的邮箱请求 `LOGIN` 验证码
- **THEN** 系统返回相同成功数据但不创建验证码、不发送邮件，调用方无法据此确认邮箱是否存在

#### Scenario: 注册邮箱已经存在

- **WHEN** 游客为已经注册的邮箱请求 `REGISTER` 验证码
- **THEN** 系统返回相同成功数据但不创建验证码、不发送邮件

#### Scenario: 不支持的用途

- **WHEN** 请求用途缺失、无法识别或为本次未实现的 `RESET_PASSWORD`
- **THEN** 系统返回 HTTP 400 和 `101001`，不创建记录、不发送邮件

### Requirement: 冷却期间不得重复投递

系统 SHALL 按规范化邮箱摘要、IP 摘要和用途限流。相同邮箱和用途在 60 秒内重复请求 SHALL 返回剩余冷却时间，不得创建第二个有效验证码、重复投递或延长原验证码有效期。Redis 不可用时 SHALL 失败关闭。

#### Scenario: 冷却窗口内重复发送

- **WHEN** 同一邮箱、同一用途在冷却窗口内再次请求
- **THEN** 系统返回原窗口剩余秒数，数据库和邮件 Provider 都没有第二次写入

#### Scenario: Redis 不可用

- **WHEN** 系统无法可靠判断验证码发送频率
- **THEN** 系统返回服务暂不可用，不创建验证码、不发送邮件，也不绕过限流

### Requirement: 验证码只保存摘要且一次性消费

系统 SHALL 生成 6 位数字验证码，只保存使用独立必填密钥计算的 HMAC-SHA-256 摘要。摘要 SHALL 为 64 位小写十六进制并以区分大小写的 ASCII 固定列保存；验证码 SHALL 在 5 分钟后过期，`attempt_count` 只允许 0～5，成功消费或达到上限后立即失效。同一验证码并发消费 SHALL 只有一个请求成功。

#### Scenario: 正确验证码首次消费

- **WHEN** `LOGIN` 验证码未使用、未过期、尝试次数未超限且摘要匹配
- **THEN** 条件更新把记录改为 `USED` 并写入 `used_time`，影响行数必须为 1

#### Scenario: 验证码错误

- **WHEN** 验证码摘要不匹配且仍有剩余次数
- **THEN** 系统原子增加 `attempt_count` 并返回 HTTP 422 和 `201002`，不建立会话

#### Scenario: 达到尝试上限

- **WHEN** 第 5 次校验仍不匹配
- **THEN** 系统增加尝试次数并把记录改为 `INVALID`，后续即使验证码正确也不能消费

#### Scenario: 并发消费

- **WHEN** 两个请求同时提交同一个有效验证码
- **THEN** 最多一个条件更新成功并建立会话，另一个返回 `201002`

### Requirement: 邮箱验证码登录复用既有会话规则

系统 SHALL 通过 `POST /api/v1/auth/login/email` 接收 `clientRequestId/email/code`，只允许正常 `USER` 账号使用 `LOGIN` 验证码。成功后 SHALL 复用现有 JWT Cookie、CSRF 更新、`CurrentUserResponse` 和登录审计；管理员继续使用独立密码入口。

#### Scenario: 普通用户验证码登录成功

- **WHEN** 普通用户提交有效 `LOGIN` 验证码
- **THEN** 系统一次性消费验证码、写入 JWT HttpOnly Cookie、返回同 `/auth/me` 一致的 `CurrentUserResponse`，并记录 `EMAIL_CODE` 成功审计

#### Scenario: 管理员尝试验证码登录

- **WHEN** `ADMIN` 账号向用户验证码登录入口提交验证码
- **THEN** 系统使用统一凭据错误响应拒绝，不允许绕过管理员密码入口

#### Scenario: 验证码无效或已使用

- **WHEN** 验证码错误、过期、达到尝试上限或已经消费
- **THEN** 系统返回 HTTP 422 和 `201002`，不签发 Token，并记录不含邮箱和验证码的失败审计

### Requirement: 邮件发送失败不得伪装成功

系统 SHALL 在数据库登记验证码后调用邮件端口。Provider 明确失败时 SHALL 使验证码失效并返回 `301001`；结果未知时 SHALL 保留验证码和原冷却窗口但返回 `301001`，不得自动重发。SMTP 密钥、完整邮箱和验证码 SHALL NOT 出现在普通日志、响应或监控标签中。

#### Scenario: Provider 明确拒绝

- **WHEN** SMTP Provider 明确拒绝验证码邮件
- **THEN** 系统把本次验证码改为 `INVALID`、返回 HTTP 503 和 `301001`，不自动重发

#### Scenario: Provider 结果未知

- **WHEN** 邮件提交超时或连接中断，无法判断 Provider 是否已受理
- **THEN** 系统保留验证码和冷却窗口、返回 HTTP 503 和 `301001`，不自动重发

### Requirement: 注册请求必须通过服务端规则校验

系统 SHALL 通过 `POST /api/v1/auth/register` 接收 `clientRequestId/email/code/inviteCode/password/privacyPolicyVersion/privacyAccepted` 和可选 `nickname`。密码 SHALL 为 8～20 位且同时包含字母和数字；`privacyAccepted` SHALL 显式为 `true`，隐私政策版本 SHALL 与服务端当前版本完全相等；昵称去除首尾空白后 SHALL 不超过 64 个字符。

#### Scenario: 隐私政策未同意或版本无效

- **WHEN** 请求未显式同意隐私政策，或提交版本与服务端当前版本不同
- **THEN** 系统返回 HTTP 422 和 `201008`，不消费验证码、不扣减邀请码、不创建账号

#### Scenario: 密码不符合规则

- **WHEN** 密码长度不在 8～20 位，或没有同时包含字母和数字
- **THEN** 系统返回 HTTP 400 和 `101001`，不进入注册事务

#### Scenario: 未填写昵称

- **WHEN** 其他注册参数有效且昵称为空
- **THEN** 系统使用 `用户` 加用户 ID 末 6 位生成昵称，不从邮箱推导昵称

### Requirement: 注册必须原子消费验证码和邀请码

系统 SHALL 在一个本地事务中规范化并检查邮箱未占用、消费 `REGISTER` 验证码、条件扣减有效邀请码、创建 `USER/NORMAL/emailVerified=true` 账号并写入邀请码使用记录。任一步失败 SHALL 回滚同一事务中的全部修改；成功后 SHALL 签发与密码登录相同的 JWT Cookie 和当前用户响应。

#### Scenario: 邀请制注册成功

- **WHEN** 邮箱未注册，`REGISTER` 验证码有效，邀请码已启用且在有效期内并有剩余次数，其他字段合法
- **THEN** 系统创建普通用户、记录隐私政策同意和邀请码使用、一次性消费验证码并建立会话

#### Scenario: 邮箱已经注册

- **WHEN** 请求邮箱已经存在，或并发注册触发邮箱唯一约束
- **THEN** 系统返回 HTTP 409 和 `201003`，验证码、邀请码和使用记录均不发生最终修改

#### Scenario: 邀请码不可用

- **WHEN** 邀请码不存在、未启用、未生效、已过期或次数已耗尽
- **THEN** 系统返回 HTTP 422 和 `201004`，验证码消费和其他注册写入全部回滚

#### Scenario: 邀请码最后一次并发扣减

- **WHEN** 多个注册请求并发使用只剩一次的邀请码
- **THEN** 只有一个条件更新成功，其他请求返回 `201004` 且不创建账号

#### Scenario: 注册后半段失败

- **WHEN** 验证码和邀请码已在事务内更新，但账号或使用记录写入失败
- **THEN** 事务回滚验证码、邀请码、账号和使用记录的全部修改

### Requirement: clientRequestId 重放不得成为登录凭据

系统 SHALL 以邀请码使用记录的 `clientRequestId` 查询已完成注册。只有本次邮箱与原账号一致、BCrypt 密码匹配、邀请码摘要对应原 `inviteId`、隐私政策版本一致，且请求中的非空昵称与原昵称一致时，才 SHALL 返回原账号并重新签发 Cookie；任一参数不一致 SHALL 返回 `101001`。

#### Scenario: 相同参数安全重放

- **WHEN** 已完成注册的请求使用相同 `clientRequestId` 和相同注册凭据再次提交
- **THEN** 系统返回原账号并建立会话，不再次消费验证码、扣减邀请码或创建使用记录

#### Scenario: 重放参数不一致

- **WHEN** 相同 `clientRequestId` 被用于不同邮箱、密码、邀请码、隐私版本或非空昵称
- **THEN** 系统返回 HTTP 400 和 `101001`，不返回原账号、不签发 Cookie

### Requirement: 邀请码不得明文持久化或输出

系统 SHALL 使用独立必填的 `AUTH_INVITE_HASH_SECRET` 对邀请码计算 HMAC-SHA-256 摘要，并只按摘要查询。摘要 SHALL 为 64 位小写十六进制并以区分大小写的 ASCII 固定列保存。数据库、响应、普通日志、OpenSpec 和代码 SHALL NOT 保存或输出邀请码明文或摘要密钥。

#### Scenario: 查询邀请码

- **WHEN** 注册服务校验请求中的邀请码
- **THEN** 持久化端口只接收摘要，日志和错误响应不包含邀请码明文

### Requirement: 首个邀请码不得通过 Flyway 种子初始化

系统 SHALL 保持 V011 仅包含结构和前向约束变更。首个培训邀请码 SHALL 在首次开放注册前通过 Git 忽略的环境配置受控初始化；Initializer SHALL 使用服务端雪花 ID 生成器和邀请码 HMAC 组件，在摘要不存在时插入，且重复执行不得新增记录或重置 `used_count`。初始化默认 SHALL 关闭。

#### Scenario: 首次受控初始化

- **WHEN** V011 已发布，初始化开关显式开启，邀请码、次数和有效期配置完整合法，数据库中不存在相同摘要
- **THEN** 系统生成正数雪花 ID、写入一条 `ENABLED` 邀请码记录，且日志不输出明文、密钥或完整摘要

#### Scenario: 重复初始化

- **WHEN** 数据库已经存在相同摘要的邀请码并再次启动初始化
- **THEN** 系统不新增记录、不更新状态、次数或有效期，也不重置 `used_count`

### Requirement: 前端必须提供可用的邀请制注册入口

登录页 SHALL 提供跳转 `/register` 的注册链接。注册页 SHALL 收集邮箱、6 位验证码、邀请码、8～20 位且同时包含字母和数字的密码、确认密码、可选昵称和隐私同意；隐私框默认不勾选。页面 SHALL 通过认证模块发送 `purpose=REGISTER` 的验证码并提交注册，不得直接调用公共请求客户端。

#### Scenario: 游客从登录页完成注册

- **WHEN** 游客从登录页进入注册页，填写合法字段、获取验证码并提交
- **THEN** 前端提交已确认的注册 DTO，成功后查询 `/auth/me` 更新全局身份并跳转首页

#### Scenario: 验证码处于发送冷却期

- **WHEN** 服务端返回剩余冷却秒数
- **THEN** 发送按钮显示倒计时并保持禁用，倒计时结束前不发送第二次请求

#### Scenario: 注册字段或业务校验失败

- **WHEN** 页面字段不合法，或服务端返回 `201002/201003/201004/201008/201009`
- **THEN** 页面显示对应的中文提示、阻止重复提交，并且不根据后端错误文案判断分支

#### Scenario: 注册响应结果未知

- **WHEN** 注册 POST 超时或网络中断
- **THEN** 前端只查询 `/auth/me`；查询仍失败时保持结果未知并允许用户再次查询，不自动重发密码、验证码或邀请码
