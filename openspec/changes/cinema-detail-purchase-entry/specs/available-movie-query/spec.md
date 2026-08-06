# Available Movie Query Spec

## ADDED Requirements

### Requirement: 系统必须按影院返回未来可售影片

系统 SHALL 提供公开接口 `GET /api/v1/shows/available-movies?cinemaId={cinemaId}`。接口只统计该影院未来 7 天内 `ON_SALE`、未开场且至少有一场排期的影片，并按影片聚合 `showCount` 和 `nearestStartTime`；不得要求前端拉取全量影片后逐个查询场次。

#### Scenario: 影院存在多个未来可售影片

- **GIVEN** 某影院未来 7 天内存在多个 `ON_SALE` 且未开场的影片场次
- **WHEN** 客户端使用该 `cinemaId` 查询可售影片
- **THEN** 接口按 `movieId` 返回每部影片的标题、海报、场次数、最近开场时间、`contentSource/contentDataTime` 和 `scheduleSource/scheduleDataTime`
- **AND** 结果按最近开场时间升序、`movieId` 升序稳定排列

#### Scenario: 影院没有未来可售影片

- **GIVEN** 某影院没有符合条件的场次
- **WHEN** 客户端查询该影院的可售影片
- **THEN** 接口返回 HTTP 200 和 `movies=[]`
- **AND** 不使用历史、已开场、停售或其他影院的场次补全结果

#### Scenario: cinemaId 参数非法

- **GIVEN** 请求缺少 `cinemaId`，或其为空白、格式非法
- **WHEN** 客户端调用可售影片接口
- **THEN** 接口返回 HTTP 400 和业务码 `100001`
- **AND** 不执行排期聚合查询

### Requirement: 影片展示摘要必须通过公开模块接口补齐

A SHALL 通过 D 的公开影片摘要 Application API 批量取得 `title`、`posterUrl`、内容来源和内容时间，不得访问 D 的 Controller、Entity、Mapper、Repository、缓存或内容表，也不得按标题猜测影片身份。

#### Scenario: 排期影片摘要可用

- **GIVEN** 排期聚合得到影片内部 ID，且 D 的公开接口返回对应摘要
- **WHEN** A 组装可售影片响应
- **THEN** 响应使用 D 返回的标题、海报、`contentSource` 和 `contentDataTime`
- **AND** `showCount`、`nearestStartTime`、`scheduleSource` 和 `scheduleDataTime` 仍以 A 的当前排期查询为准

#### Scenario: 排期影片摘要缺失

- **GIVEN** 某个排期影片无法取得有效标题摘要
- **WHEN** A 组装可售影片响应
- **THEN** A 排除该项并记录可排查的原因
- **AND** 不返回空标题，不按相近名称匹配其他影片，也不影响其他有效影片

#### Scenario: 影片内容目录整体不可用

- **GIVEN** 排期聚合得到至少一个影片 ID，但 D 的缓存、快照和 Demo 均无法提供影片内容目录
- **WHEN** 客户端查询该影院的可售影片
- **THEN** 接口返回 HTTP 503 和业务码 `303004`
- **AND** 不返回 HTTP 200 和 `movies=[]`，避免把内容服务故障显示为影院没有可售影片

### Requirement: 长沙真实影院的演示排期必须明确区分来源

系统 SHALL 为至少一家由 D 解析内部 ID 的长沙 LIVE 影院提供可重复的本地 Mock 排期，使用户能够继续完成选场次、选座、建单和模拟支付。Mock 排期必须保留 `demo-seed` 或等价演示来源；本期不要求接入第三方真实排片，但不得修改影院 LIVE 基础资料或把演示排期描述为 Provider 的真实排片。

#### Scenario: 已确认的长沙影院具有演示排期

- **GIVEN** A、D 已通过公开 Application API 确认目标长沙影院和影片内部 ID
- **WHEN** 固定种子生成并查询该影院的未来可售影片
- **THEN** 接口返回对应影片及 `demo-seed` 演示来源
- **AND** 重复执行种子不生成重复场次，也不覆盖已锁定或已售座位

#### Scenario: 长沙影院没有本地排期

- **GIVEN** 某家 LIVE 影院没有 A 管理的未来可售场次
- **WHEN** 客户端查询该影院的可售影片
- **THEN** 接口返回空数组
- **AND** 不因影院基础资料为 LIVE 而生成价格、库存或购票候选
