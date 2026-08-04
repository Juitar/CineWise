# V009 共享库迁移记录

## 执行范围

- 目标迁移：`V009__create_agent_event_tables.sql`
- OpenSpec：`openspec/changes/agent-interaction-runtime/`
- 领域 Owner：B；最终 OpenSpec 提交 `89f935b`
- 执行负责人：A
- 执行日期：2026-08-05
- 数据库版本：MySQL 8.4.11
- 迁移制品提交：`cd85247eaa9659efc42bb5fb616b19f49aa75edf`
- SQL SHA-256：`A4C94FF78EB53FF83BBD23ECE0A0CA75231B7627B4065BD362520EE9B01FA1B9`
- Flyway V009 checksum：`-1153317324`

## 发布前检查与恢复演练

- OpenSpec 严格校验、Compose 配置检查、后端 `mvnw.cmd verify` 均已通过；最终 SQL 已在空 MySQL 8.4.11 完成首次迁移、重复迁移及正反约束验证。
- 发布前只读确认共享库位于 V008：8 条成功历史、0 条失败历史，V008 checksum 为 `-282998062`，V009 历史和目标表均不存在。
- 已生成单库逻辑备份 `data/migration-backups/cinewise-before-v009-20260805-003537-complete.sql`；大小为 1,950,647 字节，SHA-256 为 `6F7DC10DA7C160A5BCF4EEC761C296EC7400BCD41B58F2ECEB48F6519602CF58`。
- 备份已恢复至全新的 MySQL 8.4.11 一次性容器：23 张表、Flyway V001--V008 共 8 条成功历史、失败历史为 0、V008 checksum 为 `-282998062`；V009 历史及两张目标表均不存在。恢复容器已删除。

## 共享库发布结果

- 使用一次性受控 Flyway 进程，以 `cinewise_migrator` 只应用 V009；Flyway 报告成功应用 1 个迁移。
- 同一冻结制品重复执行：`Schema cinewise is up to date. No migration necessary.`
- 首次和重复进程均在 Flyway 完成后因现有 non-web 安全壳缺少 Servlet `HttpSecurity` 而退出；该已知退出发生在迁移提交之后，未启动持续应用、种子或订单过期任务。实际 history 与结构复核结果为准。

## 发布后结构检查

- 当前最新成功版本为 V009，V009 checksum 为 `-1153317324`，成功记录 1 条，失败历史为 0。
- `agent_event` 与 `agent_event_stream_cursor` 均已创建，均为 InnoDB、`utf8mb4_0900_ai_ci`；两表合计 14 个字段、8 条索引记录、6 个 CHECK，物理外键为 0。
- 两张表在发布后均为 0 行。

## 结论

V009 已成功发布至共享 `cinewise`，并完成恢复演练、重复执行与结构核验。V009 自发布起冻结；后续 Agent 事件结构调整必须新增前向 Flyway 迁移。
