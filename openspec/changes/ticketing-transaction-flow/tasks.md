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
- [x] 冻结退票影响、传统页面确认、原子退票、结果恢复和替代场次契约，确认复用V003且不新增SQL。
- [x] 实现退票影响查询和传统页面二次确认后的REST退票入口，Agent `actionId`保留在Tool Adapter边界。
- [x] 实现退款幂等快照、`PAID -> REFUNDING -> REFUNDED`、票失效和按订单明细安全释放`SOLD`座位。
- [x] 实现退票结果恢复和同影片未来可售替代场次查询。
- [x] 完成同键同参/异参、跨用户、状态/归属回滚、并发退票和结果未知恢复测试。
- [x] 冻结OpenAPI与B/C票务联调JSON夹具的字段、错误和恢复语义。
- [x] 提供B的`ToolResult<T>`成功、座位冲突、幂等恢复和订单不存在JSON夹具。
- [x] 提供C的场次、座位、订单、支付、电子票、退票及关键错误REST JSON夹具。
- [x] 增加夹具解析、敏感字段、OpenAPI、并发、幂等、状态机、权限和恢复本地回归并记录结果。
- [x] PR #18 的MySQL 8.4、Redis 7.4、Backend Verify和Frontend Verify工作流全部通过；失败时不得合并。

## 实现记录

- 变更编号及模块：`ticketing-transaction-flow`，A的`ticketing/order`模块。
- 需求/问题与修改范围：基于已执行V003和V005实现原子锁座建单、本人订单生命周期、固定成功Mock支付、唯一电子票，以及退票影响、原子幂等退票、结果恢复和同影片替代场次；退票截止采用原场次严格未开场边界，本阶段未新增或修改SQL。
- 契约影响：新增已冻结的支付、电子票、退票影响、退票写入/恢复和替代场次REST端点，以及`PaymentSucceededEvent`类型和发布端口；不修改C的认证、安全路由，不实现B的确认存储，不读取D私有持久层，不修改任何历史迁移，也不占用预留版本。传统页面REST拒绝非空Agent `actionId`，未来Tool Adapter须在调用A应用服务前由B校验确认。
- 已执行的验证及结果：2026-08-03同步最新`origin/dev`后执行`openspec validate ticketing-transaction-flow --strict --no-interactive`和`git diff --check`通过；执行`mvnw.cmd clean verify`通过，共65个测试、0失败、0错误、5个需显式MySQL环境变量的测试跳过，Checkstyle为0错误、SpotBugs为0缺陷，ArchUnit和JaCoCo通过。新增7个H2退票集成测试覆盖影响查询、成功与原键/新键重放、异参和跨订单幂等冲突、开场截止、Agent确认隔离、票/座位不一致回滚、跨用户、并发唯一退款、结果恢复和替代场次过滤；OpenAPI已包含四个退票端点。本机MySQL 8.0.40隔离库`cinewise_ticketing_concurrency_check`此前已验证两个并发退票最终仅1条退款，订单`REFUNDED`、电子票`REFUNDED`、座位`AVAILABLE`，清理后退款、支付、电子票、订单、订单座位和非可用座位均为0。按V1.6口径，本次新增生产代码有效注释率约30.67%，A的`order/ticketing`模块由基线约5.58%提升到13.35%。此前支付、20请求竞争同座位及V005迁移证据继续有效。
- 未验证事项、剩余风险和后续负责人：本次同步后当前`.env`不指向退票专用隔离库，因此未重跑会清理交易夹具的MySQL退票测试；C正式JWT Cookie和订单安全路由仍需联调；B确认端口未接入，当前传统REST拒绝非空`actionId`；D公开内容摘要仍缺`cinemaArea`，支付事件实际发布继续等待D。全后端历史生产代码粗测有效注释率约10.3%，虽然本次新增达到30%且未降低A模块比例，但最终全仓30%门槛仍需A/B/C/D按各自Owner边界共同补齐，不能在本退票PR跨模块批量改写。

## OpenAPI与B/C联调夹具实现记录

- 变更编号及模块：`ticketing-transaction-flow`，A的票务公开契约与测试资源。
- 需求/问题与修改范围：新增B的5份`ToolResult<T>`夹具和C的13份REST夹具；增加自动解析、时效、幂等恢复、错误语义、敏感字段和实际SpringDoc校验，不修改生产业务代码、数据库或状态机。
- 契约影响：无运行时API变更；夹具严格复用现有`basePrice`、字符串ID、金额字符串、ISO 8601时间、稳定错误码和公共ToolResult字段。支付不进入Agent工具，C继续通过公共请求层消费REST。
- 已执行的验证及结果：同步最新`origin/dev`后，`TicketingContractFixtureTest`共3个测试通过；`mvnw.cmd verify`共91个测试、0失败、0错误、6个外部环境测试跳过，Checkstyle和SpotBugs均为0；`openspec validate ticketing-transaction-flow --strict`与`git diff --check`通过。
- 未验证事项、剩余风险和后续负责人：PR #18 的Backend MySQL Integration Run 30813882371和Backend Redis Integration Run 30813882372均通过，前后端Verify也通过。B/C仍需在各自模块消费夹具完成联调，A不替其标记消费者验收。
