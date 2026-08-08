# Tasks

- [x] 1.1 更新 `ShowContextView`、查询端口和 MyBatis 映射，支持可空 `Long movieId`。
- [x] 1.2 扩展 `PaymentSucceededEvent` 并同步实时支付事件构造。
- [x] 1.3 同步 PAID 对账补偿事件构造和事件重放语义。
- [x] 1.4 更新支付、出行、画像相关测试与 JSON/事件夹具。
- [x] 1.5 运行 OpenSpec strict、针对性测试、后端 verify 和格式检查。
- [ ] 1.6 D 新增内容模块公开的电影主类型查询能力；画像消费者使用非空 `movieId` 创建或更新 `MOVIE_GENRE/BEHAVIOR` 标签，缺失或不可用时只记录行为摘要。验证：单元测试覆盖有效类型、空或非法 ID、空类型、内容查询失败、24 小时去重和重放；不访问内容持久化层。
- [ ] 1.7 D 执行 OpenSpec strict、相关 Maven 测试、完整 backend verify 和 `git diff --check`。

## Verification record

- `openspec validate payment-event-movie-context --strict`：通过。
- `PaymentIntegrationTest`、`TravelEventContextResolverTest`、对账和事件消费者相关测试：29/29 通过。
- `mvnw.cmd -DskipTests verify`：通过，包含编译、Checkstyle 和 SpotBugs。
- 完整 `mvnw.cmd verify`：业务测试无失败，但 Windows Surefire 在测试完成后因绝对路径 classpath 检查退出；CI Linux 环境待复核。
- `git diff --check`：通过。
