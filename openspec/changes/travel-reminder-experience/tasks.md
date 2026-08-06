## 1. 跨模块前置确认

- [x] 1.1 A 确认 `PaymentSucceededEvent`、`OrderInvalidated` 的字段、事务提交后发布方式和 A→D 的 `ensureTask`、`ensureTaskCancelled` 补偿调用；验证：A、D 在本 change 记录确认，且事件不含邮箱、支付密码、座位明细、精确位置或路线几何。
- [x] 1.2 D 通过公开 Application API 提供 A 发布事件所需的 `cinemaArea` 摘要；验证：A 不访问 D 的 Entity、Mapper、Repository 或表即可组装事件。
- [x] 1.3 C 确认 `EmailDeliveryPort` 的 `deliveryKey` 发送/查询语义、Mock Provider 和已验证邮箱解析边界；验证：D 仅传 `recipientUserId`，重复键可查回原结果。
- [x] 1.4 A 已正式分配 V007，并确认 `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 的字段、索引、保留期、非负计数和终态时间 CHECK 及兼容方案；验证：记录 Owner 确认，未修改已发布迁移。
- [x] 1.5 B、C、D 确认只读出行工具和卡片边界；验证：B 对话读取不创建任务、刷新快照、发送邮件或请求位置，C 只在用户主动操作时发起路线和餐饮请求。
- [ ] 1.6 A 已确认 `PaymentSucceededEvent`、`OrderInvalidated` 的 `String cinemaId` 字段、V013 的 `travel_task.cinema_id BIGINT NULL` 及 `CHECK (cinema_id IS NULL OR cinema_id > 0)`；待 V013 SQL、A/D 事件处理和测试实际完成后再勾选。字段不含用户位置、坐标或路线数据。

## 2. 任务、事件与数据基础

- [x] 2.1 D 建立 `travel` 的 api/application/domain/infrastructure 分层、任务状态和值对象；验证：模块架构测试通过，生产源文件中文有效注释率不低于 30%。
- [x] 2.2 D 已提交并确认正式 V007 的三张出行表字段、约束、生命周期和业务语义；A 已在隔离迁移分支创建最终 Flyway SQL，并完成静态复核和专用 MySQL 8.4 验证；验证：见 `docs/database-migrations/V007_MIGRATION_VALIDATION_2026-08-04.md`，已覆盖非负计数、任务 `closed_at`、通知 `resolved_at` 的正反 CHECK、索引和唯一键、追加式建议快照及版本更新与快照写入同事务的竞争/回滚验证。
- [x] 2.3 D 实现 `PaymentSucceededEvent` 的 AFTER_COMMIT 消费、`eventId` 去重、`orderId` 唯一任务创建及 A 补偿共用的 `ensureTask`；验证：`TravelTaskApplicationServiceTest`、`TravelTaskPaymentEventIntegrationTest` 覆盖重复事件、首次消费失败后的补偿、并发创建、提交后消费和回滚不创建，均通过。
- [x] 2.4 D 实现 `OrderInvalidated` 的版本比较、任务取消和建议过期处理；验证：`TravelTaskApplicationServiceTest`、`TravelTaskPaymentEventIntegrationTest` 覆盖退款提交后取消、退款先到的 CANCELLED 墓碑、支付事件随后到达不重开任务、低版本退款后较高版本退款推进墓碑审计字段及两版本并发到达时保留较高版本，均通过。
- [x] 2.5 D 提供本人任务查询、提醒时间更新与只读建议摘要 Application/API 边界；验证：`TravelTaskQueryServiceTest` 覆盖跨用户隐藏、取消任务返回 `207002`、五分钟内刷新返回 `107001`，均通过。
- [ ] 2.6 A、D 在 A 的事件代码合入后实现并验证 `cinemaId` 处理；验证：合法支付任务、非法支付后的 PAID 补偿、退款先到合法/非法影院 ID、已有任务退款保留原值、迟到支付不重开、历史 `cinema_id=NULL` 路线不可用，以及 V013 对 `0`/负数的 CHECK 均通过。

## 3. 天气建议、快照与提醒投递

- [x] 3.1 D 定义天气 Provider、缓存、标准 DTO 与版本化 Demo 数据；验证：`WeatherQueryServiceTest` 在固定时钟下覆盖真实、缓存、Demo、不可用四种来源及来源、时效、降级字段。
- [x] 3.2 D 实现天气风险和通用交通建议的确定性规则及建议快照；验证：`TravelAdviceServiceTest` 覆盖天气不可用仍保留通用建议及同版本竞争不覆盖，`TravelTaskPaymentEventIntegrationTest` 覆盖 H2 中 Demo 回退、快照追加和任务进入 `READY`，均通过。
- [x] 3.3 D 实现提醒调度、任务版本抢占和状态转换；验证：`TravelReminderSchedulingServiceTest` 覆盖失败隔离和到期关闭，`TravelTaskPaymentEventIntegrationTest` 覆盖重复调度只生成一条建议快照、取消任务不生成建议和到期任务关闭；共 11 个相关用例通过。
- [ ] 3.4 D 接入 C 的 `EmailDeliveryPort`，实现 `deliveryKey` 唯一投递、`PENDING/SENDING/SENT/FAILED/UNKNOWN` 状态和结果查询恢复；验证：重复调用只投递一次，`UNKNOWN` 只查询恢复、不自动重发，只有 `SENT` 后任务进入 `NOTIFIED`。
- [ ] 3.5 D 建立版本化提醒 Mock、回归用例和缺陷清单；验证：关闭真实天气和邮件 Provider 后，支付→任务→建议→Mock 提醒仍可演示且不把 Mock 显示为实时数据。

## 4. 用户主动路线与餐饮查询

- [x] 4.1 D 定义基础路线 Command、Provider 和响应摘要，接收一次性设备位置或手动地点；验证：`BasicRouteServiceTest` 覆盖仅确认共享后才调用 Provider，返回值只保留安全摘要。
- [x] 4.2 D 实现路线隐私与失败处理；验证：`BasicRouteServiceTest` 覆盖未确认不调用 Provider、Provider 不可用返回 `307001`；实现不向 MySQL、缓存、日志、画像、快照或 Agent 轨迹传递起点、路线折线或途经点。
- [x] 4.3 D 定义餐饮 POI Provider、受控半径、稳定排序、缓存和版本化 Demo 回退；验证：`FoodSearchServiceTest` 覆盖默认半径、边界外 `107003`、真实结果缓存命中、Demo 回退、全部 Provider 不可用返回空结果和营业状态未知；Provider 返回空视为超时/不可用的统一降级结果。
- [ ] 4.4 C、D 联调路线地图渲染与位置授权交互；验证：定位允许、拒绝、超时和手动地点均有可继续路径，路线几何只在本次响应和页面内存使用。

## 5. 工具、接口与跨模块联调

- [x] 5.1 D 实现 `GetWeatherTool`、`GetTravelAdviceTool`、`PlanBasicRouteTool`、`SearchNearbyFoodTool`，仅调用 D Application Service；验证：`TravelReadOnlyToolsTest` 4 个用例覆盖四个工具的公开 `ToolContext`/`ToolResult<T>` 适配、只读调用和错误映射，不调用模型、不发布 SSE、不访问 Mapper。
- [ ] 5.2 A、D 联调支付事件实际发布、任务创建、订单失效和补偿；验证：支付回滚不建任务，重复支付事件和补偿均只保留一个任务。
- [ ] 5.3 B、D 联调对话中的只读建议摘要；验证：调用不产生 `agent_*`、任务、建议、通知或位置写入。
- [ ] 5.4 C、D 联调本人任务、建议、刷新、路线和餐饮接口；验证：401、403、404、409、422、429、503 与约定错误码、来源时效和降级展示一致。

## 6. 验证、质量与交付

- [ ] 6.1 D 完成任务、Provider、缓存、调度、事件、通知、权限、并发、降级和隐私的单元及集成测试；验证：测试输出记录通过数、失败数、跳过数和失败复现信息。
- [ ] 6.2 A、C、D 在 MySQL 8、Redis 和 Mock 邮件 Provider 环境执行事件、缓存、通知恢复和隐私验证；验证：不在该任务中用普通应用环境执行 Flyway，真实环境结果附环境、命令和缺陷编号。
- [ ] 6.3 D 执行离线演示回归：关闭真实天气、餐饮、路线和邮件 Provider；验证：核心购票和电子票可用，出行能力明确降级，未把 Mock 作为实时事实。
- [ ] 6.4 D 执行 `backend\mvnw.cmd verify`、`openspec validate travel-reminder-experience --strict`、`git diff --check` 和变更文件核对；验证：构建、测试、架构检查、Checkstyle、SpotBugs、JaCoCo、严格校验和空白检查均通过，未验证项明确负责人。
- [ ] 6.5 D 记录回归结果、缺陷、风险、关联提交和 A/B/C 审查结论；验证：所有已勾选任务均有对应证据，全部完成后再同步主规格并归档。
