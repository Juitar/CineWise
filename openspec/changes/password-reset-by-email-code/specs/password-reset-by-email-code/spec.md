# Password Reset by Email Code Specification

## ADDED Requirements

### Requirement: 密码重置验证码发送不暴露账号存在性

系统 SHALL 接受 `purpose=RESET_PASSWORD` 的邮箱验证码请求，复用现有验证码生成、HMAC 摘要、有效期、冷却、尝试上限和一次性消费规则。只有存在、正常、邮箱已验证的普通用户才实际创建并投递验证码；其他情况 SHALL 返回与正常发送相同结构和状态，不说明账号是否存在。

#### Scenario: 已注册普通用户发送重置验证码

- **WHEN** 正常且邮箱已验证的普通用户邮箱请求 `RESET_PASSWORD` 验证码
- **THEN** 系统创建用途为 `RESET_PASSWORD` 的摘要记录并通过现有 SMTP 能力投递，响应只包含冷却和有效期

#### Scenario: 未注册邮箱请求重置验证码

- **WHEN** 未注册邮箱请求 `RESET_PASSWORD` 验证码
- **THEN** 系统返回与正常请求相同的成功结构，不创建验证码、不调用 SMTP，响应和日志不说明邮箱未注册

#### Scenario: 冷却或邮件结果未知

- **WHEN** 同邮箱仍在冷却期，或 SMTP 发送结果未知
- **THEN** 系统不自动再次投递；冷却请求返回剩余时间，结果未知按现有邮件不可用规则返回且保留原验证码和冷却状态

### Requirement: 密码重置校验全部安全规则

系统 SHALL 通过 `POST /api/v1/auth/password/reset` 接收 `clientRequestId/email/code/newPassword`，校验邮箱格式、`RESET_PASSWORD` 用途、验证码有效性和现有密码规则。成功数据 SHALL 仅包含 `changed=true`，不得返回 `tokenVersion`、邮箱、验证码或 Cookie。

#### Scenario: 正常重置密码

- **WHEN** 正常普通用户提交有效 `RESET_PASSWORD` 验证码和合规新密码
- **THEN** 系统返回 `changed=true`，新密码可以登录，原密码不能再登录

#### Scenario: 验证码异常

- **WHEN** 验证码错误、过期、已使用、用途不匹配或达到尝试上限
- **THEN** 系统返回 `201002`，不修改密码，不说明验证码内部状态或账号存在性

#### Scenario: 新密码不合规

- **WHEN** 新密码不满足 8～20 位且同时包含字母和数字的现有规则
- **THEN** 系统返回 `101001`，不消费验证码、不修改密码

### Requirement: 密码与验证码在同一事务提交

系统 SHALL 在同一数据库事务中消费验证码、更新 BCrypt 密码摘要并递增 `tokenVersion`，数据库条件更新 SHALL 裁决重复或并发提交。

#### Scenario: 同一验证码并发提交

- **WHEN** 两个请求同时提交同一有效验证码
- **THEN** 只有一个请求成功，另一个返回 `201002`，最终只有一个新密码生效

#### Scenario: 事务中途失败

- **WHEN** 验证码消费后密码更新失败或事务提交失败
- **THEN** 验证码仍可按原状态使用，密码摘要和 `tokenVersion` 均不变化

#### Scenario: 重置后旧会话访问

- **WHEN** 重置成功前签发的 Cookie/JWT 再次访问受保护接口
- **THEN** 服务端因 `tokenVersion` 不一致返回 401，旧会话不能继续写业务数据

### Requirement: 密码重置页面不保存或重复发送敏感信息

前端 SHALL 提供公开路由 `/password/reset` 和登录页“忘记密码”入口，通过认证模块 API、Hook 和 `apiRequest<T>()` 完成发送和重置。邮箱、验证码、新密码和确认密码仅保存在组件内存，不进入 URL、浏览器持久化存储、日志或错误上报。

#### Scenario: PC 和移动端正常重置

- **WHEN** 用户在桌面或移动布局输入合法邮箱、验证码和两次一致的新密码并提交成功
- **THEN** 页面清除验证码和密码，跳转 `/login`，不使用旧凭据自动登录

#### Scenario: 两次密码不一致

- **WHEN** 新密码和确认密码不同
- **THEN** 页面显示字段错误，不调用重置接口

#### Scenario: 401、403、429、5xx 或网络失败

- **WHEN** 发送或重置请求返回认证/限流/服务错误，或请求结果未知
- **THEN** 页面显示对应安全提示；网络或结果未知时不自动重发验证码或密码，并清除密码和验证码

### Requirement: 真实环境验证必须如实记录

系统 SHALL 只在当前环境提供真实 Redis、SMTP、测试邮箱和测试账号时执行受控验证，不得把 Mock 或配置绑定测试记为真实投递。

#### Scenario: 环境具备真实配置

- **WHEN** 当前环境存在授权的真实 Redis、SMTP、测试邮箱和测试账号
- **THEN** C 使用本次专用标识验证投递、冷却、重置、重复请求和敏感信息，并只清理本次数据

#### Scenario: 环境缺少真实配置

- **WHEN** 任一必需服务或变量缺失
- **THEN** 对应真实验证任务保持未勾选，并记录缺少项和提供责任人
