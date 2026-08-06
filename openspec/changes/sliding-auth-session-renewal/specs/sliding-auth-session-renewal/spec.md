# Sliding Authentication Session Renewal Specification

## ADDED Requirements

### Requirement: 续期配置必须安全且关系正确

系统 SHALL 配置普通有效期、续期阈值和最长连续登录时间，默认分别为 30 分钟、10 分钟和 8 小时。三项 SHALL 大于零，续期阈值 SHALL 小于普通有效期，最长连续登录时间 SHALL 不小于普通有效期。

#### Scenario: 默认配置启动

- **WHEN** 未覆盖续期环境变量且 JWT 密钥等现有配置合法
- **THEN** 应用使用 30 分钟、10 分钟和 8 小时参数正常启动

#### Scenario: 配置关系错误

- **WHEN** 任一时间非正，或阈值不小于普通有效期，或最长时间小于普通有效期
- **THEN** 应用启动失败并指出时间配置关系，不输出任何密钥

### Requirement: 首次签发记录不可重置的会话起点

系统 SHALL 在密码登录、邮箱验证码登录、管理员登录和注册成功时签发 30 分钟 JWT，保留 `sub/role/tokenVersion/iat/exp/jti` 并增加内部 `sessionStartedAt`，其值等于首次签发时间。JWT 不得进入响应体。

#### Scenario: 首次登录签发

- **WHEN** 任一正式登录或注册流程成功
- **THEN** JWT 的 `exp=iat+30分钟`、`sessionStartedAt=iat`，Cookie Max-Age 与实际剩余有效期一致

### Requirement: 有效操作在阈值内自动续期

系统 SHALL 在合法 Cookie、正常账号、角色和 `tokenVersion` 一致、剩余时间不超过阈值、未到最长时间且非 OPTIONS 请求时续签。新到期时间 SHALL 为当前时间后普通有效期和原会话起点后最长时间的较早值。

#### Scenario: 剩余时间大于阈值

- **WHEN** 有效 JWT 剩余时间大于 10 分钟
- **THEN** 当前请求正常认证且响应不新增认证 `Set-Cookie`

#### Scenario: 剩余时间正好或小于阈值

- **WHEN** 有效 JWT 剩余时间正好 10 分钟或更少且未接近绝对上限
- **THEN** 响应在下游处理前写入到期时间为当前时间后 30 分钟的新 JWT Cookie

#### Scenario: 临近或达到最长时间

- **WHEN** 续期后的普通到期时间超过原会话起点后 8 小时
- **THEN** 新到期时间被限制在 8 小时上限；当前时间已达到上限时不续签，原 JWT 到期后按匿名处理

### Requirement: 续签保持身份并生成新 jti

系统 SHALL 在续签中保留原始 `sessionStartedAt`、用户 ID、角色和 `tokenVersion`，更新 `iat/exp` 并生成新的 `jti`。续签 SHALL 不推进 `tokenVersion` 或写数据库。

#### Scenario: 单次续签声明

- **WHEN** 有效请求触发续签
- **THEN** 新旧 JWT 的 `sub/role/tokenVersion/sessionStartedAt` 相同，`jti` 不同

#### Scenario: 两个并发请求续签

- **WHEN** 两个请求用同一临期 JWT 同时进入
- **THEN** 两个新 JWT 均可通过验证，具有不同 `jti`，不产生数据库写入竞争

### Requirement: 认证操作和预检不得由旧 Cookie 续签

系统 SHALL 排除密码登录、邮箱验证码登录、管理员登录、注册、发送验证码、密码重置、登出、获取 CSRF Token和 OPTIONS；`/auth/me` 及普通已认证 GET/POST SHALL 可以续签。

#### Scenario: 排除认证接口

- **WHEN** 临期旧 Cookie 请求任一排除接口
- **THEN** 过滤器不因该旧 Cookie 写续签 Cookie；接口自身成功时只执行原有正式行为

#### Scenario: 当前用户和普通接口

- **WHEN** 临期有效 Cookie 请求 `/api/v1/auth/me` 或普通受保护 GET/POST
- **THEN** 本次请求保持认证并返回续签 Cookie

#### Scenario: CORS 预检

- **WHEN** OPTIONS 请求携带临期有效 Cookie
- **THEN** 系统不续签且继续按现有 CORS 规则处理

### Requirement: 无效身份不得续签且正式失效机制优先

系统 SHALL 在续签前验证 JWT、账号状态、角色和 `tokenVersion`。非法/过期 JWT、账号停用、角色变化或版本不一致 SHALL 不认证、不续签。登出和密码重置推进版本后，之前签发或续签的全部 JWT SHALL 立即失效。

#### Scenario: 身份或账号不一致

- **WHEN** JWT 非法、过期，或数据库账号停用、角色/`tokenVersion` 与 JWT 不一致
- **THEN** 不写续签 Cookie，受保护请求按匿名返回 401/403

#### Scenario: 密码重置后的续签 Token

- **WHEN** 用户持有重置前续签得到的 JWT，随后密码重置成功推进 `tokenVersion`
- **THEN** 该 JWT 再次请求时不认证、不续签并返回 401

### Requirement: Cookie 写入和续签失败必须安全

系统 SHALL 在响应提交前一次性写入完整新 Cookie，Max-Age 与 JWT 实际剩余有效期一致。续签失败 SHALL 不破坏已验证身份、不写半成品 Cookie，只记录脱敏告警。

#### Scenario: POST SSE 建立连接

- **WHEN** 临期有效 Cookie 发起已认证 POST SSE 请求
- **THEN** 过滤器在建立流之前写续签 Cookie；后续心跳不续期，断线重连携带浏览器保存的新 Cookie

#### Scenario: 签发失败

- **WHEN** Token 服务在续签时抛出异常
- **THEN** 当前请求继续使用已验证身份，响应不含续签 Cookie，日志和响应不出现 JWT、Cookie、完整声明或用户完整信息
