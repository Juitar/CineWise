# V006 数据库迁移验证记录

## 1. 基本信息

- 迁移：`V006__create_auth_user_and_login_log_tables.sql`
- OpenSpec：`openspec/changes/password-login-and-session/`
- 领域 Owner：C
- Flyway 版本分配与验证负责人：A
- 授权与验证日期：2026-08-03
- 数据库：MySQL 8.4.11
- 目标：A 专用 `cinewise_migration_check` 空库
- 账号：专用 `cinewise_migrator`
- 凭据与主机：仅从被 Git 忽略的 `.env.migration-check` 读取，未写入本记录
- SQL SHA-256：`67164DDAC9CFB8135A793C01043B7CE6785D46B09C0D182E8BE48D83A6016251`
- Flyway V006 checksum：`-2112675263`
- 固定数据：`SEED_ENABLED=false`

## 2. 环境隔离与执行授权

- A 已在当前会话明确授权 AI 仅连接 A 专属验证库执行 V006 受控验证，并明确禁止迁移共享 `cinewise` 库。
- `CINEWISE_ENV_KIND=migration_check`、目标库名和专用账号守卫通过。
- 执行前验证库仅包含 V001 至 V005 创建的表及 Flyway 历史表，13 张业务表实际行数均为零。
- 清空后确认目标库表数为零，再使用包含 V001 至 V006 的当前制品执行首次迁移。
- 日常 `.env` 未修改；`.env.migration-check` 中 `FLYWAY_ENABLED=false` 未持久化修改。
- 未连接或修改共享 `cinewise` 库，未读取、输出或记录主机、密码和连接串。

## 3. 迁移、校验与重复执行

Flyway 从空库依次成功应用 V001 至 V006，最终版本为 `006`，失败迁移数为零。

| 版本 | 描述 | Checksum | 结果 |
| --- | --- | ---: | --- |
| 001 | create movie and cinema tables | -2081649184 | 成功 |
| 002 | create ticketing show tables | 431219885 | 成功 |
| 003 | create ticketing order tables | 83073346 | 成功 |
| 004 | create content snapshot and sync log tables | -861550512 | 成功 |
| 005 | create ticket order operation table | 467504383 | 成功 |
| 006 | create auth user and login log tables | -2112675263 | 成功 |

使用同一制品再次启动后，Flyway 成功校验 6 个迁移，确认当前版本为 `006`，并报告 `Schema is up to date. No migration necessary.`，没有重复执行 V006。

## 4. V006 实际结构

- `sys_user`：13 个字段；主键 `PRIMARY(id)`；唯一索引 `uk_sys_user_email(email)`；普通索引 `idx_sys_user_status_update(status, update_time)`。
- `sys_login_log`：9 个字段；主键 `PRIMARY(id)`；普通索引 `idx_login_user_time(user_id, create_time)`、`idx_login_result_time(success, create_time)`、`idx_login_trace(trace_id)`、`idx_login_cleanup_create_time(create_time)`。
- 实际存在 7 个 V006 CHECK：4 个属于 `sys_user`，3 个属于 `sys_login_log`。
- `sys_login_log` 只有 `create_time`，没有 `update_time`，符合只追加登录审计日志契约。
- 两张表均无 `AUTO_INCREMENT`，全库物理外键数量为零。
- 数据库、16 张表（15 张业务表和 Flyway 历史表）及全部字符串列均为 `utf8mb4_0900_ai_ci`，不一致字符串列数量为零。
- 登录日志 30 天清理 SQL 的执行计划使用 `idx_login_cleanup_create_time`，访问类型为 `range`。

## 5. 约束正反用例

| 用例 | 预期 | 结果 |
| --- | --- | --- |
| 合法用户 | 接受 | 通过 |
| 大小写不同的相同邮箱并发插入 | 仅一个成功 | 通过；一个成功，另一个被唯一键以 `ERROR 1062` 拒绝 |
| 非法 `role_code` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 非法 `status` | CHECK 拒绝 | 通过，`ERROR 3819` |
| `email_verified=2` | CHECK 拒绝 | 通过，`ERROR 3819` |
| `token_version=-1` | CHECK 拒绝 | 通过，`ERROR 3819` |
| `version=-1` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 合法成功日志 | 接受 | 通过 |
| 成功日志缺少 `user_id` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 成功日志包含 `failure_code` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 失败日志 `user_id=NULL` 且包含失败码 | 接受 | 通过 |
| 失败日志缺少 `failure_code` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 非法 `login_type` | CHECK 拒绝 | 通过，`ERROR 3819` |
| `success=2` | CHECK 拒绝 | 通过，`ERROR 3819` |

所有临时验证数据均按专用测试 ID 定向删除。验证结束时 15 张业务表总行数为零。

## 6. 兼容性说明

MySQL 8.4.11 接受并执行全部 V006 DDL。`email_verified` 和 `success` 使用项目现行规范要求的 `TINYINT(1)`，MySQL 返回两次 `Integer display width is deprecated` 警告；该警告不影响迁移、字段类型、CHECK 执行或当前 MySQL 8.4 兼容性，但后续可在统一布尔字段规范升级时评估改用不带显示宽度的 `TINYINT`。

## 7. 结论

V006 已在 MySQL 8.4.11 的 A 专用空验证库完成首次 migrate、Flyway validate、重复 migrate、历史与 checksum、实际字段、索引、CHECK、并发唯一键、清理索引、无外键、字符集和排序规则验证，结果通过。

本结论只允许 A 将当前 SHA-256 对应的 V006 文件作为验证通过的最终迁移候选；不代表已经迁移共享 `cinewise` 库，也不构成 Git 提交、推送或共享库发布授权。
