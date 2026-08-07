# V021 共享库迁移记录

迁移：`V021__create_ticketing_external_showtime_import_task.sql`
OpenSpec：`openspec/changes/external-showtime-sandbox-ticketing-import/`
Owner：A
执行日期：2026-08-07（Asia/Shanghai）

## 发布前检查

- 目标：共享 `cinewise`，不是迁移验证库。
- MySQL 版本：8.4.11。
- 共享库 Flyway 执行前最高版本：V020；V021 为 Pending；历史记录成功且无冲突。
- 逻辑备份：`data/migration-backups/cinewise-before-v021-20260807-180432-tables-data-triggers.sql`
- 备份大小：5,437,538 bytes。
- 备份 SHA-256：`DB9E9FF377FFB95D766DF19EF577CBAF4525C517FB85188D784B5D518E0AEB42`。
- 备份文件位于 `data/`，由 `.gitignore` 忽略，不进入提交。

## 执行结果

- Flyway migrate：通过；V021 于 `2026-08-07 18:06:26` 安装。
- Flyway checksum：`648765204`。
- Flyway validate：通过。
- 重复 migrate：通过；数据库已是 V021，无需重复执行。
- 执行过程仅使用独立迁移账号 `cinewise_migrator`；未使用日常应用账号。
- Flyway 仅报告 MySQL 整数显示宽度弃用警告，迁移成功且不影响本次结构。

## 结构核对

表 `ticketing_external_showtime_import_task`：

- Engine：InnoDB。
- 表排序规则：`utf8mb4_0900_ai_ci`。
- 字符串列排序规则：均为 `utf8mb4_0900_ai_ci`。
- 主键：`PRIMARY (id)`。
- 唯一键：`uk_ticketing_external_import_task_id (task_id)`、`uk_ticketing_external_import_client_request (client_request_id)`。
- 普通索引：`idx_ticketing_external_import_recovery (status, lease_until)`、`idx_ticketing_external_import_expire (expire_at)`。
- CHECK：ID/version、影院 JSON 数组、结果 JSON 数组、状态、truncated、计数、过期时间、错误码、租约配对、时间顺序和状态一致性约束均已存在。
- 当前业务行数：0；迁移未写入测试或种子数据。

未修改任何已发布迁移，未执行 DROP、TRUNCATE、DELETE 或其他数据清理操作。

记录人：A
