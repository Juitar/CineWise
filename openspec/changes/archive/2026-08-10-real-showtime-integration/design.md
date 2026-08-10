# 实现设计

## 边界和调用方向

调用方向固定为：`NetStart -> D Provider -> D 排期快照 Port -> A 导入 Application Service -> A 本地票务查询`。

D 只产生“已映射的外部排期快照”。每条快照必须同时拥有稳定的 `externalShowId`、`externalMovieId`、`externalCinemaId`，并经既有身份解析得到唯一的本地 `movieId` 和 `cinemaId`。缺少任一外部 ID、映射不存在、映射歧义或已失效的记录被隔离，不得按片名、影院名、地址、坐标或开场时间猜测匹配。

A 不读取 D 的持久化对象，也不重复维护 NetStart Provider。A 通过公开 DTO/Port 取得候选，决定是否写入自己的本地沙箱数据。D 不调用 A 的 Mapper、Repository、Entity 或 Controller。

## 候选快照 DTO

`ExternalShowtimeSnapshot` 至少包含：

- `provider`：固定来源标识，例如 `NETSTART_MAOYAN`；
- `externalShowId`、`externalMovieId`、`externalCinemaId`：稳定外部身份；
- `movieId`、`cinemaId`：已映射的本地十进制字符串 ID；
- `startTime`、`endTime`：带偏移的 ISO 8601 时间，按 `Asia/Shanghai` 解释；
- `listedPrice`：可空的外部标价字符串；仅作参考，不是本地交易价格；
- `durationMinutes`：由 Provider 的 `dur` 规范化得到的正整数片长；只供 A 计算本地预计结束时间；
- `auditoriumText`：由 Provider 的 `th` 得到的可空影厅展示文本；不能当作稳定外部影厅 ID；
- `priceSemantic=REFERENCE_ONLY`：明确 A 必须自行决定本地价格；
- `source`、`dataAt`、`expiresAt`、`isExpired`、`degraded`、`fallbackType`；
- `qualityStatus`：`ACCEPTED`、`SANDBOX_REFERENCE` 或被隔离的失败原因摘要。

外部余座、座位图、订单、支付和退款字段不进入 DTO。若 Provider 同时返回这些字段，D 丢弃它们，不缓存、不持久化、不转发。

## 查询、时效和降级

D 按城市目录将城市名解析为内部 `providerCityId`，只在 Provider 调用边界使用。调用者传入的是本地 `cinemaIds`、业务日期以及可选的本地 `movieIds`；D 在内部反查已确认的外部身份。单次请求的影院 ID 最多 100 个，空集合返回空结果，不发起 Provider 调用。

已于 2026-08-07 用脱敏请求核验 `GET /cinema/shows?ci={providerCityId}&cinemaId={externalCinemaId}`；结果返回影片 ID、`dur`、`showDate`、`seqNo`、`tm`、`th` 和 `vipPrice`，但不返回余座、座位图或可靠散场时间。`seqNo` 只在 Provider/影院范围内使用，缺失时隔离；`th` 只是展示文本，不是稳定外部影厅 ID。D 只查询当天至未来 7 天，成功快照的 `expiresAt=min(startTime, dataAt + 10 分钟)`；具体字段表见 `provider-evidence.md`。所有 Provider 时间统一转换为 `Asia/Shanghai`；只有身份有效、开场时间有效且有正 `durationMinutes` 但没有可靠 `endTime` 的记录才标记为 `SANDBOX_REFERENCE` 并放入 `snapshots`，其他无可靠结束信息的记录进入 `rejectedSnapshots`。

Provider 调用复用现有学习用途保护：本地限流、连接/读取超时，以及仅针对连接失败或 5xx 的一次短重试；429、不可重试 4xx、字段不合格和身份失败不重试。Provider 调用在数据库事务外执行。成功的合格候选写入 D 自己的快照；失败不会删除最后一份成功快照。

