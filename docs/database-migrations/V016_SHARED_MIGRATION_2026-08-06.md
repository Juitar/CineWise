# V016 邮箱验证码用途扩展共享库迁移记录

## 执行范围

- 目标迁移：`V016__extend_auth_verification_purpose.sql`
- OpenSpec：`openspec/changes/password-reset-by-email-code/`（PR #100）
- 领域 Owner：C / auth；Flyway 发布负责人：A
- 执行日期：2026-08-06
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`690342A4C1CC5CD7C03307EFF118FF4D5C30F8E5113F7BA0CF0FC80774754FF8`
- Flyway V016 checksum：`1340550648`

## 发布前检查

- A 专用验证库已完成首次 migrate、validate、重复 migrate、结构、索引、CHECK、字符集、排序规则和正反约束验证。
- 发布前只读确认共享 `cinewise` 位于 V015，V016 为 Pending，且 `sys_email_verify_code` 已存在、`RESET_PASSWORD` 尚未写入。
- 使用独立迁移账号 `cinewise_migrator`；未使用日常应用账号，未修改日常 `FLYWAY_ENABLED` 配置。
- 已生成发布前逻辑备份：`data/migration-backups/cinewise-before-v016-20260806-162045-tables-data-triggers.sql`。
- 备份大小：3,364,093 bytes；SHA-256：`B81CE84AA3C3847AB965196AB1BD323D6BB3F273D959F53738B08E6EA6B6CED8`。

## 共享库发布结果

- 使用一次性受控 Flyway 进程将共享 `cinewise` 从 V015 升级到 V016，成功应用 1 个迁移。
- V016 安装时间为 `2026-08-06 16:21:29`，成功标志为 1，checksum 为 `1340550648`。
- 发布后 `flyway validate` 成功校验 16 个迁移。
- 发布后重复 migrate 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `sys_email_verify_code` 保持 InnoDB、`utf8mb4_0900_ai_ci`。
- `chk_verify_purpose` 实际允许 `REGISTER`、`LOGIN`、`RESET_PASSWORD`。
- `idx_verify_lookup(email, purpose, status, expire_time)` 列顺序保持不变。
- 除 `code_hash` 明确使用的 `ascii_bin` 外，无非预期字符串排序规则。
- 共享库中 `RESET_PASSWORD` 现有记录数为 0；迁移未写入业务数据。

## 结论

V016 已成功发布至共享 `cinewise`，并完成备份、Flyway 历史、发布后 validate、重复执行、CHECK、索引、字符集和排序规则核验。V016 自本次成功执行起冻结；后续规则调整必须新增更高版本的前向迁移。密码重置业务代码部署前，必须确认应用版本与 V016 已共同就绪。
