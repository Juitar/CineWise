## Purpose

让天气、路线和附近餐饮基于已确认的影院坐标和用户位置粒度工作，避免把地址、区域名和行政编码误用为坐标。

## ADDED Requirements

### Requirement: 天气工具必须以影院标识查询

`GetWeatherTool` MUST 只接受 B 已确认的 `cinemaId`，并由 D 校验影院坐标后查询天气。它不得接受或转发地址、区域名、用户 ID、用户坐标或模型生成的位置文本。

#### Scenario: B 提供有效影院标识

- **WHEN** Tool Schema 提供有效 `cinemaId`
- **THEN** D 根据影院坐标查天气并返回来源、数据时间、有效期、过期标识和降级信息

#### Scenario: Tool 输入不是影院标识

- **WHEN** Tool Schema 包含地址、区域名、坐标或用户 ID
- **THEN** Schema 校验失败，且 D 不调用天气 Provider

### Requirement: 路线必须使用两个合法坐标

路线 Application、Domain、Command 与 Provider 端口 MUST 传递类型化起点和影院终点。`BasicRouteCommand.originValue`、Provider 的字符串起点/终点以及将 `cinemaArea` 作为目的地的实现必须删除。

#### Scenario: 合法个人起点和影院终点

- **WHEN** 当前用户确认第三方共享，起点粒度为 DEVICE、POI 或 ADDRESS，且影院坐标合法
- **THEN** HTTP 适配层组装一次 `longitude,latitude` 请求高德，完成后丢弃用户位置和路线几何

#### Scenario: 城市粒度请求路线

- **WHEN** 起点粒度为 CITY 或 DISTRICT
- **THEN** 系统拒绝路线规划，且不请求高德

### Requirement: 附近餐饮必须以影院坐标查询

餐饮 Provider 与缓存 MUST 以影院数值坐标和半径为输入；`cinemaArea` 只能保留为展示字段，不得作为附近餐饮的查询位置。直线距离必须在结果中明确标注为直线距离。

#### Scenario: 影院坐标可用

- **WHEN** 当前用户查询本人任务的附近餐饮且影院坐标有效
- **THEN** 系统用影院数值坐标查询、缓存并返回餐饮结果
