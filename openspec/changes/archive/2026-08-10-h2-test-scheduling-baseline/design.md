# 实现设计

公共 `SchedulingConfiguration` 已由 `cinewise.scheduling.enabled` 控制。仅在 `backend/src/test/resources/application-test.yml` 将其设为 `false`，使 H2 测试不注册 `@EnableScheduling` 与后台线程池。

生产与 MySQL 集成 profile 未使用该测试配置，因此不受影响。H2 继续固定在 V009；V010 及后续 MySQL 专属迁移仍只由 MySQL 集成测试验证。