读取顺序为“未过期快照 -> 明确标记的未过期缓存（如实现） -> 空结果或不可用”。本 change 不把过期排期快照回退为可导入候选，也不回退到 Demo 排期。Provider 不可用但仍有未过期快照时返回 `degraded=true` 和 `fallbackType=SNAPSHOT`；没有可用快照时返回不可用，不伪造空排期。

## 失败和隐私

参数非法使用 `100001`；身份未找到、歧义和失效分别沿用 `303005`、`303006`、`303007`。Provider 整体不可用使用 `303004`，并在结果或审计中细分 `RATE_LIMITED`、`TIMEOUT`、`NETWORK`、`UPSTREAM_5XX`、`INVALID_DATA` 和 `INTERNAL`。没有任何合格候选是正常空结果；Provider 故障不是空结果。

## A 已确认的导入规则

- 公开入口为 `com.miaoyu.ticket.content.application.ExternalShowtimeQueryPort#query`。
- `QueryResult` 返回 `snapshots` 和 `truncated`；候选最多 200 条，按 `provider + externalCinemaId + externalShowId` 去重。`ACCEPTED` 候选可导入真实本地交易场次，`SANDBOX_REFERENCE` 候选可用于创建明确标识的本地沙箱参考，但不能描述为外部真实交易场次。
- `QueryResult` 同时返回 `rejectedSnapshots`、`rejectedTruncated`：拒绝明细最多返回 200 条，超出部分只通过 `rejectedTruncated=true` 表示；它不写入 D 的成功快照、不计入成功候选上限，也绝不能由 A 导入。`snapshots` 可包含 `ACCEPTED` 和 `SANDBOX_REFERENCE`：A 只能将 `ACCEPTED` 导入为真实本地交易场次，也可将 `SANDBOX_REFERENCE` 用于创建明确标识的本地沙箱场次；`rejectedSnapshots` 不得导入。每条记录带 `qualityStatus`；身份隔离带稳定的 `303005/303006/303007`。
- `startTime`、`endTime`、`dataAt` 和 `expiresAt` 均为带 `Asia/Shanghai` 偏移的 `OffsetDateTime`。
- A 对 `qualityStatus=ACCEPTED`、`isExpired=false`、`degraded=false`、`fallbackType=null` 且 `endTime != null && endTime > startTime` 的候选执行真实本地交易场次导入；对 `SANDBOX_REFERENCE` 候选可按 `durationMinutes` 和 `auditoriumText` 创建明确标识的本地沙箱影厅、预计结束时间、座位和本地价格，但不得宣称这些是外部真实交易事实。
- NetStart 当前未核验到可靠 `endTime`，但返回正 `dur` 时记录会以 `SANDBOX_REFERENCE` 进入 `snapshots`，仅供 A 创建本地沙箱事实；D 不把 `dur` 推算成外部真实 `endTime`。
- `listedPrice` 只能作为参考价；本地沙箱价格由 A 配置，外部价格变化不得覆盖已导入场次或订单价格。

日志和审计只记录来源、城市名、本地 ID 数量、脱敏外部 ID 摘要、结果数量、失败分类、耗时和时间戳。不得记录 `ci`、Key、Cookie、完整原始响应、精确地点原文或座位/订单数据。

## A 的导入规则

A 只接收 `qualityStatus=ACCEPTED`、未过期且 `degraded=false` 的候选。A 在自己的 Application Service 中再次校验时间、重复外部场次 ID 和本地影片/影院存在性，再决定是否写入本地沙箱场次、影厅、座位和价格。外部 `listedPrice` 不是本地价格的直接写入值；余座始终以 A 的本地模拟库存为准。

导入失败只隔离对应候选，不修改既有 Mock 场次、座位、订单、电子票或退款。A 的 `SaleableShowBatchQueryService` 继续使用本地 `ON_SALE`、未开场、余座大于零和既有时效规则，查询不可用仍为 `306003`。
