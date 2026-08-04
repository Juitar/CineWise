# V007 共享库迁移记录

## 1. 执行范围

- 目标数据库：云端共享 `cinewise`
- 目标迁移：`V007__create_travel_reminder_tables.sql`
- OpenSpec：`openspec/changes/travel-reminder-experience/`
- 领域 Owner：D；D 已确认当前 SQL 的字段、约束、生命周期和业务语义
- 执行负责人：A
- 执行日期：2026-08-04
- 数据库版本：MySQL 8.4.11
- 远端基线：`dev@3520378`
- SQL SHA-256：`54247CD850FFC21207B6CE687F1551E5C7050B01C7B1FCB7609C4D1CB11EF8D7`
- Flyway V007 checksum：`-258544059`
- 应用环境：`FLYWAY_ENABLED=false`
- 固定数据：`SEED_ENABLED=false`、`AUTH_DEMO_SEED_ENABLED=false`

## 2. 发布前检查

- D 的内容模块修复 `4f956e8` 已由 `abb3f70` 合入 `dev`；基于该干净基线只组装 V007 SQL、验证记录和对应 OpenSpec tasks。
- `openspec validate --all --strict --no-interactive`：19 项通过、0 项失败。
- `docker compose --env-file ..\CineWise\.env config --quiet`：通过；忽略配置只被读取，未输出、复制或提交。
- 完整 `mvnw.cmd --batch-mode --no-transfer-progress verify`：164 个测试、0 个失败、0 个错误、10 个跳过，构建成功。
- 最终 JAR 仅包含 V001 至 V007，不包含 V008；JAR 内 V007 SHA-256 与 D 确认值一致。
- V007 以隔离提交 `3520378` 直提 `dev`，未夹带 Java、前端、V008 或其他成员修改。
- 使用专用 `cinewise_migrator` 对共享库只读检查：V001 至 V006 checksum 全部一致、失败迁移 0、V007 历史 0、三张目标表 0。
- 数据库、已有表和字符串列的排序规则均为 `utf8mb4_0900_ai_ci`。

## 3. 备份与恢复演练

- 正式迁移前逻辑备份：`data/migration-backups/cinewise-before-v007-20260804-175650-complete.sql`。
- 备份大小：1,934,134 字节。
- SHA-256：`D54228F48C2E7B374FD78A4881D859AEE389CFB1A6D97004D0E387F245FCC320`。
- 备份位于 Git 忽略的 `data/` 目录，不提交、不推送，也不包含迁移账号凭据。
- 最小权限账号无 `SHOW EVENTS` 权限，第一次包含 `--events` 的备份命令在该只读步骤失败，未修改共享库；正式备份排除项目迁移未定义的 Event 和 Routine，保留表、数据、视图和 Trigger。
- 正式备份已恢复到全新的 MySQL 8.4.11 一次性容器；恢复后共有 16 张表，Flyway 历史精确为 V001 至 V006，失败迁移 0，V006 checksum 为 `-2112675263`，三张出行表不存在。
- 前两次恢复尝试使用 `mysqladmin ping` 过早判断容器可用，导入在权限初始化完成前被拒绝；容器均立即删除，未连接或修改共享库。最终改用真实 `SELECT 1` 认证探测后完整恢复并核验通过，一次性容器随后删除。

## 4. 共享库迁移结果

Flyway 使用 `dev@3520378` 的干净 JAR 成功校验 V001 至 V007，并只应用一个待执行迁移：

| 版本 | 描述 | Checksum | 结果 |
| --- | --- | ---: | --- |
| 001 | create movie and cinema tables | -2081649184 | 成功 |
| 002 | create ticketing show tables | 431219885 | 成功 |
| 003 | create ticketing order tables | 83073346 | 成功 |
| 004 | create content snapshot and sync log tables | -861550512 | 成功 |
| 005 | create ticket order operation table | 467504383 | 成功 |
| 006 | create auth user and login log tables | -2112675263 | 成功 |
| 007 | create travel reminder tables | -258544059 | 成功 |

- 首次执行从 V006 升级到 V007，Flyway 报告成功应用 1 个迁移。
- 使用同一制品重复执行时，Flyway 成功校验 7 个迁移并报告 `Schema cinewise is up to date. No migration necessary.`。
- `.env` 与 `.env.migration-check` 中的 `FLYWAY_ENABLED=false` 保持不变；日常 `.env` 中 `SEED_ENABLED=false` 保持不变。
- 迁移进程只在当前 PowerShell 进程内临时启用 Flyway、关闭种子并使用随机认证密钥，没有持久化连接信息或密钥。

## 5. 发布后结构检查

- 共享库当前共 19 张表；Flyway 历史 7 条、成功 7 条、失败 0 条、最大安装序号 7。
- `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 均已创建且记录数为零。
- 字段数量分别为 17、14、18；三张表均为 InnoDB，表和字符串列均为 `utf8mb4_0900_ai_ci`。
- `travel_task`：主键 1 个、业务唯一索引 4 个、普通索引 2 个、CHECK 5 个。
- `travel_advice_snapshot`：主键 1 个、业务唯一索引 1 个、普通索引 1 个、CHECK 3 个。
- `travel_notification_log`：主键 1 个、业务唯一索引 1 个、普通索引 3 个、CHECK 6 个。
- 三张表共 14 个 CHECK；物理外键数量为 0，`AUTO_INCREMENT` 字段数量为 0，排序规则不一致数量为 0。

## 6. 执行说明

迁移通过 non-web 一次性应用进程运行。Flyway 成功提交 V007 后，现有认证安全壳因 non-web 环境没有 Servlet `HttpSecurity` 而退出；重复运行也在 Flyway 明确报告无需迁移后出现同一退出。该已知错误发生在 Flyway 完成之后，不影响历史、Schema、重复执行或发布后结构检查，且没有启动持续运行的共享应用或执行种子初始化。

## 7. 结论与后续

V007 已成功进入共享 `cinewise`。远端迁移制品、发布前只读检查、逻辑备份、MySQL 8.4.11 恢复演练、单版本执行、重复执行、三张空表、索引、14 个 CHECK、无外键、无自增和排序规则均已核验。

V007 自本次成功执行后冻结，禁止修改、改名或删除；任何后续结构调整必须申请新的向前迁移版本。D 可基于共享库完成出行提醒持久层和真实 MySQL 联调。
