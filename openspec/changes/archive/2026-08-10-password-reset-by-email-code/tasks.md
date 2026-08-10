# 任务

- [x] 1.1 C：核对 PRD、认证/前端设计、现有 API 和 V011 约束；验证：记录路径、DTO、错误码和迁移差异。
- [x] 1.2 C：提交向前迁移申请并准备私下 SQL 草案；验证：不修改 V011、不占用版本、不执行共享迁移。
- [x] 2.1 C：增加 `RESET_PASSWORD` 发送用途和固定认证邮件文案；验证：发送服务、SMTP 和账号隐私单测。
- [x] 2.2 C：实现重置请求/响应 DTO、应用服务、事务和账号条件更新；验证：后端定向单元与集成测试。
- [x] 2.3 C：更新安全公开路径、OpenAPI 和错误映射；验证：Controller 集成测试和 OpenAPI 检查。
- [x] 2.4 C：覆盖错误、过期、已用、用途不匹配、超限、并发一次成功、事务回滚和旧 JWT 失效；验证：后端认证测试全部通过。
- [x] 3.1 C：增加前端 DTO、API 和 `usePasswordReset`；验证：API/Hook 单测覆盖错误和结果未知不重发。
- [x] 3.2 C：增加 `/password/reset` 页面、登录入口和响应式样式；验证：页面单测覆盖字段错误、冷却、清理和跳转。
- [x] 3.3 C：补充认证浏览器测试和敏感信息扫描；验证：PC/移动路由及网络请求无 URL/存储泄露。
- [x] 4.1 C：同步 OpenAPI、前端 DTO、Mock 和认证夹具；验证：逐项核对差异。
- [x] 4.2 C：执行后端定向测试、`backend\\mvnw.cmd verify`、`frontend\\pnpm check` 和认证浏览器测试；验证：记录通过、失败、跳过数。
- [x] 4.3 C：执行本 change、`email-code-authentication`、`password-login-and-session` strict 校验及 Git 检查；验证：记录命令结果。
- [ ] 4.4 C/部署负责人：在具备授权配置时完成真实 Redis/测试邮箱验证；验证：记录时间、测试标识、通过数、未验证项并只清理本次数据。
