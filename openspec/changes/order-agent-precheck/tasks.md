# 任务

- [x] A/B 确认预检是只读 advisory API，写 Tool 仍保留最终复核。
- [x] A 新增预检命令、结果 DTO 和 `CreateOrderTool.validate` 公开入口。
- [x] A 增加票务 Application 层只读座位预检，不调用锁座写 SQL。
- [x] A 补充参数、场次、座位、查询故障和结果字段测试。
- [x] A 补充预检无订单/座位写入及架构边界测试。
- [x] A 执行 OpenSpec strict、Maven verify、MySQL 集成回归和 diff 检查。

## 验证记录

- `openspec validate order-agent-precheck --strict`：通过。
- `backend/.\mvnw.cmd verify`：通过，包含单元、集成、ArchUnit、Checkstyle、SpotBugs 和 JaCoCo。
- MySQL 8.4 一次性隔离库执行 `OrderLifecycleMySqlIntegrationTest`：2 个测试通过、0 失败、0 跳过；容器已删除。
- `git diff --check`：通过；未新增或修改 Flyway。
