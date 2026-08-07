# V022 电子票失效原因与任务索引迁移验证记录

## 基本信息

- 迁移：`V022__add_ticket_invalidation_reason_and_job_indexes.sql`
- OpenSpec：`electronic-ticket-show-end-invalidation`
- Owner：A / order、job
- MySQL：8.4.11
- Flyway V022 checksum：`1289633300`

## 专用验证库

- 数据库：`cinewise_migration_check`
- 执行前版本：V021
- 首次 migrate：成功，应用 1 个迁移，当前 V022
- validate：通过
- 重复 migrate：成功，应用 0 个迁移

## 共享库发布

- 数据库：`cinewise`
- 发布前版本：V021，失败历史为 0
- 发布前备份：`data/migration-backups/cinewise-before-v022-20260807-221305-tables-data-triggers.sql`
- 备份大小：8,383,800 bytes
- 备份 SHA-256：`B1CF678EEBCFD3059558641EF6D6E84CC3B170A08C278BDF996E346BCA2BE8C1`
- 首次 migrate：成功，应用 1 个迁移，当前 V022
- validate：通过
- 重复 migrate：成功，应用 0 个迁移

## 结构与执行计划

- `electronic_ticket.invalidation_reason VARCHAR(32)` 已存在。
- `electronic_ticket.idx_ticket_status_order_id(status, order_id, id)` 已存在。
- `movie_show.idx_show_end_time_id(end_time, id)` 已存在。
- 候选查询使用 `movie_show` 为驱动表的 `STRAIGHT_JOIN`；MySQL `EXPLAIN` 显示 `shows` 使用 `idx_show_end_time_id` 的 `range`，`orders` 使用 `idx_order_show`，`tickets` 使用 `uk_ticket_order`，无 `Using temporary` 或 `Using filesort`。

## 结论

V022 已在专用验证库和共享 `cinewise` 库完成首次迁移、校验、重复执行、结构检查及执行计划验证。迁移文件保持冻结，后续结构调整必须使用更高版本前向迁移。
