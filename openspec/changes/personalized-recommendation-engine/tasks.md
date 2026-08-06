## 1. 开工前确认

- [x] 1.1 A 已提供 `SaleableShowBatchQueryService` 公开 Application API；验证：`date + cinemaIds(1..100)`、最多 200 条和 `truncated`、空列表、`306003`、`expiresAt=min(show.startTime,dataAt+60秒)` 均由 A 的 `SaleableShowBatchQueryServiceTest` 覆盖，D 不访问 `movie_show`、Mapper 或 Controller。
- [x] 1.3 D 与 B 确认完整 `RankMoviePlanCommand` 的约束字段、可信当前用户与稳定 `runId` 的传入方式、结果到 `PLAN_CARD` 的字段映射、放宽条件的确认回调；验证：B 已确认 `ticketCount`、JSON 数组槽位、DTO 字段、`runId` 边界和卡片不改写规则，见 `coordination.md`。
- [x] 1.4 D 与 C 确认 `PLAN_CARD` 展示、过期、空方案和公开字段范围；验证：C 已确认卡片展示、公共 `apiRequest<T>()`、定位拒绝回退和不展示内部数据，见 `coordination.md`。
- [x] 1.5 B、C、D 已确认一次性定位的结构化交互、上下文关联和距离卡片展示；验证：`QUESTION.questionType=LOCATION_AUTHORIZATION`、`distanceContextId` 和 `distancePreference` 已确定，位置不进入 Command、槽位、模型输入、MySQL、Redis、日志或 Agent 轨迹。

## 2. D 推荐核心

- [x] 2.1 D 建立推荐约束、候选、方案、证据、放宽建议和结果的领域类型，并保留 `rec-mvp-1` 算法版本；验证：`RecommendationConstraintsTest` 通过，生产代码使用中文边界注释，业务 ID、金额和时间格式符合仓库约定。
- [x] 2.2 D 实现可信候选校验与硬过滤：日期、人数、指定影片、指定影院、指定/排除类型、最晚结束和预算；验证：`RecommendationCandidateFilterTest` 覆盖类型冲突和超预算在评分前排除，`RecommendationConstraintsTest` 覆盖时段与预算输入校验；动态票务字段完整性继续由 A 适配器接入时复用 `PurchaseCandidateValidator`。
- [x] 2.3 D 实现综合、低价、时间三类确定性评分、评分缺失后的归一化、并列顺序、`showId` 去重和不足三类的结果；验证：`RecommendationPlanRankerTest` 在固定时钟和夹具下验证三类方案、`showId` 去重及低价方案选择剩余候选中的最低价场次。
- [x] 2.4 D 实现最多一项放宽建议与无可购候选降级；验证：`RecommendationRelaxationAdvisorTest` 验证仅在移除预算后恢复候选时返回预算建议，规则不修改原条件、不创建订单、不锁座、不支付。
- [x] 2.5 D 在 A 已确认的公开 API 上实现候选适配器；验证：`TicketingBatchShowtimeQueryAdapterTest` 验证场次、金额、来源和 `truncated` 原样映射，适配器只依赖 `SaleableShowBatchQueryService`，不保存第二份票务事实。
- [x] 2.6 D 实现按城市解析影院候选的内容公开应用 API；验证：`RecommendationContentCandidateQueryServiceTest` 覆盖影院 ID、名称、来源和时效映射，内容查询异常继续由内容模块返回稳定错误。
- [ ] 2.7 D 已实现 Haversine 直线距离计算、一次性位置上下文、最近 10 家预筛、距离上限过滤和 `NEAREST` 方案的领域组件；待 B 在可信 `ToolContext` 传入已确认的 `distanceContextId` 与距离偏好后，D 才能在推荐查询中消费一次性坐标、先筛选影院再调用 A，并完成实际调用验证。当前验证仅覆盖 `CinemaDistanceSelectorTest`、`DistanceContextServiceTest`、`RecommendationPlanRankerTest` 的组件规则；不调用高德、不持久化位置。

## 3. 推荐指标

- [x] 3.1 D 实现推荐空方案率和降级率的最小指标；验证：`RecommendationMetricsRecorderTest` 覆盖算法版本、候选来源和缺失因素标签过滤，`PersonalizedRecommendationQueryServiceTest` 覆盖最终结果写入指标；日志与指标不含会话、认证信息或精确位置。

## 4. 工具、调用方和回归

- [x] 4.1 D 在 B、D 确认后升级 `RankMoviePlanTool` 的类型化输入输出，并保持只读、无 SSE、无模型调用和无 Agent 持久化访问；验证：完整入口拒绝旧 Command、使用 `RecommendationPlanResult`，并覆盖成功、空方案、降级、错误码、`dataAt`、`expiresAt` 和消费者夹具一致性。B 仍需在 4.2 将运行适配器切换到完整入口。
- [ ] 4.2 B 完成工具注册、运行上下文传递、用户确认后的重新调用和固定 `PLAN_CARD` 发送；验证：B 的 Agent 测试覆盖正常方案、空方案、放宽建议、直线距离和过期数据，D 不修改 B 的运行/SSE 实现。
- [ ] 4.3 C 完成方案展示消费和一次性定位授权；验证：来源、更新时间、过期、空方案、直线距离和拒绝定位状态均可正确展示，D 不修改 C 的公共请求层。
- [x] 4.4 D 完成内容类型、候选过滤、评分、降级和隐私回归；验证：15 个 D 推荐定向测试覆盖正常、边界、重复查询、过期候选、空方案、降级、内容合并和位置单次消费，且不访问 A 的持久化层、B 的运行状态或精确位置持久化。

## 5. 验证与交付

- [ ] 5.1 D 在 A 批准的 MySQL、Redis 环境完成限定验证；验证：使用测试专属业务 ID 与缓存键、Flyway 关闭、限定清理和无残留检查。
- [x] 5.2 D 执行 `backend\mvnw.cmd verify`、`openspec validate personalized-recommendation-engine --strict`、`git diff --check` 和变更范围核对；验证：`verify`、严格 OpenSpec 校验和 `git diff --check` 均通过，未验证的真实环境与跨负责人事项明确到 A、B、C。
- [x] 5.3 D 准备独立分支 PR 说明；验证：草稿 PR #107 已列出 Owner 确认、无迁移、验证结果、未确认项和不伪造票务事实的边界。
