# V008 共享库迁移记录

## 1. 执行范围

- 目标数据库：云端共享 `cinewise`
- 目标迁移：`V008__create_agent_session_run_tables.sql`
- OpenSpec：`openspec/changes/agent-session-run-persistence/`
- 领域 Owner：B；B 通过 `8bfe01f`、`8e14726`、`c9e85ab` 冻结最小四表契约
- 执行负责人：A
- 执行日期：2026-08-04
- 数据库版本：MySQL 8.4.11
- 远端迁移基线：`dev@16bad33`
- SQL SHA-256：`41E38D53F00C68A82E63F51847E7A27525B68336B176A68592A4990D871E1797`
- Flyway V008 checksum：`-282998062`
- 应用环境：`FLYWAY_ENABLED=false`
- 固定数据：`SEED_ENABLED=false`、`AUTH_DEMO_SEED_ENABLED=false`

## 2. 发布前检查

- A 已复核 B 的远端 OpenSpec、Agent 详细设计、字段、状态、索引、CAS、请求幂等、陈旧恢复和 30 天清理规则，并正式分配 V008。
- V008 只新增 `agent_session`、`agent_run`、`agent_message`、`agent_run_step`；不创建未来事件、确认、反馈或工具轨迹表，不修改其他 Owner 的表。
- 基于 `origin/dev@4a8db0b` 的干净隔离工作树只组装 B 的 5 个 OpenSpec 文件、V008 SQL 和验证记录，不包含 Agent Java 实现或其他成员修改。
- `openspec validate --all --strict --no-interactive`：20 项通过、0 项失败。
- `docker compose --env-file ..\CineWise\.env config --quiet`：通过；忽略配置未输出、复制或提交。
- `mvnw.cmd --batch-mode --no-transfer-progress verify`：164 个测试、0 个失败、0 个错误、10 个跳过，构建成功。
- 最终 JAR 仅包含 V001 至 V008；JAR 内 V008 SHA-256 与 B 冻结文件和验证库制品一致。
- V008 以隔离提交 `16bad33` 直提 `dev`，共享数据库执行前仓库已具备可追溯正式制品。
- 使用专用 `cinewise_migrator` 对共享库只读检查：V001 至 V007 checksum 全部一致、失败迁移 0、V008 历史 0、四张目标表 0，数据库和现有对象排序规则无偏差。

## 3. 备份与恢复演练

- 正式迁移前逻辑备份：`data/migration-backups/cinewise-before-v008-20260804-181512-complete.sql`。
- 备份大小：1,940,663 字节。
- SHA-256：`8297A04033C49B7167F4DF31C950702FC2A4B49BE9F53302FFFEBEC984EECA77`。
- 备份位于 Git 忽略的 `data/` 目录，不提交、不推送，也不包含迁移账号凭据。
- 最小权限账号执行正式备份时排除项目迁移未定义的 Event 和 Routine，保留表、数据、视图和 Trigger。
- 备份已恢复到全新的 MySQL 8.4.11 一次性容器；恢复后共 19 张表，Flyway 历史精确为 V001 至 V007、失败迁移 0、V007 checksum 为 `-258544059`，四张 Agent 表不存在。
- 恢复验证完成后一次性容器已删除，未连接或修改共享库。

## 4. 共享库迁移结果

Flyway 使用 `dev@16bad33` 的干净 JAR 成功校验 V001 至 V008，并只应用一个待执行迁移：

| 版本 | 描述 | Checksum | 结果 |
| --- | --- | ---: | --- |
| 001 | create movie and cinema tables | -2081649184 | 成功 |
| 002 | create ticketing show tables | 431219885 | 成功 |
| 003 | create ticketing order tables | 83073346 | 成功 |
| 004 | create content snapshot and sync log tables | -861550512 | 成功 |
| 005 | create ticket order operation table | 467504383 | 成功 |
| 006 | create auth user and login log tables | -2112675263 | 成功 |
| 007 | create travel reminder tables | -258544059 | 成功 |
| 008 | create agent session run tables | -282998062 | 成功 |

- 首次执行从 V007 升级到 V008，Flyway 报告成功应用 1 个迁移。
- 使用同一制品重复执行时，Flyway 成功校验 8 个迁移并报告 `Schema cinewise is up to date. No migration necessary.`。
- `.env` 与 `.env.migration-check` 中的 `FLYWAY_ENABLED=false` 保持不变；日常 `.env` 中 `SEED_ENABLED=false` 保持不变。
- 迁移进程只在当前 PowerShell 进程内临时启用 Flyway、关闭种子与订单过期任务，并使用随机认证密钥。

## 5. 发布后结构检查

- 共享库当前共 23 张表；Flyway 历史 8 条、成功 8 条、失败 0 条、最大安装序号 8。
- `agent_session`、`agent_run`、`agent_message`、`agent_run_step` 均已创建且记录数为零。
- 字段数量分别为 10、17、13、22；四表均为 InnoDB，表和字符串列均为 `utf8mb4_0900_ai_ci`。
- `agent_session`：主键 1 个、唯一索引 2 个、普通索引 2 个、CHECK 4 个。
- `agent_run`：主键 1 个、唯一索引 2 个、普通索引 2 个、CHECK 7 个。
- `agent_message`：主键 1 个、唯一索引 1 个、普通索引 3 个、CHECK 5 个。
- `agent_run_step`：主键 1 个、组合唯一索引 1 个、普通索引 2 个、CHECK 8 个。
- 四表共 24 个 CHECK；物理外键数量为 0，`AUTO_INCREMENT` 字段数量为 0，排序规则不一致数量为 0。

## 6. 执行说明

迁移通过 non-web 一次性应用进程运行。Flyway 成功提交 V008 后，现有认证安全壳因 non-web 环境没有 Servlet `HttpSecurity` 而退出；重复运行也在 Flyway 明确报告无需迁移后出现同一退出。该已知错误发生在 Flyway 完成之后，不影响历史、Schema、重复执行或发布后结构检查，且没有启动持续运行的共享应用、订单任务或种子初始化。

## 7. 结论与后续

V008 已成功进入共享 `cinewise`。B 的契约、远端迁移制品、发布前只读检查、逻辑备份、MySQL 8.4.11 恢复演练、单版本执行、重复执行、四张空表、索引、24 个 CHECK、无外键、无自增和排序规则均已核验。

V008 自本次成功执行后冻结，禁止修改、改名或删除；后续 Agent 事件、确认、反馈、工具轨迹或结构调整必须申请新的前向迁移。B 可基于共享库完成四表持久层和真实 MySQL 联调。
