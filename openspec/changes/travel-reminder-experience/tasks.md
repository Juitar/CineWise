## 1. 跨模块前置确认

- [x] 1.1 A 确认 `PaymentSucceededEvent`、`OrderInvalidated` 的字段、事务提交后发布方式和 A→D 的 `ensureTask`、`ensureTaskCancelled` 补偿调用；验证：A、D 在本 change 记录确认，且事件不含邮箱、支付密码、座位明细、精确位置或路线几何。
- [x] 1.2 D 通过公开 Application API 提供 A 发布事件所需的 `cinemaArea` 摘要；验证：A 不访问 D 的 Entity、Mapper、Repository 或表即可组装事件。
- [x] 1.3 C 确认 `EmailDeliveryPort` 的 `deliveryKey` 发送/查询语义、Mock Provider 和已验证邮箱解析边界；验证：D 仅传 `recipientUserId`，重复键可查回原结果。
- [ ] 1.4 A 在 D 提交完整迁移申请后分配 Flyway 版本号并确认 `travel_task`、`travel_advice_snapshot`、`travel_notification_log` 的字段、索引、保留期、非负计数和终态时间 CHECK 及兼容方案；验证：记录 Owner 确认，未修改已发布迁移。
- [x] 1.5 B、C、D 确认只读出行工具和卡片边界；验证：B 对话读取不创建任务、刷新快照、发送邮件或请求位置，C 只在用户主动操作时发起路线和餐饮请求。

## 2. 任务、事件与数据基础

- [ ] 2.1 D 建立 `travel` 的 api/application/domain/infrastructure 分层、任务状态和值对象；验证：模块架构测试通过，生产源文件中文有效注释率不低于 30%。
- [ ] 2.2 A 按确认版本新增三张出行表的前向 Flyway 迁移，D 复核字段与约束；验证：独立 `cinewise_migration_check` MySQL 8 执行迁移，并覆盖非负计数、任务 `closed_at`、通知 `resolved_at` 的正反 CHECK、索引和唯一键、追加式建议快照验证。
- [ ] 2.3 D 实现 `PaymentSucceededEvent` 的 AFTER_COMMIT 消费、`eventId` 去重、`orderId` 唯一任务创建及 A 补偿共用的 `ensureTask`；验证：重复事件、首次消费失败后补偿和并发创建仅保留一个任务。
- [ ] 2.4 D 实现 `OrderInvalidated` 的版本比较、任务取消和建议过期处理；验证：新事件取消任务，旧事件不改变任务，取消后不再生成提醒。
- [ ] 2.5 D 提供本人任务查询、提醒时间更新与只读建议摘要 Application/API 边界；验证：跨用户不可访问，取消任务返回 `207002`，刷新频率限制返回 `107001`。

## 3. 天气建议、快照与提醒投递

- [ ] 3.1 D 定义天气 Provider、缓存、标准 DTO 与版本化 Demo 数据；验证：固定时钟下结果稳定，真实/缓存/Demo/不可用均返回来源、时效和降级信息。
- [ ] 3.2 D 实现天气风险和通用交通建议的确定性规则及建议快照；验证：天气成功、缓存命中、Demo 回退、全部不可用和过期快照测试通过，天气失败不阻断电子票和任务 `READY`。
- [ ] 3.3 D 实现提醒调度、任务版本抢占和状态转换；验证：重复调度或并发执行不重复生成有效快照，订单失效和到期状态正确。
- [ ] 3.4 D 接入 C 的 `EmailDeliveryPort`，实现 `deliveryKey` 唯一投递、`PENDING/SENDING/SENT/FAILED/UNKNOWN` 状态和结果查询恢复；验证：重复调用只投递一次，`UNKNOWN` 只查询恢复、不自动重发，只有 `SENT` 后任务进入 `NOTIFIED`。
- [ ] 3.5 D 建立版本化提醒 Mock、回归用例和缺陷清单；验证：关闭真实天气和邮件 Provider 后，支付→任务→建议→Mock 提醒仍可演示且不把 Mock 显示为实时数据。

## 4. 用户主动路线与餐饮查询

- [ ] 4.1 D 定义基础路线 Command、Provider 和响应摘要，接收一次性设备位置或手动地点；验证：仅用户主动请求且已确认共享说明时调用，返回一条路线、预计耗时和预计出发时间。
- [ ] 4.2 D 实现路线隐私与失败处理；验证：成功、失败和超时后扫描 MySQL、Redis、日志、画像、快照和 Agent 轨迹，均无精确坐标、路线折线或途经点；路线失败返回 `307001` 且不生成文字路线。
- [ ] 4.3 D 定义餐饮 POI Provider、受控半径、稳定排序、缓存和版本化 Demo 回退；验证：默认半径、边界、超范围 `107003`、空结果、超时和营业状态未知测试通过。
- [ ] 4.4 C、D 联调路线地图渲染与位置授权交互；验证：定位允许、拒绝、超时和手动地点均有可继续路径，路线几何只在本次响应和页面内存使用。

## 5. 工具、接口与跨模块联调

- [ ] 5.1 D 实现 `GetWeatherTool`、`GetTravelAdviceTool`、`PlanBasicRouteTool`、`SearchNearbyFoodTool`，仅调用 D Application Service；验证：工具使用公共 `ToolContext` 和 `ToolResult<T>`，不调用模型、不发布 SSE、不访问 Mapper。
- [ ] 5.2 A、D 联调支付事件实际发布、任务创建、订单失效和补偿；验证：支付回滚不建任务，重复支付事件和补偿均只保留一个任务。
- [ ] 5.3 B、D 联调对话中的只读建议摘要；验证：调用不产生 `agent_*`、任务、建议、通知或位置写入。
- [ ] 5.4 C、D 联调本人任务、建议、刷新、路线和餐饮接口；验证：401、403、404、409、422、429、503 与约定错误码、来源时效和降级展示一致。

## 6. 验证、质量与交付

- [ ] 6.1 D 完成任务、Provider、缓存、调度、事件、通知、权限、并发、降级和隐私的单元及集成测试；验证：测试输出记录通过数、失败数、跳过数和失败复现信息。
- [ ] 6.2 A、C、D 在 MySQL 8、Redis 和 Mock 邮件 Provider 环境执行事件、缓存、通知恢复和隐私验证；验证：不在该任务中用普通应用环境执行 Flyway，真实环境结果附环境、命令和缺陷编号。
- [ ] 6.3 D 执行离线演示回归：关闭真实天气、餐饮、路线和邮件 Provider；验证：核心购票和电子票可用，出行能力明确降级，未把 Mock 作为实时事实。
- [ ] 6.4 D 执行 `backend\mvnw.cmd verify`、`openspec validate travel-reminder-experience --strict`、`git diff --check` 和变更文件核对；验证：构建、测试、架构检查、Checkstyle、SpotBugs、JaCoCo、严格校验和空白检查均通过，未验证项明确负责人。
- [ ] 6.5 D 记录回归结果、缺陷、风险、关联提交和 A/B/C 审查结论；验证：所有已勾选任务均有对应证据，全部完成后再同步主规格并归档。
