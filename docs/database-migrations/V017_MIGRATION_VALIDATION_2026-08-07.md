# V017 画像数据同意与可靠投递迁移验证记录

## 基本信息

- 迁移：`V017__create_profile_data_consent_tables.sql`
- OpenSpec：`user-profile-management`
- 领域 Owner：C / auth
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-07
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`B75369ACA06C903B2BDC5AADBC23A517BCBAC96A4850DF1F06CFFDB4D97ACF70`
- Flyway V017 checksum：`1650268085`

## 范围与环境守卫

- V017 仅创建 `sys_profile_data_consent` 与 `sys_profile_data_consent_outbox`；不修改 V001–V016，不建立物理外键，不包含种子、账号、密码、密钥或业务数据。
- 启动前确认验证库已成功执行 V001–V016，V017 是唯一 Pending 迁移，且两张目标表均不存在。
- 使用 `cinewise_migration_check` 与 `cinewise_migrator`；未连接或迁移共享 `cinewise`，未开启应用自动迁移。

## Flyway 验证

- 首次 migrate 成功将验证库从 V016 升级到 V017；V017 安装时间为 `2026-08-07 00:22:47`。
- `flyway validate` 成功校验 17 个迁移。
- 重复 migrate 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。

## 实际结构、索引与排序规则

- 数据库默认字符集/排序规则为 `utf8mb4` / `utf8mb4_0900_ai_ci`。
- 两张新表均为 InnoDB、`utf8mb4_0900_ai_ci`；非标准字符串列排序规则数量为 0。
- `sys_profile_data_consent` 实际包含主键、`user_id` 唯一键和 5 条 CHECK：正数 ID、状态、版本、非空隐私政策版本、授权/撤回时间关系。
- `sys_profile_data_consent_outbox` 实际包含主键、`event_id` 唯一键、`(user_id, consent_record_version)` 撤回唯一键及 `(status, next_attempt_at, id)` 扫描索引，并包含 7 条 CHECK。

## 正反约束用例

- 合法 GRANTED、WITHDRAWN、PENDING、DELIVERED、EXHAUSTED 记录均可在事务中写入。
- 非正 ID、空隐私政策版本、零同意版本、GRANTED 携带撤回时间、撤回时间早于授权时间均被 CHECK 拒绝。
- 空 `event_id`、空 `trace_id`、重复撤回唯一键、非法 outbox 状态、重试次数大于 10、PENDING 缺少下次尝试时间、DELIVERED/EXHAUSTED 的终态时间组合错误均被唯一键或 CHECK 拒绝。
- 全部测试在事务中回滚；验证结束后两张目标表的测试行数均为 0。

## 结论

V017 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、实际结构、索引、CHECK、字符集、排序规则及正反约束验证，结果通过。该记录不构成共享库迁移或 Git 提交授权。
