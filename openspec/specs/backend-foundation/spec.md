# backend-foundation Specification

## Purpose
TBD - created by archiving change foundation-contracts-and-auth-shell. Update Purpose after archive.
## Requirements
### Requirement: 唯一模块化单体入口

系统 SHALL 只有一个 Spring Boot 启动入口。各领域 SHALL 位于既定包内，禁止创建独立后端应用或内部 HTTP 自调用。

#### Scenario: 新模块接入

- GIVEN 成员新增领域功能
- WHEN 创建 Controller、用例、领域规则和持久化实现
- THEN 代码分别落在该领域的 `api/application/domain/infrastructure`
- AND 跨模块只依赖公开 Application API、DTO、事件或工具

### Requirement: 统一 REST 契约

业务 REST SHALL 返回 `Result<T>`；分页数据 SHALL 使用 `PageResult<T>`。失败码 SHALL 为六位数值，响应 SHALL 携带与响应头相同的 traceId。

#### Scenario: 调用方提供非法 traceId

- GIVEN 请求的 `X-Trace-Id` 含空白、控制字符或长度越界
- WHEN 请求进入应用
- THEN 系统生成新的 32 位十六进制 traceId
- AND 响应头、响应体与日志上下文使用新值

### Requirement: 身份来源

业务模块 SHALL 只通过 `CurrentUserAccessor` 读取用户 ID、角色和 tokenVersion，不得信任请求 DTO 中的 userId，也不得自行解析 JWT。

#### Scenario: 认证尚未接入

- GIVEN C 的认证实现尚未合并
- WHEN 调用非健康检查和非接口文档路径
- THEN 默认安全链拒绝请求

### Requirement: 可重复执行的调度任务

系统 SHALL 使用统一的 4 线程调度器。业务 Job SHALL 通过 Application Service 执行，并以数据库条件更新、版本 CAS 或唯一约束保证重复执行安全。

#### Scenario: 应用重启后补扫

- GIVEN 上一轮 Job 执行中应用重启
- WHEN 下一轮按权威数据库条件扫描
- THEN 未完成记录仍可被处理
- AND 已成功记录不会产生重复业务结果

