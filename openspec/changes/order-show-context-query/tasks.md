# Tasks

- [x] 1.1 A 确认个人订单只增加 `movieId`、`cinemaId`、`showStartTime`，不复制 D 的内容事实。（验证：用户确认与 proposal）
- [x] 1.2 确认不新增 Flyway、不修改交易状态机和 `FOR UPDATE` 查询。（验证：design 与差异检查）
- [x] 2.1 新增个人订单只读场次投影，并让列表和详情应用服务返回场次上下文。（Owner: A；验证：应用集成测试）
- [x] 2.2 新增 GET 专用 REST 响应并按业务时区输出时间，保持 POST/取消/恢复契约和交易查询不变。（Owner: A；验证：Controller 集成测试、OpenAPI）
- [x] 3.1 更新固定 JSON 夹具，覆盖成功列表、成功详情、空结果与跨用户查询。（Owner: A；验证：夹具测试）
- [x] 3.2 补充回归测试，确认列表无 N+1、交易锁 SQL 未连接 `movie_show`，且不返回 D 的内容事实。（Owner: A；验证：测试与差异审查）
- [x] 4.1 执行 `mvnw.cmd verify`、`openspec validate order-show-context-query --strict` 和 `git diff --check`。（Owner: A）
- [x] 4.2 向前端消费者提供类型与页面调整说明；不直接改动正在开发的前端工作区。（Owner: A）
