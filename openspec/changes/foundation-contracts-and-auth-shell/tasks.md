# Tasks: foundation-contracts-and-auth-shell

- [x] 创建 Java 21 / Spring Boot 3.5 Maven 工程与 Maven Wrapper。
- [x] 创建 A/B/C/D 领域包、分层说明和负责人边界。
- [x] 实现 `Result<T>`、`PageResult<T>`、错误码接口与全局异常处理。
- [x] 实现 traceId 过滤器、日志上下文与单元测试。
- [x] 配置 OpenAPI、MyBatis-Plus、Flyway、业务 Clock、CORS 和调度线程池。
- [x] 创建 `CurrentUserAccessor` 与默认拒绝安全壳。
- [x] 添加 ArchUnit、Checkstyle、SpotBugs、JaCoCo 与 GitHub Actions 门禁。
- [x] 添加 Dockerfile、Compose、环境变量模板与骨架接入文档。
- [ ] C 接入 JWT Cookie、CSRF、401/403 和 ADMIN 规则并通过 BE-AUTH-01/02。
- [ ] 在安装 Docker 的环境验证 MySQL/Redis/后端容器健康、迁移和重启。
- [ ] 导出首版 `/v3/api-docs`，由 A/B/C/D 共同审查公共 Schema。

## 数据库字段确认与基线迁移

- [ ] C 对照认证详设确认 T01-T05 的全部字段、可空性、唯一约束、验证码/邀请码条件消费和保留期。
- [ ] D 对照画像与外部数据详设确认 T06-T12 的全部字段、Provider业务唯一键、行为幂等、来源时间和清理规则。
- [ ] A 对照票务交易详设确认 T13-T20 的全部字段、状态、条件更新、幂等键、金额快照和不物理删除规则。
- [ ] B 对照Agent详设确认 T21-T28 的全部字段、SSE游标、运行/节点唯一约束和30天清理规则。
- [ ] D 对照出行详设确认 T29-T31 的全部字段、`order_id`唯一约束、投递幂等键、任务租约和保留期。
- [ ] A 确认 T32-T33 的全部字段、配置安全边界、审计可空性和索引。
- [ ] A 汇总复核 T01-T33：除`agent_event.event_id`外统一使用BIGINT雪花ID，跨模块只建立逻辑关联，金额使用`DECIMAL(10,2)`，时间使用`DATETIME(3)`，可变重要表包含必要的`version`和审计字段。
- [ ] 全部Owner确认后，由A更新数据库OpenSpec spec并分配唯一Flyway版本；不得直接修改已经共享或执行的历史迁移。
- [ ] A在空MySQL 8数据库执行基线迁移，验证33张表、主键、唯一约束、普通索引、CHECK约束和Flyway版本记录。
- [ ] A验证迁移重复执行不会重复建表或修改历史版本，随后导出结构快照供A/B/C/D复核。
- [ ] A执行固定种子初始化两次，确认8至12部影片、3至5家影院、每家2个影厅和未来7天场次不会重复生成，也不会覆盖订单、支付、电子票或退票状态。
