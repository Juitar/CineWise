# Tasks

## 1. 契约与方案

- [x] 1.1 A/D 已确认 PAID 对账只调用 D 的 `TravelTaskApplicationService.ensureTask(PaymentSucceededEvent)`，每五分钟扫描最近二十四小时、每批最多一百条且单条失败不回滚整批；验证：对照 `travel-reminder-experience` 和 D 已合入实现。
- [x] 1.2 A 创建并严格校验本 change；验证：`openspec validate paid-travel-task-reconciliation --strict --no-interactive`。

## 2. A 后端实现

- [x] 2.1 A 实现按 `paid_time/id` 的 PAID 候选键集分页查询；验证：状态、窗口、边界和同时间排序测试通过。
- [x] 2.2 A 实现权威重读、上下文解析、事件重建和逐条 `ensureTask`；验证：重复、状态变化、上下文缺失和异常隔离测试通过。
- [x] 2.3 A 实现有界配置、默认关闭的五分钟 Job、traceId 和批处理报告；验证：Job 不访问 Mapper/D Repository，缺少显式开启配置时不注册，空窗口和失败汇总正确。

## 3. 验证与交付

- [x] 3.1 A 完成与 D 公开服务的 H2 和 MySQL 8.4 集成测试；验证：遗漏任务可补建、同毫秒键集分页、重复补偿任务唯一、交易终态不改变。
- [x] 3.2 A 执行完整质量门；验证：`mvnw.cmd verify`、关键生产代码有效注释、`git diff --check` 和范围检查通过。
- [x] 3.3 A/D 在 D 交付 `ensureTaskCancelled(OrderInvalidated)` 后补充“重读后退款、再执行 ensureTask”竞态联调；结果：H2真实链路最终保持唯一 `CANCELLED` 任务，生产默认启用并由 REFUNDED 对账兜底。
