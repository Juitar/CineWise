# V004 共享库迁移记录

## 1. 执行范围

- 目标数据库：云端共享 `cinewise`
- 目标迁移：`V004__create_content_snapshot_and_sync_log_tables.sql`
- 执行负责人：A
- 执行日期：2026-08-02
- 应用环境：`FLYWAY_ENABLED=false`
- 固定数据：本次不执行种子初始化

## 2. 发布前只读检查

- MySQL 版本为 8.4.11。
- 共享库执行前共有 11 张表。
- `flyway_schema_history` 中 V001、V002、V003 均执行成功。
- V001 至 V003 的 checksum 与专用验证库记录一致。
- `external_data_snapshot`、`data_sync_log` 均尚未存在。
- 日常 `.env` 使用 `cinewise_app`，迁移验证配置使用 `cinewise_migrator`；凭据文件均已被 Git 忽略。

## 3. 备份和恢复证据

- 已生成迁移前逻辑备份：`data/migration-backups/cinewise-before-v004-20260802-174555.sql`。
- 备份大小：1,915,684 字节。
- SHA-256：`77AF456B77D37DFB586E9E1EF4A2F88B41E7CECACF86993C2D96080884C643FC`。
- 备份文件位于 Git 忽略的 `data/` 目录，不得提交或发送给其他成员。
- 已将备份恢复到一次性本地 MySQL 8.4.11 容器。
- 恢复结果为 11 张表、3 条成功 Flyway 历史，当前版本为 V003。
- 恢复校验通过后，一次性容器已删除。

## 4. 迁移账号

- 首次连接时，`cinewise_migrator` 因仅有验证库权限返回 `ERROR 1044 (42000)`，V004 在该阶段未执行。
- A 随后使用数据库管理员身份，将共享 `cinewise` 的数据库级迁移权限永久授予 `cinewise_migrator`。
- 该账号没有获得服务器级权限或 `GRANT OPTION`，继续仅由 A 保管和用于 Flyway 迁移。
- 本次未使用日常 `cinewise_app` 执行结构迁移，也未将本机 Docker 的 `MYSQL_ROOT_PASSWORD` 当作云端管理员密码。

## 5. 共享库迁移结果

Flyway 在执行前成功校验 V001 至 V004，并只应用一个待执行迁移：

| 顺序 | 版本 | 描述 | Checksum | 结果 |
| ---: | --- | --- | ---: | --- |
| 1 | 001 | create movie and cinema tables | -2081649184 | 成功 |
| 2 | 002 | create ticketing show tables | 431219885 | 成功 |
| 3 | 003 | create ticketing order tables | 83073346 | 成功 |
| 4 | 004 | create content snapshot and sync log tables | -861550512 | 成功 |

- 首次执行应用 1 个迁移，目标版本为 V004。
- 使用相同迁移目录再次执行时，Flyway 校验 4 个迁移并报告无需迁移，执行数量为 0。
- 共享应用配置中的 `FLYWAY_ENABLED=false` 未修改。
- 本次没有执行固定种子或写入业务数据。

## 6. 发布后结构检查

- `external_data_snapshot`、`data_sync_log` 均已创建，当前记录数均为 0。
- 两张表的排序规则均为 `utf8mb4_0900_ai_ci`。
- `external_data_snapshot` 包含主键、`uk_external_snapshot` 和 `idx_external_expire`。
- `data_sync_log` 包含主键、`uk_sync_request`、`idx_sync_provider_time`、`idx_sync_type_time` 和 `idx_sync_cleanup_create_time`。
- 6 个 CHECK 约束全部存在：
  - `chk_external_snapshot_expire_time`
  - `chk_sync_completion`
  - `chk_sync_counts_nonnegative`
  - `chk_sync_processed_count`
  - `chk_sync_status`
  - `chk_sync_status_count_relation`

## 7. 结论与后续

V004 已于 2026-08-02 成功迁移到共享 `cinewise`，发布前备份、恢复演练、Flyway 重复执行和发布后结构检查均通过。V004 保持冻结，后续结构调整必须新增向前迁移。

D 下一步使用日常应用账号完成持久层和真实 MySQL 集成测试。另需单独复核并收紧 `cinewise_app` 当前拥有的 DDL 权限；该权限整改不影响本次 V004 已完成结论。
