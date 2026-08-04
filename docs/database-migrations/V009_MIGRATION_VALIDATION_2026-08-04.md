# V009 Agent 事件表迁移验证记录

## 1. 基本信息

- 迁移：`V009__create_agent_event_tables.sql`
- OpenSpec：`agent-interaction-runtime`，B 最终规划提交 `89f935b`
- 领域 Owner：B
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-04
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`A4C94FF78EB53FF83BBD23ECE0A0CA75231B7627B4065BD362520EE9B01FA1B9`
- Flyway V009 checksum：`-1153317324`

## 2. 范围与环境守卫

- V009 只创建 `agent_event`、`agent_event_stream_cursor`，不修改 V001 至 V008，不写其他 Owner 的表。
- 两表只保存逻辑业务标识，不建立物理外键；不包含种子、账号、密码、连接信息或业务修数。
- `agent_event.event_id` 是后端规范明确允许的 SSE 游标自增例外；其他主键规则不变。
- `.env.migration-check` 的数据库名和账号分别精确为 `cinewise_migration_check`、`cinewise_migrator`，且文件被 Git 忽略。
- 专用验证库中非最终 V009 草案的业务表均为 0 行、历史可完全重建；最终验证前按环境守卫重建为空库，未保留旧 checksum。
- 日常 `.env`、共享库和业务数据未在验证阶段修改；持久化 `FLYWAY_ENABLED` 始终为 `false`。

## 3. 制品与 OpenSpec

- `openspec validate agent-interaction-runtime --strict --no-interactive`：通过。
- `git diff --check`：通过。
- `mvnw.cmd --batch-mode --no-transfer-progress -DskipTests package`：成功。
- 构建后 JAR 内 V009 SHA-256 与冻结 SQL 一致。
- `mvnw.cmd --batch-mode --no-transfer-progress verify`：253 个测试、0 个失败、0 个错误、14 个跳过；Checkstyle、SpotBugs、JaCoCo 和构建流程成功。

## 4. Flyway 验证

### 4.1 A 专用验证库空库验证

- 专用验证库经空数据、已知历史和目标身份守卫确认后重建为空库。
- Flyway 成功校验并从空库应用 V001 至 V009，共 9 个迁移。
- V009 历史为一条、`success=1`、checksum `-1153317324`，失败历史为 0。
- 使用同一制品重复运行，Flyway 再次成功校验 9 个迁移并报告无需迁移。

### 4.2 全新空 MySQL 基线

- 在一次性 MySQL 8.4.11 容器的空 Schema 中，Flyway 成功依次应用 V001 至 V009，共 9 个迁移。
- 使用同一制品重复运行，Flyway 成功校验 9 个迁移，当前版本 V009，并报告无需迁移。
- 一次性容器已删除，未接触共享库。

## 5. 实际结构

- `agent_event`：7 个字段；自增主键；`session_id + event_id`、`run_id + event_id`、`expire_at + event_id` 三个普通索引；3 个 CHECK。
- `agent_event_stream_cursor`：7 个字段；`session_id` 主键；3 个 CHECK。
- 两表均为 InnoDB，表和字符串列排序规则均为 `utf8mb4_0900_ai_ci`。
- 实际类型、长度、可空性、默认值、`DATETIME(3)` 和游标更新时间规则与冻结 SQL 一致。
- `payload_json` 由 JSON 列保证合法 JSON，并以 MySQL/H2 均支持的标准化文本正则 CHECK 保证是非空对象；合法对象通过，`{}` 和数组均返回 CHECK 3819。
- 两表物理外键数量为 0；自增列数量为 1，且仅为 `agent_event.event_id`。

## 6. 正反约束用例

- 13 个持久化事件类型全部插入成功。
- 合法事件、零水位游标和有保留区间游标插入成功。
- `stream.reset`、空 JSON 对象、JSON 数组、事件倒置到期时间均返回 CHECK 3819。
- 负水位线、首个保留 ID 为 0、首个保留 ID 大于最高水位、负 version、游标倒置到期时间均返回 CHECK 3819。
- 重复 `agent_event_stream_cursor.session_id` 返回 UNIQUE 1062。
- 所有验证数据已定向清理；两张目标表最终均为 0 行。

## 7. 边界与后续验证

- 会话行锁、事件事实与游标同事务提交、断线续传和 16 KiB 类型化载荷校验属于 B 的应用集成测试，不用 DDL 用例替代。
- non-web 一次性进程在 Flyway 成功提交后因现有应用启动依赖退出；该错误发生在迁移之后，不影响 Flyway 历史、Schema 或重复执行。
- 非最终草案曾使用 MySQL 专用 `JSON_TYPE/JSON_LENGTH`，真实 MySQL 验证通过但 H2 全量测试无法加载；共享发布前已改为等价可移植 CHECK，并重新构建、重建专用验证库及复验，非最终 checksum `1713060630` 作废。

## 8. 结论

V009 已通过冻结 SQL、A 专用 MySQL 8.4.11 前向升级、全新空 MySQL 基线、重复 migrate、实际结构、索引、CHECK、字符集、无外键及正反约束验证。该结论不自动构成共享库发布授权；共享发布仍需核对正式远端制品、共享历史、当前备份和恢复能力。
