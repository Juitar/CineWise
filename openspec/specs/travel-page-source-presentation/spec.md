# travel-page-source-presentation Specification

## Purpose
TBD - created by archiving change travel-page-source-localization. Update Purpose after archive.
## Requirements
### Requirement: 出行页面以中文显示数据来源

系统 MUST 把出行建议页面中用户可见的影院内容、路线和天气来源编码转换为中文名称，不得直接展示内部编码。

#### Scenario: 显示已知来源

- **WHEN** 出行任务中的影院来源为 `NETSTART_MAOYAN`
- **THEN** 页面显示“猫眼”
- **WHEN** 路线来源为 `AMAP_ROUTE`
- **THEN** 页面显示“高德路线”
- **WHEN** 天气建议来源为 `AMAP_WEATHER`
- **THEN** 页面显示“高德天气”

#### Scenario: 显示未知或缺失来源

- **WHEN** 任一用户可见来源为空或不在已知映射内
- **THEN** 页面显示“来源待确认”
- **AND** 页面不得显示该来源的内部编码

### Requirement: 保持来源事实和降级提示

系统 MUST 继续显示后端返回的数据时间、有效期和现有的演示或降级提示；来源中文化不得把演示数据说成实时数据。

#### Scenario: 显示演示天气

- **WHEN** 天气来源为 `DEMO_WEATHER_V1`
- **THEN** 页面显示“演示天气”
- **AND** 页面继续显示已有的演示或降级说明

