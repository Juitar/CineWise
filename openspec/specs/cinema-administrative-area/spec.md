# cinema-administrative-area Specification

## Purpose
TBD - created by archiving change resolve-cinema-administrative-area. Update Purpose after archive.
## Requirements
### Requirement: NetStart 影院地址必须解析已登记行政区域

系统 SHALL 在标准化 NetStart 影院搜索结果和影院详情时，按查询城市已登记的行政区名称，从上游地址中识别行政区域。系统 SHALL 优先返回最长匹配的名称，支持区、县和县级市；不得仅按单个“区”字截取文本。

#### Scenario: 长沙县级市地址

- **GIVEN** 查询城市代码为 `430100`
- **AND** 上游地址为“浏阳市镇头镇华嘉时代广场B1栋412号”
- **WHEN** 系统标准化该影院
- **THEN** `CinemaContent.area` 为“浏阳市”

#### Scenario: 行政区与县地址

- **GIVEN** 上游地址包含当前城市已登记的“岳麓区”或“长沙县”
- **WHEN** 系统标准化该影院
- **THEN** `CinemaContent.area` 分别为完整的“岳麓区”或“长沙县”

### Requirement: 未确认的地点文本不得伪造行政区域

系统 SHALL 在地址未包含当前城市已登记行政区域时返回“未知区域”。系统 MUST NOT 把“住宅区”“商业区”等地点名称当作行政区。

#### Scenario: 地点名称包含区字

- **GIVEN** 查询城市代码为 `430100`
- **AND** 上游地址为“万家丽住宅区1号”
- **WHEN** 系统标准化该影院
- **THEN** `CinemaContent.area` 为“未知区域”

