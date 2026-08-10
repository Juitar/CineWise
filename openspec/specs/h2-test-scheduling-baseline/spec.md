# h2-test-scheduling-baseline Specification

## Purpose
TBD - created by archiving change h2-test-scheduling-baseline. Update Purpose after archive.
## Requirements
### Requirement: H2 测试不自动执行后台调度

系统 SHALL 在 H2 `test` profile 关闭公共调度。测试不得因为后台 Job 访问未迁移的 MySQL 专属表而失败或无法退出。

#### Scenario: V017 outbox 未存在于 H2

- **GIVEN** H2 测试 Flyway target 为 V009 且不存在 `sys_profile_data_consent_outbox`
- **WHEN** 启动任意 Spring Boot H2 测试上下文
- **THEN** 不启动 `ProfileDataConsentOutboxJob` 或其他 `@Scheduled` 后台任务，Maven 测试可正常结束

### Requirement: 生产与 MySQL 调度不受影响

关闭 SHALL 仅存在于 H2 `test` profile，不得改变生产或 MySQL 集成测试的调度默认值。

#### Scenario: 非 H2 测试配置

- **WHEN** 应用未加载 `application-test.yml`
- **THEN** `cinewise.scheduling.enabled` 继续按默认值启用

