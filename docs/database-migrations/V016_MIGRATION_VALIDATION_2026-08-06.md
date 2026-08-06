# V016 邮箱验证码用途扩展迁移验证记录

## 基本信息

- 迁移：`V016__extend_auth_verification_purpose.sql`
- OpenSpec：`password-reset-by-email-code`（PR #100）
- 领域 Owner：C / auth
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-06
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`690342A4C1CC5CD7C03307EFF118FF4D5C30F8E5113F7BA0CF0FC80774754FF8`
- Flyway V016 checksum：`1340550648`

## 范围与环境守卫

- V016 仅替换 `sys_email_verify_code.chk_verify_purpose`，将允许值从 `REGISTER/LOGIN` 扩展为 `REGISTER/LOGIN/RESET_PASSWORD`。
- 不修改已发布的 V011，不改动表字段、索引、字符集、排序规则或历史数据，不包含种子、账号、密码、密钥或物理外键。
- 启动前已确认验证目标精确为 `cinewise_migration_check`，账号精确为 `cinewise_migrator`；日常 `.env` 和共享 `cinewise` 未用于本次验证。

## Flyway 验证

- 执行前 V001–V015 均为 Success，V016 为 Pending。
- 首次 `migrate` 成功将验证库从 V015 升级至 V016；V016 安装时间为 `2026-08-06 16:13:11`。
- `flyway validate` 成功校验 16 个迁移。
- 重复 `migrate` 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。

## 实际结构与约束

- `sys_email_verify_code` 保持 InnoDB 和 `utf8mb4_0900_ai_ci`。
- `chk_verify_purpose` 实际允许 `REGISTER`、`LOGIN`、`RESET_PASSWORD`。
- 既有 `idx_verify_lookup(email, purpose, status, expire_time)` 的列顺序未改变。
- 除 `code_hash` 明确使用的 `ascii_bin` 外，字符串列不存在非 `utf8mb4_0900_ai_ci` 排序规则。

## 正反约束用例

- `REGISTER`、`LOGIN` 和 `RESET_PASSWORD` 分别可在事务内写入。
- `RESET_UNKNOWN` 被 MySQL 的 `chk_verify_purpose` 拒绝。
- 测试事务回滚后，测试 ID 范围内无残留行。

## 结论

V016 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、CHECK、索引、字符集、排序规则和正反约束验证，结果通过。本记录不构成共享 `cinewise` 发布授权；共享库迁移、Git 直提和 PR #100 的合并/部署仍须由 A 分别授权。
