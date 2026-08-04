# V007 数据库迁移验证记录

## 1. 基本信息

- 迁移：`V007__create_travel_reminder_tables.sql`
- OpenSpec：`openspec/changes/travel-reminder-experience/`
- 领域 Owner：D
- Owner 最终确认：D 于 2026-08-04 确认当前 SHA-256 对应的 V007 字段、约束、生命周期和业务语义，可以进入发布流程
- Flyway 版本分配、静态审查与验证负责人：A
- 授权与验证日期：2026-08-04
- 数据库：MySQL 8.4.11
- 目标：A 专用 `cinewise_migration_check` 空库
- 账号：专用 `cinewise_migrator`
- 凭据与主机：仅从被 Git 忽略的 `.env.migration-check` 读取，未写入本记录
- SQL SHA-256：`54247CD850FFC21207B6CE687F1551E5C7050B01C7B1FCB7609C4D1CB11EF8D7`
- Flyway V007 checksum：`-258544059`
- 固定数据：`SEED_ENABLED=false`

## 2. 静态审查与环境隔离

- OpenSpec 的 proposal、spec、design、tasks 完整，`openspec validate travel-reminder-experience --strict` 通过。
- D 已完成最终领域确认；确认对象为本记录所列 SQL SHA-256，后续不得在不重新审查和验证的情况下修改 SQL。
- A 确认 V001 至 V006 已占用且保持冻结，正式分配 V007 给三张 D 出行表。
- 候选 SQL 与 D 提交的远端 Git blob 完全一致；A 仅补充 OpenSpec 和受控执行状态文件头后构建验证制品。
- SQL 不含 `DROP`、`TRUNCATE`、`REPLACE`、无条件 `DELETE`、真实数据或密钥；三张表不建立物理外键。
- `CINEWISE_ENV_KIND=migration_check`、目标库名 `cinewise_migration_check`、账号 `cinewise_migrator` 守卫通过。
- 验证前 V001 至 V006 历史均成功，15 张业务表均为零行；随后清空专用验证库并确认表数为零。
- 日常 `.env` 未修改；`.env.migration-check` 中 `FLYWAY_ENABLED=false` 未持久化修改；验证进程只在内存中使用临时随机认证密钥。
- 未连接或修改共享 `cinewise` 库，未读取、输出或记录主机、密码和连接串。

## 3. 首次迁移、校验与重复执行

最终验证轮次从零表开始，Flyway 依次成功应用 V001 至 V007；轮询实际观察到无历史、V001、V005、V007，最终历史 7 条且失败迁移数为零。

| 版本 | 描述 | Checksum | 结果 |
| --- | --- | ---: | --- |
| 001 | create movie and cinema tables | -2081649184 | 成功 |
| 002 | create ticketing show tables | 431219885 | 成功 |
| 003 | create ticketing order tables | 83073346 | 成功 |
| 004 | create content snapshot and sync log tables | -861550512 | 成功 |
| 005 | create ticket order operation table | 467504383 | 成功 |
| 006 | create auth user and login log tables | -2112675263 | 成功 |
| 007 | create travel reminder tables | -258544059 | 成功 |

使用同一制品再次启动后，Flyway 历史在执行前后均为 `7` 条、最大安装序号 `7`、成功记录 `7`，没有重复创建表或改写历史迁移。

验证编排期间有三次未形成最终证据的失败尝试：首次清空命令因 MySQL 预处理语句不接受多条 `DROP TABLE` 而在执行前失败；首次迁移启动因缺少认证模块必填密钥而在 Flyway 前退出且库仍为零表；一次 15 秒观察窗口在 V004 后提前终止。每次均检查失败历史为零，并重新清空专用验证库后才开始上述最终完整轮次。

## 4. V007 实际结构

