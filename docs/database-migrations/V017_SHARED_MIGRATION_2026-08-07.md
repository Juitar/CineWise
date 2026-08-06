# V017 画像数据同意与可靠投递共享库迁移记录

## 执行范围

- 目标迁移：`V017__create_profile_data_consent_tables.sql`
- OpenSpec：`openspec/changes/user-profile-management/`
- 领域 Owner：C / auth；Flyway 发布负责人：A
- 执行日期：2026-08-07
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`B75369ACA06C903B2BDC5AADBC23A517BCBAC96A4850DF1F06CFFDB4D97ACF70`
- Flyway V017 checksum：`1650268085`

## 发布前检查

- A 专用验证库已完成 V017 首次 migrate、validate、重复 migrate、结构/索引/CHECK、字符集及正反约束验证。
- 发布前只读确认共享 `cinewise` 位于 V016，V017 为 Pending，且两张目标表不存在。
- 使用独立迁移账号 `cinewise_migrator` 执行；未使用日常应用账号执行 DDL，未修改应用 `FLYWAY_ENABLED` 配置。
- 本次未独立核验发布前备份或恢复演练证据，不能将其表述为已完成。

## 共享库发布结果

- Flyway 成功将共享 `cinewise` 从 V016 升级到 V017，应用 1 个迁移。
- V017 安装时间为 `2026-08-07 00:31:52`，成功标志为 1，checksum 为 `1650268085`。
- 发布后 `flyway validate` 成功校验 17 个迁移。
- 发布后重复 migrate 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `sys_profile_data_consent` 与 `sys_profile_data_consent_outbox` 已创建，均为 InnoDB、`utf8mb4_0900_ai_ci`。
- 共享数据库默认字符集/排序规则为 `utf8mb4` / `utf8mb4_0900_ai_ci`，目标表字符串列无非标准排序规则。
- 本次仅新增同意状态和 outbox 结构，不写入用户同意数据、事件或种子数据。

## 结论

V017 已成功发布至共享 `cinewise`，并完成 Flyway 历史、发布后 validate、重复执行、结构、CHECK 与排序规则核验。V017 自本次成功执行起冻结；后续结构调整必须新增更高版本的前向迁移。
