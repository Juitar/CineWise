# RESET_PASSWORD 验证码用途向前迁移申请

## 申请内容

- 申请方/Owner：C（认证）。
- 现有迁移：`V011__create_auth_email_code_and_registration_tables.sql` 已发布，不修改。
- 需要变更：`sys_email_verify_code.chk_verify_purpose` 从 `REGISTER/LOGIN` 扩展为 `REGISTER/LOGIN/RESET_PASSWORD`。
- 申请 A：分配新的 Flyway 版本号、审核最终 SQL、在独立空 MySQL 8.4 验证后决定共享环境执行时间。

## 兼容与回退

- 这是只放宽 CHECK 枚举的向前迁移，不修改列、索引或历史数据。
- 应先执行迁移，再启用发送 `RESET_PASSWORD` 记录的应用版本。
- 若迁移未执行，应用不得在真实 MySQL 声称密码重置可用。
- 回退应用不受新增枚举影响；不得通过回改 V011 或删除已有 RESET_PASSWORD 记录回退。

## 私下草案

未编号 SQL 草案保存在 `D:\tmp\cinewise-password-reset-migration-review\`，不进入 Git。A 分配版本后再按最终文件名审查。