- `travel_task`：17 个字段；主键 1 个；业务唯一索引 4 个；普通索引 2 个；CHECK 5 个。
- `travel_advice_snapshot`：14 个字段；主键 1 个；业务唯一索引 1 个；普通索引 1 个；CHECK 3 个。
- `travel_notification_log`：18 个字段；主键 1 个；业务唯一索引 1 个；普通索引 3 个；CHECK 6 个。
- 三张表均为 InnoDB，表和字符串列均为 `utf8mb4_0900_ai_ci`；不一致字符串列数量为零。
- 三张表物理外键数量为零，`AUTO_INCREMENT` 字段数量为零。
- 内部主键和逻辑关联使用 `BIGINT`；时间字段使用 `DATETIME(3)`；建议载荷使用 MySQL `JSON`。

## 5. 约束、事务与竞争用例

| 用例 | 预期 | 结果 |
| --- | --- | --- |
| 合法 `PENDING` 任务 | 接受 | 通过 |
| 重复 `task_id` | 唯一键拒绝 | 通过，`ERROR 1062` |
| 重复 `order_id` | 唯一键拒绝 | 通过，`ERROR 1062` |
| `order_version/version/retry_count` 为负数 | CHECK 拒绝 | 通过，`ERROR 3819` |
| 终态缺少 `closed_at` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 非终态带 `closed_at` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 合法建议快照 | 接受 | 通过 |
| 相同任务版本重复快照 | 唯一键拒绝 | 通过，`ERROR 1062` |
| 建议快照布尔值为 2 | CHECK 拒绝 | 通过，`ERROR 3819` |
| 合法 `PENDING` EMAIL 通知 | 接受 | 通过 |
| 重复 `delivery_key` | 唯一键拒绝 | 通过，`ERROR 1062` |
| 非 EMAIL 渠道 | CHECK 拒绝 | 通过，`ERROR 3819` |
| `SENT` 缺少 `resolved_at` | CHECK 拒绝 | 通过，`ERROR 3819` |
| `PENDING` 带 `resolved_at` | CHECK 拒绝 | 通过，`ERROR 3819` |
| 任务版本更新与快照插入后回滚 | 两项都回滚 | 通过；任务版本仍为 0，快照数为 0 |
| 两个会话竞争相同任务版本 | 最多一个抢占成功 | 通过；赢家影响 1 行并提交 1 条快照，竞争者影响 0 行，最终版本为 1且快照数为 1 |

所有 V007 临时测试数据均按专用测试 ID 定向删除；验证结束时三张出行表均为空。

## 6. 仓库质量验证

- `mvnw.cmd package -DskipTests`：通过；构建产物中的 V007 SQL SHA-256 与工作区文件一致。
- Maven 测试中的 H2 MySQL 兼容模式成功从空库应用 V001 至 V007。
- D 已通过提交 `4f956e8` 修复内容模块演示回退缓存标识，并由 `abb3f70` 合入 `dev`。
- 基于 `origin/dev@abb3f70` 的干净隔离工作树重新执行完整 `mvnw.cmd --batch-mode --no-transfer-progress verify`：164 个测试，0 个失败、0 个错误、10 个跳过，构建成功。
- 同一隔离工作树执行 `openspec validate --all --strict --no-interactive`：19 项通过、0 项失败；执行 `docker compose --env-file ..\CineWise\.env config --quiet`：通过，命令仅读取现有忽略配置且未输出或复制凭据。
- 最终 JAR 仅包含 V001 至 V007，不包含 V008；JAR 内 V007 SHA-256 为 `54247CD850FFC21207B6CE687F1551E5C7050B01C7B1FCB7609C4D1CB11EF8D7`，与 D 确认及 MySQL 8.4.11 验证制品一致。

## 7. 结论

V007 已在 MySQL 8.4.11 的 A 专用空验证库完成首次迁移、重复迁移、历史与 checksum、实际字段、索引、CHECK、字符集、排序规则、唯一键、事务回滚和版本竞争验证，迁移本身通过。

本结论仅适用于 SHA-256 为 `54247CD850FFC21207B6CE687F1551E5C7050B01C7B1FCB7609C4D1CB11EF8D7` 的 V007 文件；不代表已经迁移共享 `cinewise` 库，也不构成 Git 提交、推送或共享库发布授权。共享发布前仍须完成历史/checksum 只读核对、备份恢复验证、迁移窗口确认和单独授权。
