# 推荐能力第一章：当前证据与 Owner 确认单

本文件记录 2026-08-05 在 `feat/real-recommendation` 工作树的代码核对和 Owner 回复。B、C 已确认的事项可作为实现依据；A 已确认本期不做任何推荐新表或 Flyway，批量查询接口交付前 D 不修改其调用适配器。

## 1. A：批量可售候选与迁移

### 当前代码证据

- `ShowQueryService.queryShows(ShowQuery)` 的输入固定为 `movieId`、`cinemaId`、`date`、`timeFrom`、`timeTo`。
- `ShowController` 的场次列表接口同样要求 `movieId` 与 `cinemaId`；当前没有按城市、日期、人数和预算获取多影片、多影院候选的公开 Application API。
- 本期不创建 `movie_tag`、`recommendation_record` 或 V013；推荐主流程不依赖新表。

### A 已确认与仍需确认

1. A 已在最新 `dev` 提供 `SaleableShowBatchQueryService` 独立批量可售场次只读 Application API，不复用旧单影片单影院查询。
2. D 先通过内容公开应用 API 将城市解析为影院 ID；A 只接收 `date + cinemaIds` 查询票务事实。
3. 可售条件固定为 `ON_SALE`、未开场、余座大于 0；`source` 使用 A 已有 `dataType`。空结果返回空列表，查询不可用返回 `306003`，不得伪装为空列表。
4. A 的实现为：一次请求最多 100 个影院 ID；最多返回 200 条候选，超出时 `truncated=true`；`expiresAt=min(show.startTime,dataAt+60秒)`，D 在 `now>=expiresAt` 时不再生成可购方案。`SaleableShowBatchQueryServiceTest` 已覆盖该行为。
5. 推荐历史页、`recommendation_record` 完整表设计和新的 Flyway 版本均暂缓到后续独立 change。

## 2. B：工具、运行和卡片

### 当前代码证据

- `RankMoviePlanExecutionAdapter` 当前只从已校验槽位读取 `movieId`、`cinemaId`、`date`、`timeFrom`、`timeTo`，并构造旧版 `RankMoviePlanCommand`。
- B 已将 `runId` 放入可信 `ToolContext`，可供 D 做推荐记录关联；D 不需要、也不应把 `runId` 放进用户输入命令。
- 当前工具结果类型为 `FixedRecommendationResult`；完整推荐需要改为 `RecommendationPlanResult`，并由 B 在节点成功后发送固定 `PLAN_CARD`。

### B 已确认

1. Command 必填 `cityCode/date/ticketCount`；可选字段、禁止字段、`runId` 边界和 JSON 数组传递规则已经写入本 change。
2. 结果整体使用 `usedProfile/source/dataAt/expiresAt/degraded`；方案、空方案和放宽建议字段已确认。
3. B 在同一 change 扩展白名单、计划校验、槽位解码、执行适配器和 `PLAN_CARD`，不重排或改写 D 结果。
4. B 对“附近、近一点、离我近”意图发送 `QUESTION(questionType=LOCATION_AUTHORIZATION, distanceContextId, distancePreference=NEAREST)`；用户确认后才在可信 `ToolContext.distanceContextId` 放入同一 UUID。该字段可空、仅用于本次运行，运行结束、拒绝、失败或到期后删除；经纬度、地址和定位来源不进入 B 的 Command、槽位、模型、持久化、日志、缓存、SSE 或卡片。

## 3. C：展示和当前用户边界

### 当前代码证据

- `SecurityContextCurrentUserAccessor` 已提供可信当前用户读取能力。
- 现有前端推荐卡片为展示结构，尚未发现对 `PLAN_CARD` 推荐结果字段或推荐记录分页查询的实际消费实现。

### C 已确认

1. 卡片、空方案、过期、来源和公开字段范围已写入本 change；本期不做推荐记录页。
2. C 只在收到 B 的 `LOCATION_AUTHORIZATION` 类型化 QUESTION 且用户确认后申请浏览器定位，坐标直接调用 D 的一次性上下文接口；定位拒绝或不可用时继续普通推荐。
3. D 的位置上传成功使用 HTTP 200 + `Result<null>`，C 继续使用公共 `apiRequest<T>()`，不修改公共请求层或绕过请求封装。
4. C 不读取 D 的表、缓存、原始 Provider 数据或位置资料；经纬度不进入 URL、localStorage、页面持久化状态、埋点或日志。

## 4. D 开工结论

- D 可以实现推荐领域类型、类型过滤、评分和测试；批量场次适配器等待 A 交付公开 API。
- D 当前不得为获得候选而访问 A 的 `movie_show`、Mapper、Repository、Controller 或本应用 HTTP 接口。
- D 当前不得自行占用 Flyway 版本、创建跨模块工具字段、发送 SSE 或修改 C 的页面。
