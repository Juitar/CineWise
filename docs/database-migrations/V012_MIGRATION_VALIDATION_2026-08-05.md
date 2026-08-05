# V012 Agent 确认动作表迁移验证记录

## 1. 基本信息

- 迁移：`V012__create_agent_action_table.sql`
- OpenSpec：`agent-confirmed-order-execution`
- 领域 Owner：B / agent
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-05
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`132F028F34C645D19003FC6239F839C515794CA4FD5125DA05D517181349681C`
- Flyway V012 checksum：`1554054250`

## 2. 范围与环境守卫

- V012 仅创建 `agent_action`；不修改 V001 至 V011，不包含种子、账号、密码或业务修数，也不建立物理外键。
- 启动前确认目标数据库精确为 `cinewise_migration_check`、账号精确为 `cinewise_migrator`；日常 `.env` 与共享 `cinewise` 未用于本次验证。
- 验证输入为仓库中的 V001 至 V011，加上经静态审查的 V012 冻结 SQL；不包含旧版 V012 或 V013。

## 3. Flyway 验证

- 验证开始前 Flyway `info` 显示 V001 至 V011 均为 Success，V012 为 Pending。
- 首次 `migrate` 成功将验证库从 V011 升级到 V012；V012 安装时间为 `2026-08-05 19:24:06`。
- `flyway validate` 成功校验 12 个迁移。
- 重复 `migrate` 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`，未重复执行 V012。

## 4. 实际结构与约束

- `agent_action` 已创建，使用 InnoDB、`utf8mb4`、`utf8mb4_0900_ai_ci`；字符串列排序规则不一致数量为 0。
- 实际字段、主键、4 个唯一键、4 个普通索引及 11 条 CHECK 约束均存在。
- 物理外键数量为 0；仅保存跨模块逻辑 ID。

## 5. 正反用例

- 合法的 `PENDING_CONFIRMATION`、`RESULT_UNKNOWN`、`SUCCEEDED` 三类记录均可在事务内插入；随后回滚，测试行残留为 0。
- 以下 14 项非法写入均被 MySQL CHECK 拒绝：非正 ID、非法状态、非正 `plan_version`、非法哈希版本、非法哈希格式、非 JSON Object 命令、状态与写请求 ID 不一致、成功缺结果引用、非成功携带结果引用、结果未知缺恢复提示、恢复期限不等于未知时间加 30 天、非结果未知残留恢复字段、`update_time` 早于 `create_time`、`expire_at` 早于 `create_time`。

## 6. 结论

V012 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、结构、索引、CHECK、字符集/排序规则及正反约束验证，结果通过。该记录不替代共享库发布授权；共享发布须另行记录。
