# 任务

- [x] 1.1 C：核对认证设计中的端口类型、模板白名单、结果和 Owner 边界；验证：记录公开字段和非范围。
- [x] 1.2 C：定义 `EmailDeliveryPort`、命令、结果、状态和给 D 的接口说明；验证：公开类型编译和文档检查。
- [x] 2.1 C：实现命令、模板、变量和站内路径校验；验证：合法、非法、超长和敏感变量单测。
- [x] 2.2 C：实现按 `recipientUserId` 解析正常已验证邮箱；验证：正常、不存在、未验证和不可用账号单测。
- [x] 2.3 C：抽取并复用现有 SMTP Provider，支持 `SENT/FAILED/UNKNOWN` 和 query；验证：明确失败、超时、结果未知和未配置测试。
- [x] 2.4 C：实现稳定 `deliveryKey` 的 Mock/进程内重复调用规则；验证：重复 send 不增加 Mock 投递计数，query 可恢复首次结果。
- [x] 2.5 C：增加安全日志和指标边界；验证：日志扫描不包含邮箱、变量、正文、验证码或密钥。
- [x] 3.1 C：增加架构依赖测试；验证：不访问 D、Agent、订单 Repository 或 Controller。
- [x] 3.2 C：执行后端定向测试和 `backend\\mvnw.cmd verify`；验证：记录通过、失败、跳过数。
- [x] 3.3 C：执行本 change、`email-code-authentication`、`password-login-and-session` strict 校验及 Git 检查；验证：记录命令结果。
- [ ] 3.4 C/部署负责人：在具备授权配置时完成真实 SMTP/测试邮箱和原 `deliveryKey` 查询验证；验证：记录时间、测试标识、通过数、未验证项并只清理本次数据。
