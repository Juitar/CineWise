# movie-available-cinemas Specification

## Purpose
TBD - created by archiving change movie-available-cinemas. Update Purpose after archive.
## Requirements
### Requirement: 返回影片的未来可售影院
系统 SHALL 公开 `GET /api/v1/shows/available-cinemas?movieId=&page=&size=`，返回分页影院摘要。`movieId` 为无前导零正十进制字符串；默认 page=1、size=20，size 为 1 至 50。

#### Scenario: 返回可售影院
- **WHEN** 影片存在未来七天 ON_SALE、未开场且至少有一个 AVAILABLE 座位的场次
- **THEN** 返回影院 ID、名称、地址、可售场次数、最近开场时间、内容和排期来源时间，并按最近开场时间、影院 ID 排序

#### Scenario: 没有可售影院
- **WHEN** 合法影片没有可售场次、影片不存在或已下线
- **THEN** 返回 HTTP 200 的空 records

#### Scenario: 查询失败
- **WHEN** 参数非法、内容摘要不可用或票务查询不可用
- **THEN** 分别返回 100001/400、303004/503、306003/503，不伪造空结果
