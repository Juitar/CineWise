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
- [x] 冻结Mock支付、支付恢复、电子票详情和支付成功事件生产边界，不新增SQL或占用V006。
- [x] 实现固定成功Mock支付、支付幂等恢复、订单与座位状态迁移和唯一电子票。
- [x] 实现支付查询、本人电子票详情REST接口及OpenAPI契约。
- [x] 完成重复/并发支付、支付与取消/过期竞争、归属回滚、跨用户和密码隔离测试。
- [x] 定义`PaymentSucceededEvent`及发布端口，待D公开`cinemaArea`摘要端口后接通AFTER_COMMIT发布。
- [ ] 待D通过公开Application API提供`cinemaArea`后，接通`PaymentSucceededEvent`的AFTER_COMMIT发布。
- [ ] 实现退票确认、幂等退票和替代场次查询。
- [ ] 完成OpenAPI、B/C联调JSON夹具以及并发、幂等、状态机、权限和恢复回归。

## 实现记录

- 变更编号及模块：`ticketing-transaction-flow`，A的`ticketing/order`模块。
- 需求/问题与修改范围：基于已执行V003和V005实现原子锁座建单、结果恢复、本人订单查询、幂等取消、可重复超时释放，以及固定成功Mock支付、支付结果恢复和唯一电子票；本阶段未新增或修改SQL。
- 契约影响：新增已冻结的支付与电子票REST端点、`PaymentSucceededEvent`类型和发布端口；不修改C的认证、安全路由或场次/座位DTO，不修改任何历史迁移，也不占用C暂定的V006。事件实际发布仍等待D通过公开Application API提供`cinemaArea`。
- 已执行的验证及结果：2026-08-03执行`openspec validate ticketing-transaction-flow --strict`和`git diff --check`通过；基于最新`origin/dev`执行`mvnw.cmd clean verify`通过，共45个测试、0失败、0错误、4个需显式MySQL环境变量的测试跳过，Checkstyle为0错误、SpotBugs为0缺陷，ArchUnit和JaCoCo通过。新增8个H2支付集成测试覆盖成功支付、原键和新键重放、跨订单幂等键冲突、到期拒绝、座位归属回滚、跨用户隔离、并发支付以及支付与取消/过期竞争；OpenAPI测试确认支付POST没有请求体且不含`paymentPassword`。本机MySQL 8.0.40独立库的支付并发测试结果为1条成功支付、1张电子票、订单`PAID`、座位`SOLD`，清理后支付、电子票、订单、订单座位和锁定座位均为0。此前20个用户竞争同座位结果仍为1个成功、19个返回`204001`，无超卖；V005云端验证及共享库迁移证据见`docs/V005_MIGRATION_VALIDATION_2026-08-02.md`和`docs/V005_SHARED_MIGRATION_2026-08-02.md`。
- 未验证事项、剩余风险和后续负责人：C的正式JWT Cookie和订单安全路由尚未合入，A仅通过`CurrentUserAccessor`测试替身验证本人权限；D的公开内容摘要端口尚不包含`cinemaArea`，因此A只完成冻结事件类型和发布端口，未跨模块读取D私表或伪造区域，待D补齐公开字段后再接通事务提交后的事件发布。
