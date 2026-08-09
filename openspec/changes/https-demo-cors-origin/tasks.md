## 1. 配置和实现

- [x] 1.1 将 CORS 来源改为 `CINEWISE_CORS_ALLOWED_ORIGINS` 环境配置并保留本地默认值。
- [x] 1.2 在 Compose 和 `.env.example` 中补充该配置及 HTTPS Demo 设置说明。

## 2. 测试和验证

- [x] 2.1 覆盖已配置 HTTPS 来源允许访问。
- [x] 2.2 覆盖未配置来源继续拒绝。
- [ ] 2.3 A 在公网 HTTPS Demo 重建容器并验证浏览器登录不再返回 403。
