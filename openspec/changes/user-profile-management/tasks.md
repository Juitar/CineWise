## 1. 前置确认与迁移申请

- [x] 1.1 D 复核画像设计、推荐设计、现有 `CurrentUserAccessor`、`ToolContext`、`ToolResult<T>` 和错误码实现；验证：实际类型、调用方向和差异已记录在 `design.md` 的“当前实现基线”，未修改公共契约。
- [x] 1.2 D 向 A 提交 `user_preference`、`user_profile_tag`、`user_behavior_event`、`profile_write_request` 的字段、类型、可空性、默认值、索引、UNIQUE、CHECK、软删除、幂等状态和清理申请，并创建 `V010__create_profile_tables.sql` 草案；A 静态审查后明确授权迁移验证。验证：V010 已完成隔离 MySQL 和共享库发布验证并冻结；详见 `docs/database-migrations/V010_*_2026-08-05.md`。
- [ ] 1.3 B 已确认 `GetProfileSummaryTool` 的注册、当前用户注入、`ProfileBehaviorRecorder`、稳定 UUID `planId` 和长期对话偏好确认输入；C 确认 `CurrentUserAccessor` 和前端错误展示边界。当前版本已取消账户删除，不实现账户删除通知。验证：确认的字段和未确认项写入 design，不把待确认接口写成已完成。
- [ ] 1.4 D 确认标签类型、来源、状态、极性、行为与目标类型的唯一映射：`DIALOG -> CONVERSATION`、`ORDER -> BEHAVIOR`、`GENRE -> MOVIE_GENRE`；A 审查 V010 的对应 CHECK，B 在 `agent-plan-feedback-events` 接入已确认的稳定 `PLAN` UUID，C 仅发送已鉴权的 `MOVIE` 行为。验证：表 CHECK、Java 枚举、REST DTO、工具夹具和推荐消费者使用同一套值。
- [x] 1.5 C 已确认个人数据保存同意的类型化查询 `ProfileDataConsentQuery` 和撤回通知 `ProfileDataConsentWithdrawnEvent`；A/B/C 确认各类行为事件的可信来源、目标校验和关闭开关后的采集规则。验证：无同意和撤回同意统一为 HTTP 403 / `202004 PROFILE_DATA_CONSENT_REQUIRED`；关闭后的行为不采集、不提交、不写入、不补采或回放。C 的接口和可靠通知尚未实现，D 的生产写入在此之前默认按未同意处理。

## 2. 画像数据与本人管理

- [x] 2.1 D 建立 `profile` 的 api/application/domain/infrastructure 分层、值对象、标签来源/状态/极性和错误映射；验证：`ProfileModuleArchitectureTest` 与 `ProfileTagValueTest` 共 5 项通过，`backend\mvnw.cmd verify` 通过；新增生产文件中文注释率均达到团队要求。
- [ ] 2.2 D 实现默认偏好设置创建、本人开关与分页标签查询、新增、更新、单标签停用/恢复、软删除、归属校验和版本冲突返回；验证：覆盖首次并发访问、102001、202001、202002、202003、跨用户访问和字符串 ID 序列化。
- [ ] 2.3 D 实现 `If-Match`、`Idempotency-Key`、版本条件更新和重复响应恢复；验证：覆盖并发编辑、首次成功后其他写入推进版本时携带旧 `If-Match` 的同键重放仍返回首次响应、不同幂等键的重复标签和缓存失效。
- [ ] 2.4 D 实现 `profile_write_request` 的请求摘要哈希、先查已提交记录再校验 `If-Match`、同一本地事务内的响应恢复、同键并发串行化和同键不同请求拒绝；验证：同键不同请求返回 HTTP 409 / `202005 IDEMPOTENCY_PARAMETER_MISMATCH` 且不执行新请求；覆盖进程重启后重放、并发相同键、成功后版本推进再重放、事务失败无残留、不同键和敏感字段不落库均通过。
- [ ] 2.5 A 在隔离 MySQL 8 环境验证 V010 前向迁移；D 补齐 Repository 集成验证。验证：四表的唯一键、CHECK、索引、软删除、限定清理、同事务幂等恢复和 UTC 时间语义均通过；不改已发布迁移。

## 3. 行为、摘要与缓存

- [ ] 3.1 D 实现仅限模块内类型化 Application API 或内部事件的最小行为接收、`eventId` 去重、24 小时归一化限制和固定权重累计；验证：不存在本机 HTTP 行为入口、未受信任调用方不能伪造 `PAID_ORDER`/方案/本人行为、重复事件、两个不同事件并发命中同一归一化键时只累计一次、事务失败后同一事件重试无残留、单次点击、阈值创建、`BEHAVIOR` 的 0.800/有效期约束、显式偏好优先、无同意拒绝和失败不影响上游业务。
- [ ] 3.2 D 实现行为标签的过期、衰减和每日任务；验证：固定时钟下 30 天衰减、90 天过期、过期标签不进入摘要，任务重复执行不重复衰减。
- [ ] 3.3 D 实现 `ProfileSummary`、Redis 版本缓存和 MySQL 回退；验证：开启、关闭、缓存命中、缓存失效、Redis 异常和隐私字段排除。
- [ ] 3.4 D 实现 `GetProfileSummaryTool` 或其适配器，仅调用 Application Service；验证：不接收 userId、不访问 Mapper、不写 `agent_*`、不开 SSE，关闭时只返回 `enabled=false`。
- [ ] 3.5 D 实现 C 已确认的 `ProfileDataConsentWithdrawnEvent` 处理和分期清理；验证：立即关闭、缓存失效、按 `eventId` 去重、重复通知不延长清理时间、30 天画像数据清理和 90 天行为摘要清理均通过，且不访问 `sys_user` Repository。当前版本不实现账户删除通知或账户删除清理任务。
- [ ] 3.6 D 实现最小审计日志、指标和清理失败告警；验证：日志扫描不含标签完整值、原始 payload、会话、认证信息、邮箱、支付明细或位置。

## 4. 推荐与前端协作

- [ ] 4.1 D 为推荐模块接入受控 `ProfileSummary`、`usedProfile` 和最小化采用标签证据，保证当前需求优先、画像不可用不阻断；验证：固定推荐回归不因无画像而改变既有结果，关闭后不读取长期标签。
- [ ] 4.2 B、D 联调确认后的对话标签写入与只读摘要查询；验证：未确认对话不落库，已确认标签可被摘要读取，且没有 Agent 运行或 SSE 写入。
- [ ] 4.3 C、D 联调画像 REST、开关、标签编辑和版本冲突展示；验证：401、404、409、400 及六位数错误码、刷新最新数据和关闭后的页面状态一致。

## 5. 验证与交付

- [ ] 5.1 D 完成标签、开关、行为、缓存、并发、权限、隐私、衰减和工具的单元及集成测试；验证：记录通过、失败、跳过和复现信息。
- [ ] 5.2 D 在 A 批准的 MySQL、Redis 环境执行限定真实验证；验证：使用测试专属业务 ID 与缓存键、Flyway 关闭、限定清理和无残留检查。
- [ ] 5.3 D 执行 `backend\mvnw.cmd verify`、`openspec validate user-profile-management --strict`、`git diff --check` 和变更范围核对；验证：未验证项明确到 A、B、C 或 D。
- [ ] 5.4 D 准备独立分支 PR 说明；验证：列出迁移版本、Owner 确认、验证结果、未确认项和隐私边界，用户明确要求后才提交、推送或创建 PR。
