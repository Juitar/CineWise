# cors-origin Specification

## Purpose
TBD - created by archiving change https-demo-cors-origin. Update Purpose after archive.
## Requirements
### Requirement: CORS 来源必须来自显式配置

系统 SHALL 从 `CINEWISE_CORS_ALLOWED_ORIGINS` 读取逗号分隔的来源白名单；未配置时 SHALL 只允许本地开发来源。系统 SHALL 保持 `allowCredentials=true`，不得使用 `*`。

#### Scenario: HTTPS Demo 登录

- **WHEN** 页面来源为 `https://47.97.45.119` 且该来源已配置
- **THEN** `/api/v1/auth/csrf` 和 `/api/v1/auth/login/password` 不因 CORS 被拒绝，并继续执行 CSRF 校验

#### Scenario: 未配置来源被拒绝

- **WHEN** 请求来源不在白名单中
- **THEN** Spring Security 返回拒绝结果，不进入登录 Controller

