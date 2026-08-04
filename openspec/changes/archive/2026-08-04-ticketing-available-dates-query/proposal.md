# Proposal: ticketing-available-dates-query

## 背景

场次选择页需要先查询指定影片和影院未来可选的排期日期，再按用户选择的日期查询具体场次。A 的详细设计、总体后端设计和前端应用设计均已声明 `GET /api/v1/shows/available-dates`，但现有 `content-and-show-selection-flow` 明确将可售日期排除在原实现范围之外，当前后端也没有该接口。

## 目标

- 由 A 的 `ticketing` 模块提供公开、只读的可售日期 Application Service 和 REST 接口。
- 使用注入的业务 `Clock`，按未来七天窗口聚合指定 `movieId + cinemaId` 下 `ON_SALE` 且未开场的场次。
- 返回按日期升序排列的 `date + showCount`；没有匹配排期时返回空数组，不伪造日期。
- 同步 OpenAPI、REST Mock 夹具、H2 契约测试和可选真实 MySQL 只读集成测试。

## 非目标

- 不修改 `/api/v1/shows` 的筛选或售罄展示语义。
- 不按座位余量过滤日期；本接口的“可售日期”沿用前端冻结规则，仅要求场次 `ON_SALE` 且未开场。
- 不实现 B 的 `queryAvailableDates` Agent Tool Adapter、计划或 SSE。
- 不实现 C 的登录、路由、公共请求封装或页面组件。
- 不访问 D 的 Entity、Mapper、Repository 或内容表，也不返回影片、影院展示字段。
- 不新增或修改 Flyway、数据表、索引、种子、Redis 或配置。

## Owner 与协作

- A：接口、票务查询、OpenAPI、Mock 和测试。
- C：后续在公共前端请求层消费该公开 REST 接口；本 change 不修改 C 的代码。
- B：后续通过 A 的公开 Application Service 适配 Agent Tool；本 change 不修改 B 的代码或 Tool 契约。
- D：无实现依赖；影片和影院 ID 仅作为已选条件，不读取 D 的内容事实。

## 验收

- 合法 `movieId + cinemaId` 返回未来七天内按日期聚合的可售场次数量。
- 已开场、恰好到达开场时刻、停售、窗口外及其他影片/影院的场次不计入结果。
- 空结果返回 `dates: []`；缺少或非法 ID 返回 HTTP 400 / `100001`。
- 接口公开匿名可读，OpenAPI 字段为 `date` 和非负整数 `showCount`。
- 不产生数据库写入，不需要 SQL 或跨 Owner 生产实现。
