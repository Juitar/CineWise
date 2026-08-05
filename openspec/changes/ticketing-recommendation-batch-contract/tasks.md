# 任务

- [x] A 与 D 冻结批量可售场次的 Owner 边界、字段、时效、截断和失败语义。
- [x] A 升级 Application DTO 和服务：100 家影院、`dataAt`、`dataType/source`、60 秒时效。
- [x] A 将 `movie_show.source` 纳入只读投影，并保持数据库侧可售过滤、排序和 201 条探测。
- [x] A 将 306003 的 HTTP 映射更新为 503，并补齐错误语义测试。
- [x] A 补充单元与 MySQL 集成测试：时效、来源、100 家边界、空结果、截断和不可用。
- [x] A 运行 OpenSpec strict、Maven verify 与 diff 检查，并提供 D 的 Java DTO 契约。
- [ ] D 在 A 合入后仅通过 `SaleableShowBatchQueryService` 接入推荐过滤、评分和回归测试。

## 验证记录

- `openspec validate ticketing-recommendation-batch-contract --strict`：通过。
- `backend/.\mvnw.cmd -Dtest=SaleableShowBatchQueryServiceTest,TicketingShowtimeQueryAdapterTest test`：8 项通过。
- `backend/.\mvnw.cmd verify`：通过，覆盖全量编译、测试、Checkstyle、SpotBugs 与 JaCoCo 门禁。
- `git diff --check`：通过。
- `ShowQueryMySqlIntegrationTest` 已更新为新契约；本次未设置 `CINEWISE_MYSQL_IT=true`，因此没有连接或操作任何 MySQL 数据库。
