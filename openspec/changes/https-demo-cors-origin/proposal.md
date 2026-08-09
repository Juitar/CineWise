## Why

HTTPS Demo 页面 `https://47.97.45.119` 的登录请求带有该页面的 `Origin`，后端当前 CORS 白名单只有本地 HTTP 地址，Spring Security 在登录 Controller 之前返回 HTTP 403。

## What Changes

- 将 CORS 来源白名单改为环境变量 `CINEWISE_CORS_ALLOWED_ORIGINS`，保留本地开发默认值。
- 允许演示服务器显式配置 `https://47.97.45.119`，不使用 `*`，继续允许 Cookie 凭据。
- 增加允许配置 HTTPS 来源和拒绝未配置来源的测试。

## Non-Goals

- 不放宽 CSRF、Cookie、登录权限或认证错误处理。
- 不允许任意来源，不把真实服务器地址写入部署密钥或账号配置。
- 不改变登录接口字段和前端请求逻辑。

## Owner

- C：后端 CORS 配置和测试。
- A：演示服务器 `.env` 设置、容器重建和公网登录冒烟。

## Acceptance

- 配置 `CINEWISE_CORS_ALLOWED_ORIGINS=https://47.97.45.119` 后，浏览器登录不再因 CORS 返回 403。
- 未配置来源仍被拒绝。
- `AUTH_COOKIE_SECURE=true` 时，登录 Cookie 继续使用 Secure、HttpOnly、SameSite=Lax。
