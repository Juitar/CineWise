# 验证记录

## MySQL 8.4

- V017 的首次迁移、重复迁移、索引、唯一键、CHECK、字符集和清理结果由 A 在独立
  `cinewise_migration_check` 完成，记录见
  `docs/database-migrations/V017_MIGRATION_VALIDATION_2026-08-07.md`；共享库发布记录见
  `docs/database-migrations/V017_SHARED_MIGRATION_2026-08-07.md`。两张表的约束用例均在事务内回滚，
  验证结束后测试行数为 0。
- C 新增 `ProfileDataConsentMySqlIntegrationTest`，只允许连接本机或 GitHub Actions 的
  `cinewise_profile_consent_it`，且账号必须是 `cinewise_ci`。测试库由工作流创建，Job 结束后随
  MySQL 8.4 容器销毁，不连接共享 `cinewise`。
- 可复现命令：

  ```text
  gh workflow run backend-mysql-integration.yml --ref feat/profile-data-consent-and-withdrawal
  bash ./mvnw --batch-mode --no-transfer-progress -Dtest=ProfileDataConsentMySqlIntegrationTest test
  ```

- GitHub Actions 运行 `31147996467` 通过：空库成功执行并校验 17 个迁移，
  `cinewise_profile_consent_it` 成功执行到 V017；画像同意 3 个真实 MySQL 用例全部通过，覆盖
  CAS 版本不匹配、撤回与 outbox 同事务提交、outbox 唯一键失败时整体回滚、失败重试到
  `EXHAUSTED`、人工恢复复用原 `eventId` 并改为 `DELIVERED`。同一运行中的 33 个既有
  MySQL 用例、12 个 Agent MySQL 用例和重复 Flyway 初始化也通过。

## Redis 7.4

- 本机在 2026-08-07 连接共享 Redis `121.40.211.212:6379` 时 TCP 超时，本机 Docker 服务也未启动，
  因此没有在本机共享 Redis 写入测试键。
- 新增 `RedisProfileSummaryCacheIntegrationTest`，验证撤回时删除目标用户的全部画像摘要键，同时保留
  其他用户的键；`RedisProfileSummaryCacheTest` 继续验证 Redis 不可用时不阻断撤回。
- 可复现命令：

  ```text
  gh workflow run backend-redis-integration.yml --ref feat/profile-data-consent-and-withdrawal
  bash ./mvnw --batch-mode --no-transfer-progress verify
  ```

- GitHub Actions 运行 `31147998267` 使用一次性 Redis 7.4.10，完整后端 `verify` 和画像缓存清理
  集成测试通过；Redis 容器及测试键在 Job 结束后销毁。共享 Redis 的网络可用性仍由环境维护人处理，
  完成条件是部署环境能连接该地址并通过健康检查，不影响本次一次性 Redis 功能验收。

## 后端、前端和规范

- 后端工作流 `31147973476` 通过：762 个测试，失败 0、错误 0、跳过 55，编译、Checkstyle、
  SpotBugs 和 JaCoCo 校验通过。
- 前端工作流 `31147973262` 通过：98 个测试文件、480 个测试通过，格式、Lint、类型检查和生产构建通过。
- 本机 `pnpm check` 的格式、Lint、类型检查及 98 个测试文件、480 个测试通过；命令在生产构建阶段达到
  180 秒工具上限，生产构建结果以上述前端工作流为准。
- 本机定向后端测试使用 `-DforkCount=0` 运行 21 个测试，失败 0、错误 0、跳过 3；默认分叉模式受
  Windows JaCoCo 启动错误 `processing of -javaagent failed` 阻断。
- `openspec validate profile-data-consent-and-withdrawal --strict` 与 `git diff --check` 通过。

