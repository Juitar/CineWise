# Tasks

- [x] 1.1 更新 `ShowContextView`、查询端口和 MyBatis 映射，支持可空 `Long movieId`。
- [x] 1.2 扩展 `PaymentSucceededEvent` 并同步实时支付事件构造。
- [x] 1.3 同步 PAID 对账补偿事件构造和事件重放语义。
- [x] 1.4 更新支付、出行、画像相关测试与 JSON/事件夹具。
- [x] 1.5 运行 OpenSpec strict、针对性测试、后端 verify 和格式检查。
- [x] 1.6 D 新增内容模块公开的电影主类型查询能力；画像消费者使用非空 `movieId` 创建或更新 `MOVIE_GENRE/BEHAVIOR` 标签，缺失或不可用时只记录行为摘要。验证：单元测试覆盖有效类型、空或非法 ID、空类型、内容查询失败、24 小时去重和重放；不访问内容持久化层。
- [ ] 1.7 D 执行 OpenSpec strict、相关 Maven 测试、完整 backend verify 和 `git diff --check`。

## Verification record

- `openspec validate payment-event-movie-context --strict`：通过。
- `ProfileBehaviorRecorderTest`、`MovieGenreQueryServiceTest`：15/15 通过，覆盖有效类型、空或非法 `movieId`、空类型、内容查询异常、24 小时去重与同一支付事件重放。
- `backend/mvnw.cmd -DskipTests verify`：已实际执行但在 Checkstyle 失败；仅剩 `AgentConversationSlotService` 的 4 处既有大括号规则违规，不属于本 change。
- 完整 `backend/mvnw.cmd verify`：已实际执行但失败；`PaymentIntegrationTest`、`PaymentMovieIdNullableIntegrationTest`、`RefundIntegrationTest` 和 `ProfilePaymentEventFlowIntegrationTest` 的 H2 测试库缺少 `cinema_id` 列或 `sys_profile_data_consent` 表。该测试库结构不属于本 change，1.7 保持未完成。
- `git diff --check`：通过。
