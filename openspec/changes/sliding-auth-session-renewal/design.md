# 设计

## 1. 配置

`AuthProperties` 增加：

- `accessTokenTtl`：默认 30 分钟；
- `renewalThreshold`：默认 10 分钟；
- `absoluteSessionTtl`：默认 8 小时。

三项必须大于零，且 `renewalThreshold < accessTokenTtl`、`absoluteSessionTtl >= accessTokenTtl`。Bean Validation 在应用启动绑定阶段失败，错误只说明配置关系，不包含 JWT 密钥。

环境变量为 `AUTH_ACCESS_TOKEN_TTL`、`AUTH_RENEWAL_THRESHOLD`、`AUTH_ABSOLUTE_SESSION_TTL`。同步 `application.yml`、`.env.example` 和配置绑定测试。

## 2. Token 类型与声明

`AccessTokenService.issue(AuthUser)` 首次签发，内部令牌结果包含 JWT 字符串和实际 `expiresAt`；`renew(AuthUser, sessionStartedAt)` 保留原始会话起点续签。JWT 声明保持 `sub/role/tokenVersion/iat/exp/jti`，新增与现有 camelCase 风格一致的 `sessionStartedAt`。

首次签发时 `sessionStartedAt=issuedAt`。续签到期时间为 `min(now + accessTokenTtl, sessionStartedAt + absoluteSessionTtl)`；不晚于当前时间时返回不续签。每次签发生成新 `jti`，续签不改数据库和 `tokenVersion`。

为兼容升级前仍在有效期内的旧 JWT，缺少 `sessionStartedAt` 时仍按现有账号、角色和 `tokenVersion` 规则认证，但不续签；它按原 `exp` 到期。

## 3. 过滤器顺序

`JwtCookieAuthenticationFilter` 固定顺序：

1. 读取 Cookie 并解码 JWT。
2. 回查账号存在、状态正常，核对角色和 `tokenVersion`。
3. 建立 Spring Security 身份。
4. 请求符合续期范围时，比较 `exp-now <= renewalThreshold` 和绝对上限。
5. Token 服务成功返回新令牌后，过滤器在调用下游 Filter/Controller/SSE 前写 `Set-Cookie`。
6. 继续当前请求。

续签失败只记录不含 JWT、Cookie、声明原文和用户完整信息的安全告警，保留本次已经验证成功的身份，不写半成品 Cookie。

## 4. 触发和排除

只有合法 Cookie、正常账号、角色/`tokenVersion` 一致、剩余时间不超过阈值、未到绝对上限且方法不是 OPTIONS 时续签。`/api/v1/auth/me` 和普通已认证 REST GET/POST 可以续签。

以下路径精确排除旧 Cookie 续签：

- `/api/v1/auth/login/password`
- `/api/v1/auth/login/email`
- `/api/v1/admin/auth/login`
- `/api/v1/auth/register`
- `/api/v1/auth/email-codes`
- `/api/v1/auth/password/reset`
- `/api/v1/auth/logout`
- `/api/v1/auth/csrf`

POST SSE 不在排除列表；满足条件时在下游建立流之前写 Cookie。心跳不是新请求，不续期；断线重连由浏览器自动携带最后收到的 Cookie。

## 5. Cookie

`AuthCookieManager.writeAccessToken` 接收类型化令牌，根据注入 `Clock` 计算 `max(Duration.ZERO, expiresAt-now)`，按完整秒写 `Max-Age`。临近 8 小时上限时只写实际剩余时间，不固定写 1800 秒。Cookie 继续使用 `HttpOnly`、配置化 `Secure`、`SameSite` 和 `Path=/`；响应体不包含 JWT。

## 6. 并发、失效与测试

并发请求只读取账号并各自签发不同 `jti`，不写数据库；任一新 JWT 都携带相同会话起点、身份、角色和 `tokenVersion`，浏览器最后保存任意一个都可继续使用。

登出、密码重置、账号停用或其他 `tokenVersion` 变化后，过滤器在续签前的数据库核对失败，因此旧 JWT 不认证也不续签。

使用固定 `Clock` 覆盖 10 分钟边界、8 小时上限、排除路径、GET/POST、OPTIONS、并发、Cookie Max-Age、失败日志和 POST SSE 响应提交前 Cookie。认证集成测试只验证过滤器行为，不修改 Agent 业务。

