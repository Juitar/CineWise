## Why

当前代码把用户坐标、影院区域名、城市编码、NetStart `ci` 和高德 `adcode` 混作 location：路线把 `cinemaArea` 当终点，天气和餐饮按区域字符串查找，浏览器坐标也没有唯一的校验入口。结果是距离、路线和天气可能针对错误地点，且公开 REST 与 Agent 工具的修改会同时影响 B、C。

## What Changes

- 新增 D 负责的 `geo` 模块，统一表达经纬度和位置粒度，并校验空值、范围及小数精度。
- 新增浏览器坐标和已确认 `placeText` 的适配入口；城市、行政区、POI、完整地址由高德地理编码适配器解析，多候选不得静默选择。
- 将路线、天气、附近餐饮和距离排序改为传递类型化坐标；`lng,lat` 字符串仅允许在 Provider HTTP 适配层组装。
- 新增影院坐标公开查询服务，天气按影院坐标逆地理得到 `adcode`。
- **BREAKING**：路线请求不再接收 `originValue`；天气 Agent 工具改为只接收 `cinemaId`；相关 REST/OpenAPI、B Tool Schema 与 C 前端类型需要同步确认后再修改。

## Capabilities

### New Capabilities

- `normalized-user-location`: 统一用户位置、影院位置和 Provider 专用位置转换，并限制精确坐标的生命周期。
- `location-aware-travel-query`: 天气、路线和附近餐饮按影院 ID 与类型化坐标查询，不再把区域文本作为位置。
- `location-aware-distance-recommendation`: 距离上下文仅接受允许进行个人距离计算的位置粒度。

### Modified Capabilities

- 无；相关主规格尚未归档，现有 active change 的实现由本 change 在确认后统一替换。

## Impact

- D：`geo`、`content`、`recommendation`、`travel` 的 Application 与 Provider Adapter，以及相应测试和配置。
- B：`GetWeatherTool`、地点文本传递和 Agent Tool Schema；B 必须确认 `placeText` 的唯一候选交互和 `cinemaId` 输入。
- C：浏览器经纬度上传、路线请求 DTO、OpenAPI 生成类型、Mock 与页面内存清理；C 必须确认前端字段替换和授权失败展示。
- A：不得访问 D 的位置实体、Mapper、Repository；本变更不新增表、迁移或票务字段。
