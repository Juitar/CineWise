# Tasks: ticketing-transaction-flow

## 数据库基线

- [X] 建立proposal、design、ticketing-database spec和任务清单。
- [X] 冻结T16-T20字段、唯一约束、索引和CHECK约束。
- [X] 明确`ticket_order_seat`为追加写快照并补齐`update_time`审计字段。
- [X] A生成T16-T20标准多行Flyway SQL并完成静态核对。
- [X] V003已在本地MySQL 8.0.40可丢弃隔离库完成兼容性预演。
- [X] A按迁移规范在独立MySQL 8.4空库正式执行Flyway。
- [X] 正式执行重复迁移、约束和索引验证并保存结果；预演结果见`content-and-show-selection-flow/tasks.md`。

## 后续交易实现

- [x] 扩充proposal、design和spec，冻结原子建单、幂等、恢复和后续交易边界。
- [x] 实现交易配置、订单领域状态、错误码和持久化端口。
- [x] 实现按座位ID升序的场次校验与`AVAILABLE -> LOCKED`条件更新。
- [x] 实现`POST /api/v1/orders`原子建单与同键同参恢复。
- [x] 实现`GET /api/v1/orders/by-request/{clientRequestId}`本人结果恢复。
- [x] 完成单请求成功、同键同参、同键异参、部分冲突回滚和跨用户不可见测试。
- [x] 在真实MySQL 8执行20请求竞争同一座位的并发验收。
- [x] 冻结本人订单列表/详情、取消幂等记录、安全座位释放和过期Job契约。
- [x] 生成V005`ticket_order_operation`前向迁移并完成静态审查。
- [x] 实现本人订单分页列表、详情和批量座位快照查询。
- [x] 实现取消操作幂等、`PENDING_PAYMENT -> CANCELLED`和按订单归属释放座位。
- [x] 实现每单独立事务的过期释放服务与`ExpiredOrderReleaseJob`。
- [x] 完成本人/跨用户查询、重复取消、同键异参、旧订单归属保护、重复/并发过期及取消过期竞争测试。
- [x] 在独立MySQL 8库验证V005首次/重复迁移和取消/过期条件更新。
- [ ] 实现固定成功Mock支付、唯一电子票、`PaymentSucceededEvent`和结果未知恢复。
- [ ] 实现退票确认、幂等退票和替代场次查询。
- [ ] 完成OpenAPI、B/C联调JSON夹具以及并发、幂等、状态机、权限和恢复回归。

## 实现记录

- 变更编号及模块：`ticketing-transaction-flow`，A的`ticketing/order`模块。
- 需求/问题与修改范围：基于已执行V003实现原子锁座、幂等建单、丢失响应恢复、本人订单查询、幂等取消和可重复的超时释放；后续继续Mock支付和电子票。
- 契约影响：新增已冻结的订单REST端点和A拥有的V005操作幂等表；不修改C的认证、安全路由或场次/座位DTO，不修改V001至V004历史迁移。
- 已执行的验证及结果：2026-08-02执行`openspec validate ticketing-transaction-flow --strict`通过；执行`mvnw.cmd clean verify`通过，共25个测试、0失败、0错误、3个需显式MySQL环境变量的测试跳过，Checkstyle、SpotBugs、ArchUnit和JaCoCo通过。H2覆盖本人/跨用户查询、取消幂等、异参冲突、座位归属不一致回滚、重复/并发过期、取消与过期竞争及`PAYING/PAID`保护。本机MySQL 8.0.40独立库`cinewise_ticketing_concurrency_check`已从V004升级到V005，确认`utf8mb4_0900_ai_ci`、重复取消仅一个操作结果、过期仅一次成功且可安全重放；交易夹具已清理。此前20个用户竞争同座位结果为1个成功、19个返回`204001`，无超卖。
- 未验证事项、剩余风险和后续负责人：C的正式JWT Cookie和订单安全路由尚未合入，A仅通过`CurrentUserAccessor`测试替身验证本人权限；Mock支付、电子票和支付成功事件仍由A按上述任务继续实现。
