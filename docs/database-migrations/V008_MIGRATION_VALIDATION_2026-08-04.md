# V008 Agent 会话运行表迁移验证记录

## 1. 基本信息

- 迁移：`V008__create_agent_session_run_tables.sql`
- OpenSpec：`openspec/changes/agent-session-run-persistence/`
- 领域 Owner：B
- Owner 最终确认：B 已通过远端提交 `8bfe01f`、`8e14726`、`c9e85ab` 冻结四表字段、状态、索引、CAS、幂等、恢复和 30 天清理规则
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-04
- 目标：A 专用 `cinewise_migration_check` 空库
- 账号：专用 `cinewise_migrator`
- MySQL：8.4.11
- 凭据与主机：仅从被 Git 忽略的 `.env.migration-check` 读取，未写入本记录
- SQL SHA-256：`41E38D53F00C68A82E63F51847E7A27525B68336B176A68592A4990D871E1797`
- Flyway V008 checksum：`-282998062`

## 2. 静态审查与环境隔离

- A 已复核 B 的远端 OpenSpec、Agent 详细设计和四表字段、状态、索引、CHECK、CAS、幂等、恢复与清理规则，并正式分配 V008。
- 本迁移只创建 `agent_session`、`agent_run`、`agent_message`、`agent_run_step`，不创建 `agent_event`、`agent_action`、`agent_feedback`、`agent_tool_call`。
- SQL 不含 `DROP`、`TRUNCATE`、`REPLACE`、无条件 `DELETE`、真实数据、账号或密钥；四表不建立物理外键。
- `CINEWISE_ENV_KIND=migration_check`、数据库名 `cinewise_migration_check`、账号 `cinewise_migrator` 守卫通过。
- 验证前数据库只有 V001 至 V007 的 7 条成功历史和对应 18 张业务表；所有业务表精确行数均为零。
- V007 SQL SHA-256 为 `54247CD850FFC21207B6CE687F1551E5C7050B01C7B1FCB7609C4D1CB11EF8D7`，与 V007 已验证记录一致。
- 在确认目标、历史、已知表和零业务数据后重建专用验证库，并确认表数为零。
- 日常 `.env`、`.env.cloud` 和 `.env.migration-check` 均未修改，三者的 `FLYWAY_ENABLED` 最终仍为 `false`。
- 验证进程只在内存中临时设置 `FLYWAY_ENABLED=true`、禁用种子和订单过期任务，并生成随机认证密钥。
- 未连接或修改共享 `cinewise` 库，未输出或记录主机、密码和连接串。

## 3. 验证制品

- `mvnw.cmd --batch-mode --no-transfer-progress -DskipTests package`：通过。
- 构建后的 JAR 内 V007 SQL SHA-256 与工作区 V007 一致。
- 构建后的 JAR 内 V008 SQL SHA-256 为 `41E38D53F00C68A82E63F51847E7A27525B68336B176A68592A4990D871E1797`，与 B 冻结草案逐字一致。

## 4. 首次 migrate、validate 与重复 migrate

- 最终验证轮次从零表开始，Flyway 先成功校验 8 个迁移，再依次应用 V001 至 V008。
- 首次执行完成后共有 8 条成功历史、0 条失败历史，当前版本为 V008。
- V008 Flyway checksum 为 `-282998062`，验证库共 23 张表，即 22 张业务表和 1 张 Flyway 历史表。
- 使用同一制品重复启动后，Flyway 再次成功校验 8 个迁移，确认当前版本为 V008，并报告 `Schema is up to date. No migration necessary.`。
- non-web 一次性进程在 Flyway 提交后因认证安全壳缺少 Servlet `HttpSecurity` 退出；该错误发生在迁移成功之后，不影响 Flyway 历史、Schema、重复执行或约束验证。

## 5. V008 实际结构

- `agent_session`：10 个字段；主键、2 个唯一键、2 个普通索引、4 个 CHECK。
- `agent_run`：17 个字段；主键、2 个唯一键、2 个普通索引、7 个 CHECK。
- `agent_message`：13 个字段；主键、1 个唯一键、3 个普通索引、5 个 CHECK。
- `agent_run_step`：22 个字段；主键、1 个组合唯一键、2 个普通索引、8 个 CHECK。
- 四表均为 InnoDB，表排序规则均为 `utf8mb4_0900_ai_ci`；数据库默认字符集和排序规则为 `utf8mb4`、`utf8mb4_0900_ai_ci`。
- 实际字段类型、长度、可空性、默认值、`DATETIME(3)` 精度和 `ON UPDATE CURRENT_TIMESTAMP(3)` 与冻结 SQL 一致。
- 四表物理外键数量为零。

## 6. 正反约束用例

以下用例在真实 MySQL 8.4.11 中执行。正例在事务内回滚；反例连接因约束错误结束时事务自动回滚。最终四张 Agent 表测试数据为零行。

