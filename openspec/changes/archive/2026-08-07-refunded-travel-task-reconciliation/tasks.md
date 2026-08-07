# Tasks

## 1. 契约与方案

- [x] 1.1 A/D 已冻结 `OrderInvalidated`、`ensureTaskCancelled`、退款版本比较和 `CANCELLED` 墓碑语义；验证：D 的 PR #52、`travel-reminder-experience` 和出行设计一致。
- [x] 1.2 A 创建本 change 并确认不需要 SQL/Flyway；验证：订单已有 `refunded_time`，本次只新增只读查询、应用服务、调度和测试。
- [x] 1.3 A 严格校验本 change；验证：`openspec validate refunded-travel-task-reconciliation --strict --no-interactive` 已通过。

## 2. A 后端实现

- [x] 2.1 A 实现按 `refunded_time/id` 的 REFUNDED 候选键集分页查询；验证：状态、非空时间、冻结窗口、同毫秒排序和批次上限测试通过。
- [x] 2.2 A 实现权威重读、上下文解析、`OrderInvalidated` 重建和逐条 `ensureTaskCancelled`；验证：重复、状态/版本变化、上下文缺失、异常隔离和事件字段测试通过。
- [x] 2.3 A 实现有界配置、五分钟 REFUNDED Job、traceId 和批处理报告；验证：Job 不访问 Mapper 或 D Repository，配置边界、空窗口和失败汇总正确。
- [x] 2.4 A 在安全门通过后默认启用 PAID 与 REFUNDED 两类 Job，测试配置继续显式关闭；验证：`TravelReconciliationConfigurationTest` 与两个 Job 条件测试通过。

## 3. 并发、集成与交付

- [x] 3.1 A/D 完成“PAID 重读后退款、再执行 ensureTask”的 H2 竞态联调；验证：最终唯一任务为 `CANCELLED`，版本不低于退款完成版本，交易终态仍为 `REFUNDED`。
- [x] 3.2 A 完成真实 MySQL 8.4 集成测试；验证：一次性 MySQL 8.4 容器中同毫秒退款、batch=1 键集分页、重复补偿和唯一取消任务通过，未修改历史迁移。
- [x] 3.3 A 更新 `paid-travel-task-reconciliation` 任务 3.3 与依赖说明；验证：两个 change 对默认开关和竞态结论一致。
- [x] 3.4 A 执行完整质量门；验证：`mvnw.cmd verify` 共执行 284 项测试、0 失败、0 错误、16 项环境条件跳过，Checkstyle/SpotBugs 均为 0；两个 OpenSpec strict、`git diff --check` 和范围检查通过；本次新增 5 个生产 Java 文件按规范口径统计 77 行有效注释、165 行有效代码，注释率 31.82%，并将后端整体机械统计值由 24.18% 提升至 24.25%。
