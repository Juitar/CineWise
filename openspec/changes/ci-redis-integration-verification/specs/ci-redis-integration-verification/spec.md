## Purpose

在 PR 合并前使用隔离的真实 Redis 验证后端缓存集成，同时保持共享环境和生产凭据不参与 CI。

## ADDED Requirements

### Requirement: Redis 集成测试必须在隔离服务上运行

系统 SHALL 为后端 Redis 集成验证启动固定版本的一次性 Redis 服务，并仅在服务健康后运行验证。

#### Scenario: Redis 服务正常启动

- **WHEN** 后端变更触发 Redis 集成工作流
- **THEN** 系统启动 Redis 7.4.10 并通过 PING 健康检查
- **AND** 系统开启 Redis 集成测试环境门并执行后端验证

#### Scenario: Redis 服务不可用

- **WHEN** Redis 容器无法启动或未在健康检查期限内响应
- **THEN** Redis 集成质量门失败
- **AND** 系统不得把相关测试标记为通过

### Requirement: Redis CI 不得依赖共享环境

系统 SHALL 只连接 GitHub Runner 上的一次性 Redis，不得读取云端 Redis 地址、生产密码或共享业务数据。

#### Scenario: 执行 PR 验证

- **WHEN** Redis 集成工作流执行
- **THEN** 后端通过本机地址连接一次性容器
- **AND** 工作流结束后不保留 Redis 数据

### Requirement: 快速后端质量门保持独立

系统 SHALL 保留现有不依赖外部服务的后端快速验证，并将 Redis 集成失败作为独立结果报告。

#### Scenario: Redis 集成失败

- **WHEN** 快速后端验证通过但 Redis 集成测试失败
- **THEN** 两个质量门分别显示结果
- **AND** PR 不得仅凭快速验证通过而视为 Redis 集成已通过
