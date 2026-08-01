# Design: foundation-contracts-and-auth-shell

## 决策

采用单 Maven 工程的包级模块化单体。所有领域位于 `com.miaoyu.ticket` 下，并以 `api/application/domain/infrastructure` 分层。使用 ArchUnit 阻止 Domain 依赖框架、API 依赖持久层和 Application 依赖 Web。

公共响应由 Controller 显式返回 `Result<T>`，不使用全局响应自动包装，避免字符串、文件、SSE 与 SpringDoc 响应被误包。异常由 `GlobalExceptionHandler` 映射为 HTTP 状态和领域码。

认证暂使用 fail-closed 安全链，只开放健康检查与接口文档。C 在同一配置上加入 JWT Cookie、CSRF 和权限规则，避免出现多条顺序不明确的过滤链。业务模块仅依赖 `CurrentUserAccessor`。

各Owner先在对应change和领域详设中确认本人表结构；A核对全局ID、时间和命名规则后统一分配Flyway版本。确认门完成前不生成`system_config`、`system_operation_log`或其他领域表迁移。

## 风险与处理

- H2 与 MySQL 行为不同：H2 只做快速上下文测试，交易与迁移必须补真实 MySQL 集成测试。
- 安全链尚未实现认证：默认拒绝确保未完成时不会误开放业务接口。
- Docker 在部分成员电脑不可用：Maven verify 可独立运行，容器验证在有 Docker 的机器和 CI 环境补做。
- 公共类过早膨胀：骨架只冻结跨模块必需契约，领域 DTO 留在各自 Application 包。
