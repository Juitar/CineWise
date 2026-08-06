# V015 扩展 Agent 运行步骤确认状态共享库迁移记录

## 执行范围

- 目标迁移：`V015__extend_agent_run_step_confirmation_states.sql`
- OpenSpec：`openspec/changes/agent-multi-tool-orchestration-and-replanning/`
- 领域 Owner：B / agent；Flyway 发布负责人：A
- 执行日期：2026-08-06
- 数据库版本：MySQL 8.4.x
- SQL SHA-256：`74E56C4E3B31C40DE2FD7ECF8F3EF4DD9D940C409ECDEB856BAD9183B2C0DBBA`
- Flyway V015 checksum：`1782125149`

## 发布前检查

- A 专用验证库已完成首次 migrate、validate、重复 migrate、实际约束、排序规则与正反 CHECK 用例验证。
- 发布前只读确认共享 `cinewise` 位于 V014，V015 为 Pending。
- 使用独立迁移账号 `cinewise_migrator`；未使用日常应用账号 `cinewise_app`。

## 共享库发布结果

- 使用一次性受控 Flyway 进程将共享 `cinewise` 从 V014 升级到 V015；Flyway 成功应用 1 个迁移。
- V015 安装时间为 `2026-08-06 14:37:52`，成功标志为 1，checksum 为 `1782125149`。
- 发布后重复 `migrate` 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `_v015` 后缀的 4 个 Check 约束已成功覆盖原约束生效。
- `agent_run_step` 表排序规则保持 `utf8mb4_0900_ai_ci`。
- 本次仅替换兼容的 CHECK，已自动向下兼容现有合法数据。

## 结论

V015 已成功发布至共享 `cinewise`，并完成 Flyway 历史、重复执行、CHECK 与排序规则核验。V015 自本次成功执行起冻结；后续结构调整必须新增更高版本的前向 Flyway 迁移。
