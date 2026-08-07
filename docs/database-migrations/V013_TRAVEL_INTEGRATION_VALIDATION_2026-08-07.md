# V013 出行任务 A/D MySQL 联调验证记录

## 基本信息

- OpenSpec：`travel-reminder-experience`
- 基线：`e7a39c7`（PR #143 合并后的 `dev`）
- 验证日期：2026-08-07
- MySQL：8.4.11
- A 专用临时 Docker 实例：`127.0.0.1:3307`
- 验证库：`cinewise_ticketing_concurrency_check`
- 迁移目标：`latest`（V001 至 V017）

## 执行命令

在 `backend/` 设置 CI 同等 MySQL 环境变量后执行：

```text
./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=PaymentMySqlIntegrationTest,RefundMySqlIntegrationTest,PaidTravelTaskReconciliationIntegrationTest,RefundedTravelTaskReconciliationIntegrationTest,TravelTaskPaymentEventIntegrationTest,TravelTaskControllerIntegrationTest test
```

其中出行联调测试显式启用 `CINEWISE_MYSQL_TRAVEL_IT` 及 PAID/REFUNDED reconciliation 开关。

## A/D 出行结果

- `TravelTaskPaymentEventIntegrationTest`：13/13 通过。
- `PaidTravelTaskReconciliationIntegrationTest`：2/2 通过。
- `RefundedTravelTaskReconciliationIntegrationTest`：3/3 通过。
- `TravelTaskControllerIntegrationTest`：2/2 通过。

覆盖：支付提交后建任务、重复支付幂等、支付回滚不建任务、退款取消、退款先到取消墓碑、非法影院 ID 写入 `NULL`、迟到支付不重开、历史 `cinema_id=NULL` 兼容、过期任务关闭和任务查询。

## 迁移结构复核

- `travel_task.cinema_id` 为 `BIGINT NULL`。
- `chk_travel_task_cinema_id_positive` 为 `cinema_id IS NULL OR cinema_id > 0`。
- Flyway V013、V017 均为成功状态；OpenSpec 严格校验通过。

支付/退款独立并发测试在同一批命令中因固定测试场次已过售票时间报“场次不可售或已开场”；该问题与 PR #143 的出行取消/调度修改无关，不影响上述 A/D 出行联调结果，后续应单独更新其时间夹具。

## 结论

PR #143 合并后的 A/D `cinemaId` 兼容链路和 V013 出行任务 MySQL 验收通过。本记录不代表 Redis、邮件、路线 Provider 或 C/D 前端联调已完成。
