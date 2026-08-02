# V004 数据库迁移验证记录

## 1. 基本信息

- 迁移：`V004__create_content_snapshot_and_sync_log_tables.sql`
- OpenSpec：`openspec/changes/d-demo-content-recommendation-baseline/`
- Owner：D
- 审核与执行：A
- 授权与验证日期：2026-08-02
- 数据库：MySQL 8.4.11
- 目标：A 专用 `cinewise_migration_check` 空库
- 账号：专用 `cinewise_migrator`
- 凭据与主机：仅从被 Git 忽略的 `.env.migration-check` 读取，未写入本记录
- 固定数据：`SEED_ENABLED=false`

## 2. 环境隔离证据

- `CINEWISE_ENV_KIND=migration_check` 守卫通过。
- 目标库名、专用账号、独立密码及与共享库不同的数据库名均通过检查。
- 日常 `.env` 的 `FLYWAY_ENABLED=false` 未修改。
- 执行前查询 `information_schema.tables`，目标库表数为 0。
- 本次未使用共享 `cinewise` 库，未执行演示种子。

## 3. 迁移结果

Flyway 从空库按顺序执行并成功应用 4 个迁移：

| 顺序 | 版本 | 描述 | Checksum | 结果 |
| ---: | --- | --- | ---: | --- |
| 1 | 001 | create movie and cinema tables | -2081649184 | 成功 |
| 2 | 002 | create ticketing show tables | 431219885 | 成功 |
| 3 | 003 | create ticketing order tables | 83073346 | 成功 |
| 4 | 004 | create content snapshot and sync log tables | -861550512 | 成功 |

首次完成后，Flyway 报告当前版本为 `004`。使用同一当前制品再次启动后：

- 成功校验 4 个迁移；
- 当前版本仍为 `004`；
- 报告 `Schema is up to date. No migration necessary.`；
- 没有重复建表或改写历史版本。

## 4. 结构检查

- 表总数为 13：12 张业务表和 1 张 `flyway_schema_history`。
- 所有表的排序规则均为 `utf8mb4_0900_ai_ci`，不一致数量为 0。
- `external_data_snapshot` 的主键、`uk_external_snapshot`、`idx_external_expire` 均存在。
- `data_sync_log` 的主键、`uk_sync_request`、两个查询索引及 `idx_sync_cleanup_create_time` 均存在。
- V004 的 6 个 CHECK 均存在：
  - `chk_external_snapshot_expire_time`
  - `chk_sync_counts_nonnegative`
  - `chk_sync_processed_count`
  - `chk_sync_status`
  - `chk_sync_status_count_relation`
  - `chk_sync_completion`

## 5. 数据约束验证

共执行 16 个正反用例，全部符合预期：

| 用例 | 预期 | 结果 |
| --- | --- | --- |
| 合法 T11 快照 | 接受 | 通过 |
| T11 三字段唯一键重复 | 拒绝 | 通过 |
| T11 `expire_time < data_time` | 拒绝 | 通过 |
| 合法 `RUNNING` | 接受 | 通过 |
| `RUNNING` 设置 `finished_at` | 拒绝 | 通过 |
| 统计数为负 | 拒绝 | 通过 |
| 已处理数量超过总数 | 拒绝 | 通过 |
| 非法同步状态 | 拒绝 | 通过 |
| 合法 `SUCCESS` | 接受 | 通过 |
| `SUCCESS` 含失败数量 | 拒绝 | 通过 |
| 合法 `FAILED` | 接受 | 通过 |
| `FAILED` 含成功数量 | 拒绝 | 通过 |
| 合法 `PARTIAL` | 接受 | 通过 |
| `PARTIAL` 未同时包含成功和失败 | 拒绝 | 通过 |
| 终态结束时间早于开始时间 | 拒绝 | 通过 |
| T12 `provider + request_id` 重复 | 拒绝 | 通过 |

测试完成后已删除 `provider=TEST` 的测试数据，残留测试记录数为 0。

## 6. 项目质量验证

执行 `backend\mvnw.cmd verify`：

- 14 个测试通过，失败 0、错误 0；
- 1 个需要单独启用真实 MySQL 测试环境的集成测试被跳过；
- Checkstyle 与 SpotBugs 门禁通过；
- 测试环境也成功识别并执行 V001 至 V004。

## 7. 异常与处理

第一次 Docker 镜像验证发现镜像 JAR 中仍是旧版 V004，数据库缺少两个新增 CHECK。该轮结果已判定无效，没有作为最终证据。

随后采取以下处理：

1. 从当前工作区重新执行 Maven `clean package`；
2. 直接检查生成 JAR，确认包含全部新增约束；
3. 清空仅用于迁移验证的 `cinewise_migration_check`；
4. 使用该 JAR 从空库重新执行全部迁移和验证矩阵。

本记录中的 checksum、结构和用例结果均来自重新执行后的当前 JAR。

## 8. 结论

V001 至 V004 在 MySQL 8.4.11 专用空库迁移成功；重复 migrate、Flyway 历史、字符集、排序规则、索引、唯一键和 V004 CHECK 验证全部通过。V004 自本次成功执行后冻结，不得再修改；后续结构调整必须新增向前迁移。
