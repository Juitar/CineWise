## Context

Spring Security 的 CORS 过滤器会先检查浏览器的 `Origin`。当前配置只列出 `http://localhost:8000` 和 `http://localhost:5173`，因此公网 HTTPS 页面发出的登录、CSRF 和其他 API 请求会在 Controller 前返回 403。

## Decision

使用 `CINEWISE_CORS_ALLOWED_ORIGINS` 作为逗号分隔的环境配置，并由 Spring Boot 绑定到现有 `CorsProperties.allowedOrigins`。默认值只保留本地开发来源；演示服务器自行填写实际 HTTPS Origin。`allowCredentials=true` 保持不变，禁止使用 `*`。

## Deployment

服务器 `.env` 设置：

```env
AUTH_COOKIE_SECURE=true
CINEWISE_CORS_ALLOWED_ORIGINS=https://47.97.45.119
```

修改后执行 `docker compose up -d --force-recreate backend frontend`，并检查浏览器登录顺序：`GET /api/v1/auth/csrf`、`POST /api/v1/auth/login/password`、`GET /api/v1/auth/me`。
