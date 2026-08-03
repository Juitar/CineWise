# V005 共享库迁移记录

## 1. 执行范围

- 目标数据库：云端共享 `cinewise`
- 目标迁移：`V005__create_ticket_order_operation_table.sql`
- 执行负责人：A
- 执行日期：2026-08-02
- 数据库版本：MySQL 8.4.11
- 应用环境：`FLYWAY_ENABLED=false`
- 固定数据：本次不执行种子初始化

## 2. 发布前只读检查

- 使用专用 `cinewise_migrator`，未使用日常 `cinewise_app` 执行结构迁移。
- 共享库执行前共有 13 张表，当前版本为 V004，失败迁移数为零。
- V001 至 V004 checksum 与专用验证库及历史发布记录一致。
- `ticket_order_operation` 表和 V005 Flyway 历史均尚不存在。
- V005 仅新增独立空表，不修改既有表、字段或业务数据，不建立物理外键，属于向后兼容增量迁移。
- A 的最终执行指令作为本次迁移窗口确认。

## 3. 备份和恢复证据

- 有效逻辑备份：`data/migration-backups/cinewise-before-v005-20260802-215700.sql`。
- 备份大小：1,919,656 字节。
- SHA-256：`A2257A14B6C4CE283C6B6D327AC22A493F3564CFF6E6098652E3B0E9D9CD6762`。
- 备份位于 Git 忽略的 `data/` 目录，不得提交或发送给无关成员。
- 已恢复到一次性 `mysql:8.4.11` 容器。
- 恢复结果为 13 张表、4 条成功 Flyway 历史、当前版本 V004、失败迁移数 0。
- 恢复验证完成后，一次性容器已删除。
- 首次带 `--events` 的备份尝试因最小权限账号无 `SHOW EVENTS` 权限失败，该文件不作为有效备份；项目迁移不使用数据库事件或存储过程。

## 4. 共享库迁移结果

Flyway 成功校验 V001 至 V005，并只应用一个待执行迁移：

| 版本 | 描述 | Checksum | 结果 |
| --- | --- | ---: | --- |
| 001 | create movie and cinema tables | -2081649184 | 成功 |
| 002 | create ticketing show tables | 431219885 | 成功 |
| 003 | create ticketing order tables | 83073346 | 成功 |
| 004 | create content snapshot and sync log tables | -861550512 | 成功 |
| 005 | create ticket order operation table | 467504383 | 成功 |

- 首次执行从 V004 升级到 V005，失败迁移数为零。
- 使用相同制品重复执行时，Flyway 校验 5 个迁移并报告无需迁移。
- `.env`、`.env.cloud` 和 `.env.migration-check` 中的 `FLYWAY_ENABLED=false` 均未修改。
- 本次没有执行固定种子或写入业务交易数据。

## 5. 发布后结构检查

- 共享库当前共 14 张表。
- `ticket_order_operation` 排序规则为 `utf8mb4_0900_ai_ci`，记录数为零。
- 主键：`PRIMARY(id)`。
- 唯一键：`uk_order_operation_user_action_key(user_id, action, idempotency_key)`。
- 普通索引：`idx_order_operation_order_action(order_id, action)`。
- CHECK：`chk_order_operation_result_version`。
- 物理外键数量为零。
- 全库表及字符串列排序规则不一致数量均为零。

## 6. 执行说明

迁移通过当前工作区构建制品运行。为让迁移进程一次性退出，本次使用 non-web 启动；Flyway 提交成功后，C 的安全壳因缺少 Servlet `HttpSecurity` 报错。该错误发生在迁移成功之后，不影响 Flyway 历史、Schema 或重复执行结果；本次未修改 C 的认证和安全配置。

## 7. 结论与后续

V005 已成功进入共享 `cinewise`，发布前备份、MySQL 8.4.11 恢复、Flyway 历史、重复执行和发布后结构检查均通过。V005 自本次成功执行后冻结；任何后续结构调整必须新增 V006，禁止修改、改名或删除 V005。

依赖 V005 的应用代码和迁移文件当前仍在本地功能分支，必须完成验证、提交并通过 PR 合入；在此之前不得让其他成员占用 V005。
