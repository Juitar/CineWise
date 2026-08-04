# D 内容与固定推荐回归用例 v1

本清单只记录固定 Demo 内容、缓存/快照回退和固定推荐的可重复结果。所有用例使用
`demo-content-v1`、`fixed-rec-v1` 与固定业务时钟 `2026-08-03T09:00:00+08:00`；不填写用户身份、精确位置、密码、Token 或其他敏感输入。

| caseId | module | priority | preconditions | input | mockProfile | expectedTool | expectedBusinessRefs | expectedResult | forbiddenResult | dataSourceExpectation | timeoutMs | owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | --- |
| D-CONT-001 | content | P1 | classpath 存在 `demo-content-v1` | 影片关键字 `星河` | 无 | 无 | `mock-movie-01` | 固定影片、来源 `DEMO_CONTENT`、`MOCK`、顺序稳定 | 场次、价格、库存 | `MOCK`/`DEMO_CONTENT` | 500 | D |
| D-CONT-002 | content | P1 | classpath 存在 `demo-content-v1` | 城市 `330100`、关键字 `影城` | 无 | 无 | `mock-cinema-01` 至 `mock-cinema-04` | 四家影院按目录顺序返回，带来源和时间 | 第二套影院或随机顺序 | `MOCK`/`DEMO_CONTENT` | 500 | D |
| D-CONT-003 | content | P1 | 缓存有未过期标准内容 | 合法影片查询 | 无 | 无 | 查询条件缓存键 | 返回 `CACHE`、`degraded=true` | 直接跳到快照或 Demo | `CACHE` | 500 | D |
| D-CONT-004 | content | P1 | 缓存未命中或不可用，快照未过期 | 合法影片查询 | 无 | 无 | 标准化快照 | 返回 `SNAPSHOT`、`degraded=true`、未过期 | 把快照标成实时数据 | `SNAPSHOT` | 500 | D |
| D-CONT-005 | content | P1 | 仅有未超过七天的过期快照 | 合法影片查询 | 无 | 无 | 标准化快照 | 返回 `SNAPSHOT`、`isExpired=true` | 可购候选或刷新写入 | `SNAPSHOT` | 500 | D |
| D-CONT-006 | content | P1 | 缓存、快照、Demo 都无结果 | 未知实际影片 ID | 无 | 无 | 无 | 返回 `303004` | 补造影片、场次、价格或库存 | 无 | 500 | D |
| D-REC-001 | recommendation | P1 | A 场次查询无结果 | `movieId=101`、`cinemaId=201`、日期 `2026-08-03` | 无 | `rankMoviePlan` | `101`、`201` | `purchaseEligible=false`、`missingFactors=[SHOWTIME]` | `showId`、价格、`PLAN_CARD` | `FIXED_RECOMMENDATION` | 500 | D |
| D-REC-002 | recommendation | P1 | A 返回完整且未过期场次事实 | 同 D-REC-001 | 无 | `rankMoviePlan` | A 返回的 `showId/movieId/cinemaId/price/startTime/expiresAt` | 只引用 A 返回事实，价格两位小数，顺序固定 | D 自建场次或库存 | `TICKETING:MOCK` | 500 | D |
| D-REC-003 | recommendation | P1 | A 查询不可用、候选缺字段或已过期 | 同 D-REC-001 | 无 | `rankMoviePlan` | `101`、`201` | 返回不可购内容候选，不抛出可购结果 | `showId`、价格、库存或可购卡片 | `FIXED_RECOMMENDATION` | 500 | D |
