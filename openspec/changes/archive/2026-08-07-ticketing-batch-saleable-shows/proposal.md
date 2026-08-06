# 按日期和多影院查询可售场次

## 背景

D 的推荐模块需要在一个自然日内取得多个影院的真实可购场次，用于后续确定性筛选和排序。现有 `ShowQueryService` 仅支持单个 `movieId + cinemaId`，不能满足该只读场景。

## 范围

- A 在 ticketing/application 中提供按日期和影院 ID 集合查询可售场次的公开只读 Application API。
- 在 ticketing 持久化投影中增加数据库侧有界查询，并排除售罄场次。
- 补充参数、空结果、截断、排序、查询不可用和 MySQL 集成测试。
- 同步 OpenSpec 契约；不新增 REST Controller。

## 非范围

- 不解析城市名、cityCode 或地点字符串；影院 ID 由 D 解析后传入。
- 不修改现有单影片单影院查询行为。
- 不新增或修改 Flyway、业务表、种子或 V013。
- 不实现推荐筛选、评分、标签、推荐记录或 D 的持久化。

## Owner 与协作

- Owner：A（ticketing）。
- 消费方：D recommendation，仅依赖公开 Application API 和 DTO。
- A 不访问 content 的 Entity、Mapper、Repository；D 不访问 ticketing 持久化实现。

## 验收

在 Asia/Shanghai 业务时钟下，查询严格返回未来七天指定日期、指定影院集合内 `ON_SALE` 且余座大于 0 的场次，按开场时间和场次 ID稳定排序；超过 limit 明确返回 `truncated=true`；非法参数和票务查询故障分别返回 100001 与 306003。
