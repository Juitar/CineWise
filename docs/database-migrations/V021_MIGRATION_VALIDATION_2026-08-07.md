# V021 外部场次异步导入任务迁移验证记录

## 基本信息

- 迁移：`V021__create_ticketing_external_showtime_import_task.sql`
- OpenSpec：`external-showtime-sandbox-ticketing-import`
- 领域 Owner：A / ticketing
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-07
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`EF97499FEE1129CF9D0EC83EA1592C68AE0311CC6226613E62B968479D5190D0`
- Flyway V021 checksum：`648765204`

## 范围与环境守卫

- V021 仅创建 A 自有 `ticketing_external_showtime_import_task` 异步导入任务表。
- 表保存任务 ID、可选客户端幂等键、日期、影院 JSON 范围、状态、租约、计数、截断标记、A 本地场次 ID JSON、稳定错误码、版本、过期时间和审计时间。
- 不保存 D Provider 原始响应或异常正文，不建立物理外键，不写入种子或业务数据。
- 验证使用最新 V001–V020 基线与 V021 草案；目标数据库和账号已核对为 `cinewise_migration_check` / `cinewise_migrator`。

## Flyway 验证

- 执行前 V001–V020 均为 Success，V021 为 Pending。
- 首次 migrate 成功将验证库从 V020 升级到 V021；V021 安装时间为 `2026-08-07 17:56:59`。
- `flyway validate` 成功校验 21 个迁移。
- 重复 migrate 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。
- MySQL 输出 `TINYINT(1)` 整数显示宽度弃用警告；该警告不影响本次迁移成功、CHECK 或结构结果，后续可在独立变更中改为 `TINYINT`。

## 实际结构与约束

- 表为 InnoDB、`utf8mb4_0900_ai_ci`。
- `task_id` 唯一，`client_request_id` 可空且唯一；影院范围和结果场次均为 JSON 数组。
- 状态、计数、租约成对、错误码、版本、过期时间、完成时间顺序和 JSON 数组约束均已实际存在。
- 索引 `idx_ticketing_external_import_recovery(status, lease_until)` 和 `idx_ticketing_external_import_expire(expire_at)` 已创建。

## 正反约束用例

- PENDING、RUNNING、SUCCESS、PARTIAL、FAILED 五种合法状态均可写入；FAILED 零候选计数可写入。
- 空影院数组、租约半填、PENDING 非零计数、完成时间早于开始时间、`truncated` 非 0/1 均被 CHECK 拒绝。
- 测试事务回滚后，任务表无测试残留。

## 结论

V021 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、状态、租约、计数、JSON、时间顺序、索引、字符集和排序规则验证，结果通过。本记录不构成共享库发布授权；异步 Worker、租约恢复、管理员权限和票务导入幂等仍需应用测试完成。
