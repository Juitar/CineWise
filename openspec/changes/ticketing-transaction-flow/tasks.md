# Tasks: ticketing-transaction-flow

## 数据库基线

- [x] 建立proposal、design、ticketing-database spec和任务清单。
- [x] 冻结T16-T20字段、唯一约束、索引和CHECK约束。
- [x] 明确`ticket_order_seat`为追加写快照并补齐`update_time`审计字段。
- [x] A生成T16-T20标准多行Flyway SQL并完成静态核对。
- [x] V003已在本地MySQL 8.0.40可丢弃隔离库完成兼容性预演。
- [x] A使用Flyway 13.0.0在云端MySQL 8.4.11空库正式执行V003。
- [x] 完成重复迁移、约束和索引验证并保存结果；证据见`content-and-show-selection-flow/tasks.md`。

## 后续交易实现

- [ ] 实现原子锁座与幂等建单。
- [ ] 实现本人订单查询、取消和过期释放。
- [ ] 实现固定成功Mock支付、唯一电子票和结果未知恢复。
- [ ] 实现退票确认、幂等退票和替代场次查询。
- [ ] 完成并发、幂等、状态机、权限和恢复测试。