| 用例 | 预期 | 结果 |
| --- | --- | --- |
| 四表合法记录及合法自动跳过步骤 | 成功 | 通过 |
| `CLEARED` 会话仍带 `active_run_id` | CHECK 3819 | 通过 |
| 重复 `session_id` | UNIQUE 1062 | 通过 |
| 两个会话占用相同 `active_run_id` | UNIQUE 1062 | 通过 |
| 大写 SHA-256 摘要 | CHECK 3819 | 通过 |
| 终态运行缺少 `finished_at` | CHECK 3819 | 通过 |
| `RUNNING` 运行带 `finished_at` | CHECK 3819 | 通过 |
| 重复 `(user_id, session_id, client_request_id)` | UNIQUE 1062 | 通过 |
| USER 消息使用非 TEXT 类型 | CHECK 3819 | 通过 |
| `retry_count > attempt_count` | CHECK 3819 | 通过 |
| 终态步骤仍为 `recovery_pending=1` | CHECK 3819 | 通过 |
| RUNNING 步骤缺少 `started_at` | CHECK 3819 | 通过 |
| `auto_skipped=1` 且 `skip_reason=NULL` | CHECK 3819 | 通过 |
| 重复 `(run_id, plan_version, node_id)` | UNIQUE 1062 | 通过 |

## 7. 并发与条件更新

- 两个并发客户端以相同 `id + version=0 + status=PENDING` 推进步骤：受影响行数为 `1` 和 `0`，最终步骤为 `RUNNING`、`version=1`、`attempt_count=1`。
- 两个并发客户端以 `active_run_id IS NULL` 占用同一会话：受影响行数为 `1` 和 `0`，最终只有一个活动运行，session version 为 1。
- 两个并发客户端写入相同 `(user_id, session_id, client_request_id)`：一个成功，另一个返回 UNIQUE 1062，最终只有一条运行记录。
- 并发用例完成后已定向清理，测试记录数量为零。

## 8. 仓库质量验证

- 首轮完整 `verify` 曾出现 `ContentControllerIntegrationTest.shouldExposePublicMovieAndCinemaListsWithCContract` 失败：期望 `fallbackType=MOCK`，实际为 `CACHE`。该问题属于内容模块将 Demo 回退写入缓存后错误标记来源，与 V008 SQL 无关。
- D 在提交 `4f956e8` 修正 Demo 回退缓存标识，并通过 PR #39 合入 `origin/dev`；A 使用“最新 `origin/dev` + 冻结 V007 + 冻结 V008”的一次性组合验证副本复验，没有改写当前 V007 分支历史。
- 定向 `ContentControllerIntegrationTest`：4 个测试全部通过，0 个失败、0 个错误、0 个跳过。
- 完整 `mvnw.cmd --batch-mode --no-transfer-progress verify`：164 个测试全部通过，0 个失败、0 个错误、10 个跳过；Checkstyle、SpotBugs、JaCoCo 和 Maven 构建流程成功退出。
- H2 测试上下文成功校验并从空库应用 V001 至 V008，说明最新开发基线能够加载完整迁移链。
- 基于 `origin/dev@4a8db0b` 的干净隔离发布工作树，组合 B 已推送的 OpenSpec `c9e85ab` 与冻结 V008 后重新执行全量门禁；`openspec validate --all --strict --no-interactive` 为 20 项通过、0 项失败，`docker compose --env-file ..\CineWise\.env config --quiet` 通过。
- 最终 JAR 仅包含 V001 至 V008，不包含更高版本；JAR 内 V008 SHA-256 仍为 `41E38D53F00C68A82E63F51847E7A27525B68336B176A68592A4990D871E1797`。

## 9. 非最终尝试说明

- 首次只读预检因 PowerShell 端口参数插值错误在客户端连接前失败，未访问数据库；修正局部变量后守卫和只读预检通过。
- 第一轮约束用例编排错误地把 PowerShell 对象传给 MySQL，所有语句在解析前失败，未形成约束证据且测试数据仍为零；随后改为显式 SQL 字符串并要求匹配 3819/1062 错误码，最终用例全部通过。
- 首轮并发编排虽然数据库结果正确，但外层命令继承非零退出码；随后增加子进程退出码、受影响行数、1062 和最终数据库状态断言，复核结果全部通过。

## 10. 结论

V008 已在 MySQL 8.4.11 的 A 专用空验证库完成首次 migrate、Flyway validate、重复 migrate、历史与 checksum、实际字段、索引、CHECK、字符集、排序规则、无外键、正反约束、CAS、活动运行占用和并发幂等唯一键验证，迁移本身通过。

本结论不代表 V008 已进入共享 `cinewise`，也不构成 Git 提交、推送或共享库发布授权。共享发布前仍需确保 V007 已进入正式迁移链，核对共享库历史、完成备份或恢复能力确认，并由 A 单独授权发布。
