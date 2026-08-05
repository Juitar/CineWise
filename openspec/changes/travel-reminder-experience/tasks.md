## 1. 跨模块前置确认

- [x] 1.1 A 确认 `PaymentSucceededEvent`、`OrderInvalidated` 的字段、事务提交后发布方式和 A→D 的 `ensureTask`、`ensureTaskCancelled` 补偿调用；验证：A、D 在本 change 记录确认，且事件不含邮箱、支付密码、座位明细、精确位置或路线几何。
- [x] 1.2 D 通过公开 Application API 提供 A 发布事件所需的 `cinemaArea` 摘要；验证：A 不访问 D 的 Entity、Mapper、Repository 或表即可组装事件。
- [x] 1.3 C 确认 `EmailDeliveryPort` 的 `deliveryKey` 发送/查询语义、Mock Provider 和已验证邮箱解析边界；验证：D 仅传 `recipientUserId`，重复键可查回原结果。
- [x] 1.4 A 已正式分配 V007，并确认 `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 的字段、索引、保留期、非负计数和终态时间 CHECK 及兼容方案；验证：记录 Owner 确认，未修改已发布迁移。
- [x] 1.5 B、C、D 确认只读出行工具和卡片边界；验证：B 对话读取不创建任务、刷新快照、发送邮件或请求位置。路线改为仅由 C 页面在用户主动操作后发起，具体页面确认见 1.8。
- [ ] 1.6 D 确认真实天气 Provider 的服务商、授权主体、部署 Key、配额、超时和启用开关；验证：确认记录不包含明文 Key。
- [x] 1.7 A、D 已确认 `PaymentSucceededEvent`、`OrderInvalidated` 同步新增 `String cinemaId`，并正式分配 V013：`travel_task.cinema_id BIGINT NULL` 不建物理外键，CHECK 仅允许 `NULL` 或正数；验证：新支付任务写入非空影院 ID，历史任务和影院 ID 非法的退款先到墓碑可保持 `NULL` 且路线不可用，事件不含用户精确位置或路线几何。
- [x] 1.8 C、D 已确认路线页面的首次询问、后续再次请求、浏览器一次性定位授权、请求体不记日志和静态地图图片流展示；验证：页面只在用户主动确认后发起路线请求，拒绝定位不调用后端高德接口，图片响应不跳转高德地址且 `Cache-Control: no-store`。
- [ ] 1.9 D 确认高德路线和静态地图的授权主体、`AMAP_WEB_SERVICE_KEY`、基础 LBS 配额、超时、启用开关和允许域名；验证：确认记录不包含明文 Key，路线和静态地图共用 Web 服务 Key。

## 2. 任务、事件与数据基础

- [x] 2.1 D 建立 `travel` 的 api/application/domain/infrastructure 分层、任务状态和值对象；验证：模块架构测试通过，生产源文件中文有效注释率不低于 30%。
- [x] 2.2 D 已提交并确认正式 V007 的三张出行表字段、约束、生命周期和业务语义；A 已在隔离迁移分支创建最终 Flyway SQL，并完成静态复核和专用 MySQL 8.4 验证；验证：见 `docs/database-migrations/V007_MIGRATION_VALIDATION_2026-08-04.md`，已覆盖非负计数、任务 `closed_at`、通知 `resolved_at` 的正反 CHECK、索引和唯一键、追加式建议快照及版本更新与快照写入同事务的竞争/回滚验证。
- [x] 2.3 D 实现 `PaymentSucceededEvent` 的 AFTER_COMMIT 消费、`eventId` 去重、`orderId` 唯一任务创建及 A 补偿共用的 `ensureTask`；验证：`TravelTaskApplicationServiceTest`、`TravelTaskPaymentEventIntegrationTest` 覆盖重复事件、首次消费失败后的补偿、并发创建、提交后消费和回滚不创建，均通过。
- [x] 2.4 D 实现 `OrderInvalidated` 的版本比较、任务取消和建议过期处理；验证：`TravelTaskApplicationServiceTest`、`TravelTaskPaymentEventIntegrationTest` 覆盖退款提交后取消、退款先到的 CANCELLED 墓碑、支付事件随后到达不重开任务、低版本退款后较高版本退款推进墓碑审计字段及两版本并发到达时保留较高版本，均通过。
- [x] 2.5 D 提供本人任务查询、提醒时间更新与只读建议摘要 Application/API 边界；验证：`TravelTaskQueryServiceTest` 覆盖跨用户隐藏、取消任务返回 `207002`、五分钟内刷新返回 `107001`，均通过。
- [ ] 2.6 A、D 更新两个事件和 D 任务消费者的 `cinemaId` 处理；验证：覆盖合法正数的新支付任务、新退款墓碑、已有任务退款保留原值、非法支付事件跳过并由 A 的 PAID 补偿恢复、非法退款先到仍创建 `cinema_id=NULL` 的墓碑且阻止迟到支付重开，以及历史 `cinema_id=NULL` 读取。

## 3. 天气建议、快照与提醒投递

- [x] 3.1 D 定义天气 Provider、缓存、标准 DTO 与版本化 Demo 数据；验证：`WeatherQueryServiceTest` 在固定时钟下覆盖真实、缓存、Demo、不可用四种来源及来源、时效、降级字段。
- [x] 3.2 D 实现天气风险和通用交通建议的确定性规则及建议快照；验证：`TravelAdviceServiceTest` 覆盖天气不可用仍保留通用建议及同版本竞争不覆盖，`TravelTaskPaymentEventIntegrationTest` 覆盖 H2 中 Demo 回退、快照追加和任务进入 `READY`，均通过。
- [x] 3.3 D 实现提醒调度、任务版本抢占和状态转换；验证：`TravelTaskPaymentEventIntegrationTest` 覆盖重复调度只保留一条快照、退款任务不再生成和开场两小时后转为 `COMPLETED`；`TravelReminderSchedulingServiceTest` 覆盖调度候选与关闭条件调用，均通过。
- [ ] 3.3a D 接入已确认的真实天气 Provider；验证：启用/未配置/超时/无效响应、缓存、Demo 回退和来源时效测试通过，Key 不出现在仓库、日志或测试输出。
- [ ] 3.4 D 接入 C 的 `EmailDeliveryPort`，实现 `deliveryKey` 唯一投递、`PENDING/SENDING/SENT/FAILED/UNKNOWN` 状态和结果查询恢复；验证：重复调用只投递一次，`UNKNOWN` 只查询恢复、不自动重发，只有 `SENT` 后任务进入 `NOTIFIED`。
- [ ] 3.5 D 建立版本化提醒 Mock、回归用例和缺陷清单；验证：关闭真实天气和邮件 Provider 后，支付→任务→建议→Mock 提醒仍可演示且不把 Mock 显示为实时数据。

## 4. 路线与静态地图

- [ ] 4.1 D 定义步行、骑行、公交路线选项和所选路线静态地图的 DTO、错误码、来源时效和不排序规则；验证：规格场景覆盖首次跳过后再请求、拒绝定位、非本人/取消任务、单方式失败和地图失败。
- [ ] 4.2a D 向 A 提交 V013 SQL 草案并完成静态复核前修订；验证：草案仅增加 `travel_task.cinema_id BIGINT NULL` 和仅允许 `NULL` 或正数的 CHECK，无外键、默认值、回填或执行记录，A 的审查结论写回本 change。
- [ ] 4.2 D 通过内容模块公开 `CinemaRouteDestinationQueryPort` 查询影院静态坐标，并在 V013 发布后的任务中使用 A 确认的 `cinemaId`；验证：路线模块不访问 content Entity、Mapper、Repository 或表，历史 `cinema_id=NULL` 或缺少合法坐标时不调用高德。
- [ ] 4.3 D 实现高德步行、骑行、公交 Provider 与静态地图图片流代理；验证：固定 HTTP Mock 覆盖三种成功响应、公交 `city`、无 Key、超时、无可达方案、错误响应和 Key 不泄露。
- [ ] 4.4 D 实现本人任务校验、一次性坐标校验、所选方式路线请求、每用户限流和所选方式地图请求；验证：成功、跨用户、取消任务、历史 `cinema_id=NULL`、无效坐标、频繁请求、Provider 异常和隐私扫描测试通过。
- [ ] 4.5 C 接入首次询问、后续路线入口、浏览器位置授权、三张路线卡和所选地图展示；验证：用户拒绝授权、定位失败、某一方式不可用和后续重试的页面行为符合约定。

## 5. 工具、接口与跨模块联调

- [x] 5.1 D 实现 `GetWeatherTool`、`GetTravelAdviceTool`，仅调用 D Application Service；验证：`TravelReadOnlyToolsTest` 覆盖天气时效与降级透传、建议只读查询以及错误目标名不调用下游服务，均通过。
- [ ] 5.1a D 在提交前移除未合并的 `SearchNearbyFoodTool` 及其测试引用，并确保 `PlanBasicRouteTool` 不被 B 注册；验证：全局搜索无餐饮工具，路线请求只经 C 页面 API，不出现 Agent 位置参数或注册项。
- [x] 5.2 A、D 已联调支付事件实际发布、任务创建、订单失效和补偿；验证：`PaymentIntegrationTest`、`RefundIntegrationTest`、`TravelTaskPaymentEventIntegrationTest`、`PaidTravelTaskReconciliationIntegrationTest`、`RefundedTravelTaskReconciliationIntegrationTest` 在最新 `dev` 共通过，覆盖支付回滚不建任务、退款后取消、重复支付事件和 PAID/REFUNDED 补偿均只保留一个任务。
- [ ] 5.3 B、D 联调对话中的只读建议摘要；验证：调用不产生 `agent_*`、任务、建议、通知或位置写入。
- [ ] 5.4 C、D 联调本人任务、建议和刷新接口；验证：401、403、404、409、422、429、503 与约定错误码、来源时效和降级展示一致。

## 6. 验证、质量与交付

- [ ] 6.1 D 完成任务、Provider、缓存、调度、事件、通知、权限、并发、降级和隐私的单元及集成测试；验证：测试输出记录通过数、失败数、跳过数和失败复现信息。
- [ ] 6.2 A、C、D 在 MySQL 8、Redis 和 Mock 邮件 Provider 环境执行事件、缓存、通知恢复和隐私验证；验证：不在该任务中用普通应用环境执行 Flyway，真实环境结果附环境、命令和缺陷编号。
- [ ] 6.3 D 执行离线演示回归：关闭真实天气、路线、静态地图和邮件 Provider；验证：核心购票和电子票可用，天气按 Demo 规则展示，路线和地图明确不可用，未把 Mock 作为实时事实。
- [ ] 6.4 D 执行 `backend\mvnw.cmd verify`、`openspec validate travel-reminder-experience --strict`、`git diff --check` 和变更文件核对；验证：构建、测试、架构检查、Checkstyle、SpotBugs、JaCoCo、严格校验和空白检查均通过，未验证项明确负责人。
- [ ] 6.5 D 记录回归结果、缺陷、风险、关联提交和 A/B/C 审查结论；验证：所有已勾选任务均有对应证据，全部完成后再同步主规格并归档。
