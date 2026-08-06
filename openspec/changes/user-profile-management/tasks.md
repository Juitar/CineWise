## 1. 前置确认与迁移申请

- [x] 1.1 D 复核画像设计、推荐设计、现有 `CurrentUserAccessor`、`ToolContext`、`ToolResult<T>` 和错误码实现；验证：实际类型、调用方向和差异已记录在 `design.md` 的“当前实现基线”，未修改公共契约。
- [x] 1.2 D 向 A 提交 `user_preference`、`user_profile_tag`、`user_behavior_event`、`profile_write_request` 的字段、类型、可空性、默认值、索引、UNIQUE、CHECK、软删除、幂等状态和清理申请，并创建 `V010__create_profile_tables.sql` 草案；A 静态审查后明确授权迁移验证。验证：V010 已完成隔离 MySQL 和共享库发布验证并冻结；详见 `docs/database-migrations/V010_*_2026-08-05.md`。
- [ ] 1.3 B 已确认 `GetProfileSummaryTool` 的注册、当前用户注入、`ProfileBehaviorRecorder`、稳定 UUID `planId` 和长期对话偏好确认输入；C、D 已确认 `CurrentUserAccessor`、画像错误展示和同意撤回边界。C 已按 A 分配的 `V017` 补齐同意记录、版本递增、可靠投递与保留规则，并准备私有 SQL 草案，等待 A 静态审核；撤回记录的 CAS 条件更新与 `(user_id, consent_record_version)` outbox 插入必须在同一 MySQL 本地事务内提交，任一步失败整体回滚。已执行 `openspec validate user-profile-management --strict`，结果通过；未经后续 MySQL 验证授权不得执行 SQL。D 已确认快照与撤回事件字段的消费规则。当前版本已取消账户删除，不实现账户删除通知。验证：确认的字段和未确认项写入 design，不把待确认接口写成已完成。
- [ ] 1.4 D 确认标签类型、来源、状态、极性、行为与目标类型的唯一映射：`DIALOG -> CONVERSATION`、`ORDER -> BEHAVIOR`、`GENRE -> MOVIE_GENRE`；A 审查 V010 的对应 CHECK，B 在 `agent-plan-feedback-events` 接入已确认的稳定 `PLAN` UUID，C 仅发送已鉴权的 `MOVIE` 行为。验证：表 CHECK、Java 枚举、REST DTO、工具夹具和推荐消费者使用同一套值。
- [x] 1.5 C 已确认个人数据保存同意的类型化查询 `ProfileDataConsentQuery` 和撤回通知 `ProfileDataConsentWithdrawnEvent`；A/B/C 确认各类行为事件的可信来源、目标校验和关闭开关后的采集规则。验证：无同意和撤回同意统一为 HTTP 403 / `202004 PROFILE_DATA_CONSENT_REQUIRED`；关闭后的行为不采集、不提交、不写入、不补采或回放。C 的接口和可靠通知尚未实现，D 的生产写入在此之前默认按未同意处理。

## 2. 画像数据与本人管理

- [x] 2.1 D 建立 `profile` 的 api/application/domain/infrastructure 分层、值对象、标签来源/状态/极性和错误映射；验证：`ProfileModuleArchitectureTest` 与 `ProfileTagValueTest` 共 5 项通过，`backend\mvnw.cmd verify` 通过；新增生产文件中文注释率均达到团队要求。
- [x] 2.2 D 已实现默认偏好设置创建、本人开关与分页标签查询、新增、更新、单标签停用/恢复、软删除、归属校验和版本冲突返回；验证：画像定向测试和全量 `mvn verify` 通过，分页总数和字符串 ID 均由 REST DTO 返回。
- [x] 2.3 D 已实现 `If-Match`、`Idempotency-Key`、版本条件更新和重复响应恢复；验证：`ProfileManagementServiceTest` 覆盖首次响应重放优先于后续版本校验，全量 `mvn verify` 通过。
- [x] 2.4 D 已实现 `profile_write_request` 的请求摘要哈希、先查已提交记录再校验 `If-Match`、同一本地事务内的响应恢复和同键不同请求拒绝；验证：响应以真实 JSON 保存，重放测试和全量 `mvn verify` 通过。MySQL 并发实库验证仍列在 2.5。
- [ ] 2.5 A 在隔离 MySQL 8 环境验证 V010 前向迁移；D 补齐 Repository 集成验证。验证：四表的唯一键、CHECK、索引、软删除、限定清理、同事务幂等恢复和 UTC 时间语义均通过；不改已发布迁移。

