## Why

当前 `rankMoviePlan` 只能根据调用方给出的单个影片、单个影院和时段查询 A 的场次；没有候选召回、硬约束过滤、评分或三类方案。真实影片和影院基础资料已经可以同步，但它们不能被直接当作可购买的场次，因此需要先把“真实内容资料”和“A 提供的可售场次”分开，再实现可解释的推荐规则。

## What Changes

- 新增 D 的确定性推荐引擎：只按当前输入的影片类型、内容评分、A 的公开可售场次、票价和时间生成综合推荐、低价优先、时间优先三类不重复方案。
- 在评分前执行影片、日期、影院、类型、时间、预算和可选距离等硬约束；没有方案时仅给出一项最小影响的放宽建议，不自动放宽。
- 只把 A 的 `ShowQueryService` 返回的 `showId`、票价、开场时间和有效期用于可购方案。真实内容 Provider 只能用于影片、影院和标签资料，不能生成场次、票价、库存或座位。
- 将 D 的推荐结果升级为 `rec-mvp-1` 的方案、理由、证据、缺失因素、放宽建议和有效期；B 仍负责工具注册、运行、SSE 和 `PLAN_CARD` 发送，A 仍在建单时再次校验交易事实。
- 不新增 `movie_tag`，直接使用内容模块已有的 `genresJson` 和 `rating`；本次不做情绪、场景、恐怖、烧脑、节奏、人工标注或 AI 标签。
- 新增用户主动定位后的直线距离推荐：使用用户本次经纬度和影院静态经纬度筛选最近影院并生成“最近优先”方案；不调用高德、不计算路线、不保存用户位置。
- **BREAKING**：将 `rankMoviePlan` 从固定影片、影院查询升级为可表达当前推荐约束的类型化输入；B 负责工具登记、运行、确认和 SSE 卡片发送，D 负责推荐结果字段与证据。

## Capabilities

### New Capabilities

- `personalized-recommendation-ranking`: D 根据可信内容、场次和可选画像摘要完成确定性过滤、评分、去重、解释和降级。
- `recommendation-presentation-contract`: B、C、D 共同使用固定的 `PLAN_CARD` 推荐结果字段、空结果和过期展示规则。
- `recommendation-content-candidate-query`: D 按城市取得可追溯影院候选和影片基础资料，供推荐在调用 A 前确定影院 ID。
- `nearby-cinema-distance`: D 计算用户本次位置到候选影院的直线距离，并提供距离上限过滤和最近优先排序。

### Modified Capabilities

- `fixed-recommendation-candidates`: 将当前固定单影片、单影院候选升级为带约束、三类方案和明确降级规则的推荐结果；保留 D 不伪造票务事实的限制。
- `agent-rank-movie-plan-execution`: 将 Agent 的 `rankMoviePlan` 白名单输入和 D 工具结果升级为完整推荐约束及方案结果。

## Impact

- 主要修改 `backend/.../recommendation/**`；依赖已完成的真实内容快照。`MovieContent` 已有类型和评分，推荐不依赖用户画像、影片标签表、路线服务或 AI 服务。
- 需要 A 确认推荐能否用其现有公开查询完成“多影片、多影院”候选召回；若当前 `ShowQueryService` 只支持单影片和单影院，本 change 不允许 D 绕过 A 的持久化层补查。
- B 负责扩展后的工具登记、当前用户上下文、用户确认后的重新计算、`PLAN_CARD` 发送和 Agent 测试；D 不实现 B 的 ToolRouter、Agent 运行或 SSE。
- C 负责推荐卡片页面消费；D 只提供已确认的结果 DTO，不访问认证表。
