# V006 共享库迁移记录

## 1. 执行范围

- 目标数据库：云端共享 `cinewise`
- 目标迁移：`V006__create_auth_user_and_login_log_tables.sql`
- OpenSpec：`openspec/changes/password-login-and-session/`
- 领域 Owner：C
- 执行负责人：A
- 执行日期：2026-08-03
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`67164DDAC9CFB8135A793C01043B7CE6785D46B09C0D182E8BE48D83A6016251`
- Flyway V006 checksum：`-2112675263`
- 应用环境：`FLYWAY_ENABLED=false`
- 固定数据：`SEED_ENABLED=false`，本次不执行账号或其他种子初始化

## 2. 发布前只读检查

- `origin/dev` 已包含正式 V006，远端 Git blob 与本地文件一致。
- 使用专用 `cinewise_migrator`，未使用日常 `cinewise_app` 执行结构迁移。
- 共享库执行前共 14 张表，Flyway 当前版本为 V005，失败迁移数为零。
- V001 至 V005 checksum 与专用验证库及历史发布记录一致。
- `sys_user`、`sys_login_log` 和 V006 Flyway 历史均尚不存在。
- 数据库、现有表和字符串列均为 `utf8mb4_0900_ai_ci`。
- V006 只新增两张独立空表，不修改现有表、字段或业务数据，不建立物理外键，属于向后兼容增量迁移。
- A 在当前会话明确授权检查并执行共享库 V006，作为本次迁移窗口确认。

## 3. 备份和恢复证据

- 迁移前逻辑备份：`data/migration-backups/cinewise-before-v006-20260803-155851.sql`。
- 备份大小：1,920,928 字节。
- SHA-256：`2FBE162E1408EAD54319C58A71F2E67C0686C8869ECD8CDC76EFDEF554BEC856`。
- 备份位于 Git 忽略的 `data/` 目录，不提交、不推送，也不向无关成员发送。
- 备份已恢复到一次性 MySQL 8.4.11 容器。
- 恢复结果：14 张表、Flyway 当前版本 V005、V005 checksum `467504383`、失败迁移数 0。
- 第一次一次性容器在刚通过就绪探测后立即导入时返回非零并被删除；随后两个全新同版本容器均成功完成恢复，其中最终一次完成全部历史核验。该过程未连接或修改共享库。
- 恢复验证完成后，一次性容器均已删除。

## 4. 共享库迁移结果

Flyway 成功校验 V001 至 V006，并只应用一个待执行迁移：

| 版本 | 描述 | Checksum | 结果 |
| --- | --- | ---: | --- |
| 001 | create movie and cinema tables | -2081649184 | 成功 |
| 002 | create ticketing show tables | 431219885 | 成功 |
| 003 | create ticketing order tables | 83073346 | 成功 |
| 004 | create content snapshot and sync log tables | -861550512 | 成功 |
| 005 | create ticket order operation table | 467504383 | 成功 |
| 006 | create auth user and login log tables | -2112675263 | 成功 |

- 首次执行从 V005 升级到 V006，失败迁移数为零。
- 使用同一制品重复执行时，Flyway 成功校验 6 个迁移，并报告 `Schema is up to date. No migration necessary.`。
- `.env`、`.env.cloud` 和 `.env.migration-check` 中的 `FLYWAY_ENABLED=false` 均未持久化修改。
- 本次没有执行账号、密码散列、真实邮箱或其他种子数据。

## 5. 发布后结构检查

- 共享库当前共 16 张表，Flyway 当前版本为 V006，失败迁移数为零。
- `sys_user`、`sys_login_log` 均已创建，记录数均为零。
- `sys_user` 包含主键、`uk_sys_user_email` 和 `idx_sys_user_status_update`。
- `sys_login_log` 包含主键、`idx_login_user_time`、`idx_login_result_time`、`idx_login_trace` 和 `idx_login_cleanup_create_time`。
- 7 个 V006 CHECK 全部存在：
  - `chk_sys_user_role_code`
  - `chk_sys_user_status`
  - `chk_sys_user_email_verified`
  - `chk_sys_user_versions_nonnegative`
  - `chk_sys_login_log_type`
  - `chk_sys_login_log_success`
  - `chk_sys_login_log_result_consistency`
- 两张新表、全库表和全部字符串列均为 `utf8mb4_0900_ai_ci`，不一致数量为零。
- 全库物理外键数量为零。

## 6. 执行说明

迁移通过当前 `dev` 构建的制品运行。为避免迁移完成后持续启动共享应用，本次使用 non-web 一次性进程；Flyway 成功提交 V006 后，认证安全壳因没有 Servlet `HttpSecurity` 退出。该错误发生在迁移提交之后，不影响 Flyway 历史、Schema、重复执行或发布后结构检查，也没有执行定时任务和种子初始化。

## 7. 结论与后续

V006 已成功进入共享 `cinewise`。发布前只读检查、迁移前备份、MySQL 8.4.11 恢复演练、Flyway 历史与 checksum、单版本执行、重复执行、两张空表、索引、CHECK、无外键和排序规则检查均通过。

V006 自本次成功执行后冻结，禁止修改、改名或删除；任何后续结构调整必须申请新的向前迁移版本。C 下一步可使用日常应用账号完成认证持久层、密码登录、`/auth/me`、登出、CSRF 和 Cookie 的真实 MySQL 联调。
