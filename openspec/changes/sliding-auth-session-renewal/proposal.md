# 登录会话操作续期

## 背景

现有认证 JWT 固定 30 分钟到期，用户持续操作时不会续期，`AuthCookieManager` 也始终写固定 30 分钟 `Max-Age`。需要由 C 在统一认证过滤器中增加受 8 小时上限约束的操作续期，浏览器只通过 `Set-Cookie` 接收新 JWT。

## 范围

- C 为认证配置增加续期阈值和最长连续登录时间，并校验三项时间关系。
- C 为首次登录、邮箱验证码登录、管理员登录和注册签发的 JWT 增加内部会话起始声明。
- C 调整 Token 服务、`JwtCookieAuthenticationFilter` 和 `AuthCookieManager`，在合法请求进入且符合阈值时续签，并按新 JWT 实际剩余时间写 Cookie。
- C 覆盖普通已认证 GET/POST、`/auth/me`、OPTIONS、排除认证接口、并发、失败和 POST SSE 建连前续期测试。
- C 验证密码重置推进 `tokenVersion` 后，续签前生成的 JWT 同样立即失效。

## 非范围

- 不新增 `/refresh` 接口，不要求前端定时请求或读取/保存 JWT。
- 不修改 Agent 前端、B 的 Agent 业务、首页、影片、影院或 A/D 模块。
- 不在 SSE 持续期间中途改 Cookie，不因续期重发消息、确认或工具调用。
- 不修改密码重置和公共邮件 change 的任务归属或完成状态。

## Owner 与影响

- Owner：C（认证后端）。
- A/B/D：无需修改业务模块；已认证 REST 和 POST SSE 自动经过统一过滤器。
- 部署负责人：同步三个环境变量并验证 HTTPS 代理保留续签 `Set-Cookie`。

## 验收结果

默认 JWT 有效 30 分钟，剩余不超过 10 分钟的有效操作自动续签到“当前时间后 30 分钟”和“原会话起点后 8 小时”的较早值；最长时间到达后不再续签。新 JWT 保留身份、角色、`tokenVersion` 和原会话起点，使用新 `jti`；浏览器仅通过安全 Cookie 接收。

