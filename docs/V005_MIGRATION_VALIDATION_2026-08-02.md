# V005 数据库迁移验证记录

## 1. 基本信息

- 迁移：`V005__create_ticket_order_operation_table.sql`
- OpenSpec：`openspec/changes/ticketing-transaction-flow/`
- Owner：A
- 授权与验证日期：2026-08-02
- 数据库：MySQL 8.4.11
- 目标：A 专用 `cinewise_migration_check` 空库
- 账号：专用 `cinewise_migrator`
- 凭据与主机：仅从被 Git 忽略的 `.env.migration-check` 读取，未写入本记录
- 固定数据：`SEED_ENABLED=false`

## 2. 环境隔离证据

- `CINEWISE_ENV_KIND=migration_check`、目标库名和专用账号守卫通过。
- `.env.migration-check` 已被 Git 忽略，日常 `.env` 未修改。
- 未连接或修改共享 `cinewise` 库。
- 清空前确认验证库只包含 V001 至 V005 DDL 定义的表及 Flyway 历史表，业务数据为零。
- 清空后确认目标库表数为零，再执行完整迁移。

## 3. 迁移与重复执行结果

Flyway 从空库依次成功应用 V001 至 V005，最终版本为 `005`，失败迁移数为零。

| 版本 | 描述 | Checksum | 结果 |
| --- | --- | ---: | --- |
| 001 | create movie and cinema tables | -2081649184 | 成功 |
| 002 | create ticketing show tables | 431219885 | 成功 |
| 003 | create ticketing order tables | 83073346 | 成功 |
| 004 | create content snapshot and sync log tables | -861550512 | 成功 |
| 005 | create ticket order operation table | 467504383 | 成功 |

使用同一当前制品再次启动后，Flyway 成功校验 5 个迁移，报告当前版本为 `005`，并明确输出 `Schema is up to date. No migration necessary.`。

## 4. V005 结构检查

- 数据库共 14 张表：13 张业务表和 1 张 Flyway 历史表。
- `ticket_order_operation` 共 11 个字段，类型、非空性和毫秒时间字段与 OpenSpec 一致。
- 主键：`PRIMARY(id)`。
- 唯一键：`uk_order_operation_user_action_key(user_id, action, idempotency_key)`。
- 普通索引：`idx_order_operation_order_action(order_id, action)`。
- CHECK：`chk_order_operation_result_version`。
- 物理外键数量为零。
- 所有表及字符串列均为 `utf8mb4_0900_ai_ci`，不一致数量为零。

## 5. 约束用例

| 用例 | 预期 | 结果 |
| --- | --- | --- |
| 合法操作记录 | 接受 | 通过 |
| 相同用户、动作和幂等键重复 | 唯一键拒绝 | 通过，MySQL `ERROR 1062` |
| `result_version=-1` | CHECK 拒绝 | 通过，MySQL `ERROR 3819` |

测试结束后已按专用测试用户 ID 定向删除数据，`ticket_order_operation` 剩余记录数为零。

## 6. 执行说明

验证制品通过 `mvnw.cmd -DskipTests package` 从当前工作区重新构建，并确认 JAR 包含 V005。为使迁移进程一次性退出，本次使用 non-web 启动；Flyway 完成后，C 的安全壳因缺少 Servlet `HttpSecurity` 报错。该错误发生在迁移提交之后，未改变 Flyway 成功记录、Schema 或约束验证结果；本次未修改 C 的认证或安全配置。

## 7. 结论

V005 已在 MySQL 8.4.11 的 A 专用空验证库完成首次迁移、V004 到 V005 升级、重复执行、Flyway 历史、结构、索引、CHECK、唯一约束、无外键和排序规则验证。验证通过不代表已经迁移共享 `cinewise` 库；共享库发布仍需单独完成历史核对、备份恢复确认、迁移窗口通知和 A 的正式发布授权。
