# V010 用户画像表迁移验证记录

## 1. 基本信息

- 迁移：`V010__create_profile_tables.sql`
- OpenSpec：`user-profile-management`
- 领域 Owner：D
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-05
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`F1ED5D7409352B1D996D2DB979F6BB86EE1DDFC75A6C55864599F57961311E22`
- Flyway V010 checksum：`1185312571`

## 2. 范围与环境守卫

- V010 仅创建 `user_preference`、`user_profile_tag`、`user_behavior_event`、`profile_write_request` 四张画像表；不修改 V001 至 V009，不包含种子、账号、密码或业务修数。
- 本次只连接专用验证库；启动前确认数据库精确为 `cinewise_migration_check`、账号精确为 `cinewise_migrator`，MySQL 版本为 8.4.11。
- 验证开始前目标库为全新空库：0 张表、0 条 Flyway 历史、4 张 V010 目标表均不存在。
- 日常 `.env`、共享 `cinewise` 库及其业务数据未修改；`.env.migration-check` 的 `FLYWAY_ENABLED=false` 未被持久化修改。受控进程仅在内存中临时开启 Flyway，并禁用种子和后台任务。

## 3. 制品与 OpenSpec

- `openspec validate user-profile-management --strict --no-interactive`：通过。
- `git diff --check`：通过。
- 当前验证 JAR 内的 V010 与冻结 SQL 的 SHA-256 一致。
- 当前 `dev` 未跟踪该 V010 SQL；这是验证完成前 A 的隔离迁移草案状态。本记录不构成提交、推送或共享库发布。

## 4. Flyway 验证

- 首次受控启动成功校验并从空库依次应用 V001 至 V010，共 10 个迁移；最终版本为 V010，`flyway_schema_history` 共 10 条成功记录、0 条失败记录。
- V010 成功记录的 checksum 为 `1185312571`。
- 使用同一 JAR 的第二次受控启动再次成功校验 10 个迁移，并报告 `Schema cinewise_migration_check is up to date. No migration necessary.`，没有重复执行 V010。
- 两次 non-web 进程均在 Flyway 已完成后因现有认证安全壳需要 Servlet `HttpSecurity` 而退出。该退出发生在迁移提交和重复执行检查之后；实际 Flyway 历史和结构复核均成功，未启动持续应用、种子或后台任务。

## 5. 实际结构

- 四张目标表均已创建，均为 InnoDB，表和全部字符串列排序规则均为 `utf8mb4_0900_ai_ci`；字符串列排序规则不一致数量为 0。
- `user_preference`：6 个字段，主键、`deleted_at` 索引及 2 条 CHECK 均存在。
- `user_profile_tag`：15 个字段（包括 `active_flag` 生成列），主键、活动唯一键、查询/清理索引及 10 条 CHECK 均存在。
- `user_behavior_event`：12 个字段，`event_id` 唯一键、用户时间/事件清理索引及 5 条 CHECK 均存在。
- `profile_write_request`：12 个字段，三元幂等唯一键、过期清理索引及 5 条 CHECK 均存在。
- 四张表的物理外键数量为 0；仅保存跨模块逻辑业务 ID。

## 6. 正反约束用例

- 四张表的合法最小写入均成功。
- 非法 `tag_type`、`DELETED` 但 `deleted_at` 为空、`CLICK` 使用非 `MOVIE` 目标、`PAID_ORDER` 缺少订单追溯字段、非法 `request_hash` 均被 MySQL CHECK 拒绝（错误 `3819`）。
- 重复 `event_id` 与重复 `(user_id, operation, idempotency_key)` 分别被唯一键拒绝（错误 `1062`）。
- 所有测试行按固定前缀 ID 定向删除；四张目标表最终均为 0 行。

## 7. 结论与后续

V010 已在 A 专用 MySQL 8.4.11 空验证库完成首次 migrate、重复 migrate、Flyway 历史、实际字段、索引、CHECK、唯一键、无物理外键、字符集/排序规则及正反约束验证，结果通过。

本结论不自动授权发布到共享 `cinewise`，也不自动授权 Git 提交或推送。共享发布前仍需按 `DATABASE_MIGRATION_REVIEW.md` 单独核对共享库历史、备份与恢复能力，并由 A 明确授权。
