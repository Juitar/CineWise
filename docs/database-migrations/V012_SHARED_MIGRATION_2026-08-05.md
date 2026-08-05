# V012 Agent 确认动作表共享库迁移记录

## 执行范围

- 目标迁移：`V012__create_agent_action_table.sql`
- OpenSpec：`openspec/changes/agent-confirmed-order-execution/`
- 领域 Owner：B / agent；Flyway 发布负责人：A
- 执行日期：2026-08-05
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`132F028F34C645D19003FC6239F839C515794CA4FD5125DA05D517181349681C`
- Flyway V012 checksum：`1554054250`

## 发布前检查

- A 专用验证库已完成首次 migrate、validate、重复 migrate、结构、字符集/排序规则与正反 CHECK 用例验证。
- 发布前只读确认共享 `cinewise` 位于 V011，V001 至 V011 均为成功历史，V012 为 Pending。
- 使用独立迁移账号 `cinewise_migrator`；未使用日常应用账号 `cinewise_app`，未修改日常 `FLYWAY_ENABLED` 配置。
- 本次执行前未取得可审计的 V011 状态备份与恢复演练证据。此项为发布流程记录缺口，不能通过事后记录补充为“已完成”；应由 A 核验服务器自动/手工备份是否覆盖 `2026-08-05 19:30:48` 之前的时间点。

## 共享库发布结果

- 使用一次性受控 Flyway 进程将共享 `cinewise` 从 V011 升级到 V012；Flyway 成功应用 1 个迁移。
- V012 安装时间为 `2026-08-05 19:30:48`，成功标志为 1，checksum 为 `1554054250`。
- 发布后 `flyway validate` 成功校验 12 个迁移。
- 发布后重复 `migrate` 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `agent_action` 已创建，表引擎为 InnoDB，表排序规则为 `utf8mb4_0900_ai_ci`。
- V012 仅新增结构，未写入 `agent_action` 业务数据。

## 结论

V012 已成功发布至共享 `cinewise`，并完成发布后的 Flyway 历史、校验、重复执行及表排序规则核验。V012 自本次成功执行起冻结；后续任何结构调整必须新增向前 Flyway 迁移。

本记录如实保留发布前备份/恢复证据待补核验这一流程缺口，不将其表述为已完成。