## 3. 行为、摘要与缓存

- [x] 3.1 D 已实现仅限模块内类型化 Application API 和支付提交后内部事件的最小行为接收、`eventId` 去重、24 小时归一化限制及固定权重累计；验证：没有本机 HTTP 行为入口，`ProfilePaymentEventListener` 只接收 A 的 `PaymentSucceededEvent`，全量 `mvn verify` 通过。
- [x] 3.2 D 已实现行为标签的过期、衰减和每日任务；验证：固定时钟的 `ProfileDecayJobTest` 覆盖 30 天衰减、90 天过期和并发版本条件，全量 `mvn verify` 通过。
- [x] 3.3 D 已实现 `ProfileSummary`、Redis 版本缓存和 MySQL 回退；验证：`ProfileQueryServiceCacheVersionTest` 覆盖版本键隔离，关闭时摘要不返回标签，全量 `mvn verify` 通过。
- [x] 3.4 D 已实现 `GetProfileSummaryTool`，仅调用 Application Service；验证：工具不接收 userId、不访问 Mapper、不写 `agent_*`、不开 SSE，关闭或不存在设置时只返回 `enabled=false`。
- [x] 3.5 D 已实现 `ProfileDataConsentWithdrawnEvent` 处理和分期清理；验证：立即关闭、缓存失效、按 `eventId` 去重、30 天标签清理和 90 天行为摘要清理均由受限批任务执行。当前版本不实现账户删除通知或账户删除清理任务。
- [x] 3.6 D 已实现最小审计日志、清理成功/失败 Micrometer 指标和清理失败告警；验证：`ProfileAuditFieldPolicy` 仅允许 userId、operation、result、errorCode、traceId，日志适配器会再次过滤调用方传入字段。

## 4. 推荐与前端协作

- [x] 4.1 D 已为推荐模块接入受控 `ProfileSummary`、`profileApplied` 和最小化采用标签证据；当前请求明确影院始终优先，只有画像中同样偏好的影院才记录采用证据。验证：固定推荐回归不因无画像改变，`FixedRecommendationQueryServiceTest` 覆盖匹配影院画像的最小证据返回。
- [ ] 4.2 B、D 联调确认后的对话标签写入与只读摘要查询；验证：未确认对话不落库，已确认标签可被摘要读取，且没有 Agent 运行或 SSE 写入。
- [ ] 4.3 C、D 联调画像 REST、开关、标签编辑和版本冲突展示；验证：401、404、409、400 及六位数错误码、刷新最新数据和关闭后的页面状态一致。

## 5. 验证与交付

- [ ] 5.1 D 完成标签、开关、行为、缓存、并发、权限、隐私、衰减和工具的单元及集成测试；验证：记录通过、失败、跳过和复现信息。
- [ ] 5.2 D 在 A 批准的 MySQL、Redis 环境执行限定真实验证；验证：使用测试专属业务 ID 与缓存键、Flyway 关闭、限定清理和无残留检查。
- [x] 5.3 D 已执行 `mvn verify`、`openspec validate user-profile-management --strict`、`git diff --check` 和变更范围核对；验证：147 个测试报告失败 0，Checkstyle 错误 0，SpotBugs 问题 0；未验证项已明确为 A/B/C 的外部协作或实库授权。
- [x] 5.4 D 已准备独立分支 PR 说明；验证：`PR_DESCRIPTION.md` 列出修改范围、验证命令、Owner 待确认项和隐私边界。未提交、未推送、未创建 PR。
