# Proposal: foundation-contracts-and-auth-shell

## 背景

四位成员需要在同一个模块化单体中并行开发。若先各自创建启动类、响应包装、错误处理、数据库配置或安全链，会造成包结构、DTO、迁移和配置冲突。

## 目标

- 建立唯一 Java 21 / Spring Boot 3.5 Maven 应用。
- 冻结公共响应、错误码、traceId、分页、ID/金额/时间、OpenAPI 和认证上下文边界。
- 建立 MyBatis-Plus、Flyway、MySQL、Redis、调度、健康检查和可选 MinIO 的公共入口。
- 为 A/B/C/D 创建清晰的领域包和自动化架构守卫。
- 提供本地 Compose、镜像构建、CI 与团队接入说明。

## 非目标

- 不实现 C 的 JWT、登录注册、验证码与邮件业务。
- 不实现 A/B/D 的具体领域用例、表结构或第三方 Provider。
- 不冻结尚未由相应 Owner 在详细设计中确定的内部字段。

## Owner 与审查

A 负责骨架实现与迁移顺序；C 审查 Security、CurrentUser 和 CORS；B/D 审查跨模块端口落位。公共契约变更需所有受影响消费者审查。
