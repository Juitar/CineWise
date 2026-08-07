# V018 Agent 等待定位状态共享库迁移记录

## 执行范围

- 目标迁移：`V018__add_agent_run_waiting_location_status.sql`
- OpenSpec：`openspec/changes/agent-distance-recommendation-resume/`
- 领域 Owner：B / agent；Flyway 发布负责人：A
- 执行日期：2026-08-07
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`97F219D8FCB6DFF9F7B3B877EFC64C02E599D6C3A9A1C01D88CB958EB5870685`
- Flyway V018 checksum：`77476108`

## 发布前检查

- A 专用验证库已完成首次 migrate、validate、重复 migrate、旧状态兼容、结构、索引、排序规则及正反 CHECK 验证。
- 发布前只读确认共享 `cinewise` 位于 V017，V018 为 Pending。
- 使用独立迁移账号 `cinewise_migrator`；未使用日常应用账号，未修改日常 `FLYWAY_ENABLED` 配置。
- 发布时使用临时 SQL 目录，仅包含 V001–V017 和 B 的 V018 草案；A 侧尚未验证的 V019 草案未纳入本次发布。
- 已生成发布前逻辑备份：`data/migration-backups/cinewise-before-v018-20260807-135103-tables-data-triggers.sql`。
- 备份大小：3,422,057 bytes；SHA-256：`41FFE1F007FF3C7B104FB6FAAEA7680BB53976EBAA724D3A89E692182C3CB55A`。

## 共享库发布结果

- 使用一次性受控 Flyway 进程将共享 `cinewise` 从 V017 升级到 V018，成功应用 1 个迁移。
- V018 安装时间为 `2026-08-07 13:51:48`，成功标志为 1，checksum 为 `77476108`。
- 发布后 `flyway validate` 成功校验 18 个迁移。
- 发布后重复 migrate 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `agent_run` 和 `agent_session` 均保持 InnoDB、`utf8mb4_0900_ai_ci`。
- `chk_agent_run_status_v018` 实际允许 `RUNNING`、`WAITING_LOCATION`、`COMPLETED`、`FAILED`、`CANCELLED`。
- `chk_agent_run_completion_v018` 实际要求 RUNNING/WAITING_LOCATION 的 `finished_at` 为空，三个终态非空。
- 本次只替换 CHECK，不写入业务数据，不改变列、索引或物理关联。

## 结论

V018 已成功发布至共享 `cinewise`，并完成备份、Flyway 历史、发布后 validate、重复执行、CHECK 与排序规则核验。V018 自本次成功执行起冻结；后续结构调整必须新增更高版本的前向迁移。B 的 CAS、五分钟等待恢复、距离上下文清理和旧计划事件拒绝仍需在应用 MySQL 集成测试中完成。
