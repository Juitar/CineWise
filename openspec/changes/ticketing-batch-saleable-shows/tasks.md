# 任务

- [x] A 冻结批量查询输入、输出、错误、排序、截断和职责边界。
- [x] A 新增批量查询 Application DTO、服务和稳定校验。
- [x] A 新增数据库侧多影院投影、余座 HAVING、稳定排序和有界 LIMIT。
- [x] A 补充单元测试：非法参数、空集合、日期/时间窗口、排序和 truncated。
- [x] A 补充 MySQL 集成测试：多影院多影片、售罄、状态和日期边界。
- [x] A 补充跨模块契约测试，确保 D 仅依赖公开 Application API。
- [x] A 执行 openspec strict、Maven verify、diff 检查并记录结果。

## 验证记录

- `openspec validate ticketing-batch-saleable-shows --strict`：通过。
- `backend/.\mvnw.cmd verify`：通过，包含编译、测试、ArchUnit、Checkstyle、SpotBugs 和 JaCoCo。
- MySQL 8.4 一次性隔离库执行 `ShowQueryMySqlIntegrationTest`：2 个测试通过、0 失败、0 跳过；容器已删除。
- `git diff --check`：通过；本 change 未新增或修改 Flyway SQL。
