# Tasks

## 1. 契约与方案

- [x] 1.1 A/D 已冻结 `OrderInvalidated` 字段、`invalidReason=REFUNDED`、退款后订单版本和 `AFTER_COMMIT` 消费边界；验证：对照 `travel-reminder-experience` 与系统设计。
- [x] 1.2 A 创建并严格校验本 change；验证：`openspec validate order-invalidated-event --strict --no-interactive`。

## 2. A 事件生产端

- [x] 2.1 A 将支付专用上下文解析器重构为支付与退款共用的出行事件上下文解析器；验证：仅调用 A 场次公开服务和 D 的 `ContentSummaryQueryPort`，支付回归通过。
- [x] 2.2 A 新增 `OrderInvalidated`、发布端口和 Spring 适配器；验证：字段、时区、隐私和模块架构检查通过。
- [x] 2.3 A 在首次退款事务内安全登记事件；验证：重放、并发、回滚和异常隔离测试通过。

## 3. 验证与交付

- [x] 3.1 A 补齐退款事件集成测试；验证：首次提交、幂等重放、并发、回滚、同步登记失败、提交后消费失败和上下文降级全部通过。
- [x] 3.2 A 执行后端完整质量门；验证：`mvnw.cmd verify`、生产代码有效注释率、`git diff --check` 和范围检查通过。
