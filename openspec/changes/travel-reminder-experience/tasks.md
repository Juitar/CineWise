## 1. 跨模块前置确认

- [x] 1.1 A 确认 `PaymentSucceededEvent`、`OrderInvalidated` 的字段、事务提交后发布方式和 A→D 的 `ensureTask`、`ensureTaskCancelled` 补偿调用；验证：A、D 在本 change 记录确认，且事件不含邮箱、支付密码、座位明细、精确位置或路线几何。
- [x] 1.2 D 通过公开 Application API 提供 A 发布事件所需的 `cinemaArea` 摘要；验证：A 不访问 D 的 Entity、Mapper、Repository 或表即可组装事件。
- [x] 1.3 C 确认 `EmailDeliveryPort` 的 `deliveryKey` 发送/查询语义、Mock Provider 和已验证邮箱解析边界；验证：D 仅传 `recipientUserId`，重复键可查回原结果。
- [x] 1.4 A 已正式分配 V007，并确认 `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 的字段、索引、保留期、非负计数和终态时间 CHECK 及兼容方案；验证：记录 Owner 确认，未修改已发布迁移。
- [x] 1.5 B、C、D 确认只读出行工具和卡片边界；验证：B 对话读取不创建任务、刷新快照、发送邮件或请求位置，C 只在用户主动操作时发起路线请求。
- [x] 1.6 A 已确认 `PaymentSucceededEvent`、`OrderInvalidated` 的 `String cinemaId` 字段、已发布 V013 的 `travel_task.cinema_id BIGINT NULL` 及 `CHECK (cinema_id IS NULL OR cinema_id > 0)`；验证：V013 结构记录与合并后 MySQL 出行联调记录覆盖支付、退款、补偿、墓碑和任务查询场景。字段不含用户位置、坐标或路线数据。

## 2. 任务、事件与数据基础

- [x] 2.1 D 建立 `travel` 的 api/application/domain/infrastructure 分层、任务状态和值对象；验证：模块架构测试通过，生产源文件中文有效注释率不低于 30%。
- [x] 2.2 D 已提交并确认正式 V007 的三张出行表字段、约束、生命周期和业务语义；A 已在隔离迁移分支创建最终 Flyway SQL，并完成静态复核和专用 MySQL 8.4 验证；验证：见 `docs/database-migrations/V007_MIGRATION_VALIDATION_2026-08-04.md`，已覆盖非负计数、任务 `closed_at`、通知 `resolved_at` 的正反 CHECK、索引和唯一键、追加式建议快照及版本更新与快照写入同事务的竞争/回滚验证。
- [x] 2.3 D 实现 `PaymentSucceededEvent` 的 AFTER_COMMIT 消费、`eventId` 去重、`orderId` 唯一任务创建及 A 补偿共用的 `ensureTask`；验证：`TravelTaskApplicationServiceTest`、`TravelTaskPaymentEventIntegrationTest` 覆盖重复事件、首次消费失败后的补偿、并发创建、提交后消费和回滚不创建，均通过。
- [x] 2.4 D 实现 `OrderInvalidated` 的版本比较、任务取消和建议过期处理；验证：`TravelTaskApplicationServiceTest`、`TravelTaskPaymentEventIntegrationTest` 覆盖退款提交后取消、退款先到的 CANCELLED 墓碑、支付事件随后到达不重开任务、低版本退款后较高版本退款推进墓碑审计字段及两版本并发到达时保留较高版本，均通过。
- [x] 2.5 D 提供本人任务查询、提醒时间更新与只读建议摘要 Application/API 边界；验证：`TravelTaskQueryServiceTest` 覆盖跨用户隐藏、取消任务返回 `207002`、五分钟内刷新返回 `107001`，均通过。
- [ ] 2.6 A、D 在 A 的事件代码合入后实现并验证 `cinemaId` 处理；已验证：合法支付任务、非法支付后的 PAID 补偿、退款先到合法/非法影院 ID、已有任务退款保留原值、迟到支付不重开，以及 V013 对 `0`/负数的 CHECK。D 已在 `BasicRouteService` 中拒绝历史 `cinemaId=NULL` 任务，并补充单元测试和隔离 MySQL 集成测试；待隔离 MySQL 实际执行该路线用例后再勾选。

## 3. 天气建议、快照与提醒投递

- [x] 3.1 D 补齐高德天气的影院行政区码映射：优先本地影院区域，其次本地影院城市和行政区码；不得由模糊地址猜测。映射缺失或查询失败按缓存、Demo、明确不可用处理，且仍生成通用交通建议；验证：`AmapWeatherProviderTest` 覆盖官方完整实况响应、`status=1` 且 `infocode=10000` 成功校验、错误码降级、区域优先、城市备用、映射缺失和 Provider 失败路径。
- [x] 3.2 D 实现天气风险和通用交通建议的确定性规则及建议快照；验证：`TravelAdviceServiceTest` 覆盖天气不可用仍保留通用建议及同版本竞争不覆盖，`TravelTaskPaymentEventIntegrationTest` 覆盖 H2 中 Demo 回退、快照追加和任务进入 `READY`，均通过。
- [x] 3.2.1 D 将建议 REST 响应改为类型化 `TravelAdviceResponse`，保留旧字符串字段的兼容期，并提供 OpenAPI 示例和固定夹具；验证：`travel-public-rest-contracts` 已完成，相关 24 个出行测试通过。
- [x] 3.3 D 实现提醒调度、任务版本抢占和状态转换；验证：`TravelReminderSchedulingServiceTest` 覆盖失败隔离和到期关闭，`TravelTaskPaymentEventIntegrationTest` 覆盖重复调度只生成一条建议快照、取消任务不生成建议和到期任务关闭；共 11 个相关用例通过。
- [x] 3.4 D 接入 C 的 `EmailDeliveryPort`，实现 `deliveryKey` 唯一投递、`PENDING/SENDING/SENT/FAILED/UNKNOWN` 状态和结果查询恢复；验证：`TravelNotificationServiceTest` 覆盖新投递、重复键和 `UNKNOWN` 查询恢复。
- [x] 3.5 D 建立版本化提醒 Mock、回归用例和缺陷清单；验证：`InMemoryEmailProviderAdapter` 按 `deliveryKey` 幂等，提醒服务只向 C 传 `recipientUserId`，异常不自动重发。

## 4. 用户主动真实路线查询

- [x] 4.1 D 已定义基础路线 Command、Provider 和响应摘要，接收一次性设备位置或手动地点；验证：`BasicRouteServiceTest` 覆盖仅确认共享后才调用 Provider，返回值只保留安全摘要。
- [x] 4.2 D 已实现路线隐私与失败处理；验证：`BasicRouteServiceTest` 覆盖未确认不调用 Provider、Provider 不可用返回 `307001`；实现不向 MySQL、缓存、日志、画像、快照或 Agent 轨迹传递起点、路线折线或途经点。
- [x] 4.3 D 实现高德真实路线 Provider，配置 `AMAP_ROUTE_ENABLED`、`AMAP_ROUTE_KEY`、2 秒连接超时和 5 秒读取超时；请求只传本次起点、影院终点和出行方式，支持 `DRIVING` 和 `WALKING`，成功返回路线摘要及 `source=AMAP_ROUTE`。验证：`AmapRouteProviderTest` 与 `RestClientAmapRouteClientTest` 覆盖两种请求路径、未配置、超时、非成功响应和字段不完整；失败时由 `DEMO_ROUTE_V1` 明确降级。
- [ ] 4.4 C、D 联调路线地图渲染与位置授权交互；验证：C 完成定位授权，定位允许、拒绝、超时和手动地点均有可继续路径；路线折线只在本次响应和页面内存使用。

## 5. 工具、接口与跨模块联调

- [x] 5.1 D 实现 `GetWeatherTool`、`GetTravelAdviceTool`、`PlanBasicRouteTool`，仅调用 D Application Service；验证：现有出行工具测试覆盖公开 `ToolContext`/`ToolResult<T>` 适配、只读调用和错误映射，不调用模型、不发布 SSE、不访问 Mapper。本期不将 `SearchNearbyFoodTool` 作为完成条件。
- [x] 5.2 A、D 联调支付事件实际发布、任务创建、订单失效和补偿；验证：`TravelTaskPaymentEventIntegrationTest` 13/13、PAID 补偿 2/2、REFUNDED 补偿 3/3、任务查询 2/2 均在 MySQL 8.4 隔离库通过，覆盖提交后消费、回滚隔离、重复事件、退款取消和补偿结果。H2 默认不运行读取 `cinema_id` 的集成测试。
- [ ] 5.3 B、D 联调对话中的只读建议摘要；D 提供仅接受 `travelTaskId` 受控槽位的结构化 `getTravelAdvice` Tool、定义和执行适配器，B 复核会话槽位和卡片映射；验证：调用不产生 `agent_*`、任务、建议、通知或位置写入，且不存在、无权或非法任务号统一返回 `207001`。
- [ ] 5.4 C、D 联调本人任务、建议、刷新和路线接口；验证：401、403、404、409、422、429、503 与约定错误码、来源时效和降级展示一致。
- [x] 5.4.1 D 提供 C 的提醒时间浏览器联调固定夹具；范围仅为正常更新、`100409` 请求版本不一致、`207003` 条件更新冲突、`207001` 不存在或无权、`207002` 已取消及写结果未知后的 GET 查询恢复。验证：`fixtures/travel/c/reminder-update-fixtures.json` 引用任务详情、建议、PUT 请求和六类固定结果；本期不提供提醒关闭/重新开启字段、接口、夹具或迁移。

## 6. 验证、质量与交付

- [x] 6.1 D 完成任务、Provider、缓存、调度、事件、通知、权限、并发、降级和隐私的单元及集成测试；验证：2026-08-07 在关闭真实天气、路线和邮件 Provider 的配置下执行全量测试，191 个测试类汇总 738 通过、0 失败、0 错误、51 个按环境条件跳过；A/D MySQL 联调结果见 `docs/database-migrations/V013_TRAVEL_INTEGRATION_VALIDATION_2026-08-07.md`，本地离线记录见 `docs/travel-offline-regression-2026-08-07.md`。
- [ ] 6.2 后续由 A、C、D 在 MySQL 8、Redis 和 Mock 邮件 Provider 环境执行事件、缓存、通知恢复和隐私验证；服务器 Key、Compose 实际注入、真实 SMTP 和生产环境验证不阻塞本 change 当前 D 代码与测试完成。验证：不在该任务中用普通应用环境执行 Flyway，真实环境结果附环境、命令和缺陷编号。
- [x] 6.3 D 执行离线演示回归：关闭真实天气、路线和邮件 Provider；验证：2026-08-07 全量后端测试在 `AMAP_WEATHER_ENABLED=false`、`AMAP_ROUTE_ENABLED=false`、`AUTH_MAIL_DELIVERY_ENABLED=false` 下通过，出行测试覆盖天气/路线 Demo 降级、邮件 Mock、来源时效和隐私边界；记录见 `docs/travel-offline-regression-2026-08-07.md`。
- [ ] 6.4 D 执行 `backend\mvnw.cmd verify`、`openspec validate travel-reminder-experience --strict`、`git diff --check` 和变更文件核对；待 MySQL 工作流实际执行 V013 集成测试并处理当前 dev 的既有 Checkstyle 问题后，才能声明构建、测试、架构检查、Checkstyle、SpotBugs、JaCoCo 全部通过。
- [ ] 6.5 D 记录回归结果、缺陷、风险、关联提交和 A/B/C 审查结论；验证：所有已勾选任务均有对应证据，全部完成后再同步主规格并归档。
