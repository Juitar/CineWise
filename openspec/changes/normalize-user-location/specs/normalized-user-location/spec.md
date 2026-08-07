## Purpose

统一 D 模块的位置输入、影院位置和外部地图适配，防止私有 Provider 标识或精确用户坐标越过应有边界。

## ADDED Requirements

### Requirement: 用户位置必须使用统一的类型化坐标

系统 MUST 用 `ResolvedGeoPoint(longitude, latitude, granularity)` 在 D 模块内传递用户位置。经度必须位于 -180 至 180，纬度必须位于 -90 至 90，且两者的有效小数位不得超过 6 位；空值、越界或超过精度的输入必须被拒绝。

#### Scenario: 浏览器提交合法设备坐标

- **WHEN** C 将合法经纬度提交给 D 的位置入口
- **THEN** 系统返回 `granularity=DEVICE` 的统一坐标，且不做地址字符串拼接或地理编码

#### Scenario: 浏览器提交缺失或越界坐标

- **WHEN** 经度或纬度为空、越界或超过 6 位小数
- **THEN** 系统拒绝该请求，且不创建距离上下文、不请求外部 Provider、不记录精确坐标

#### Scenario: 浏览器坐标超过 6 位小数

- **WHEN** C 将 `position.coords.longitude/latitude` 的原始数值提交给 D，且任一坐标超过 6 位小数
- **THEN** `BrowserUserLocationAdapter` MUST 先校验原始经度位于 `[-180, 180]`、原始纬度位于 `[-90, 90]`，原始值合法后再按 `HALF_UP` 统一四舍五入到 6 位小数并构造坐标；C 不得自行截断，也不得把原始坐标写入 URL、存储、日志、Mock 或 Agent/SSE 消息

### Requirement: 地点文本必须经地理编码并保留粒度

系统 MUST 仅接收 B 已提取并确认的 `placeText`，通过高德地理编码 Adapter 解析为统一坐标。解析结果必须按 Provider 返回级别映射为 CITY、DISTRICT、POI 或 ADDRESS；多个候选不能唯一确定时必须返回结构化候选或明确错误。

#### Scenario: 唯一城市候选

- **WHEN** B 传入已确认的城市名称，且地理编码返回唯一城市候选
- **THEN** 系统返回 `granularity=CITY` 的代表点

#### Scenario: 多个地点候选

- **WHEN** 高德地理编码返回多个无法唯一确定的候选
- **THEN** 系统不选择其中任何一个，并将候选或歧义错误返回给 B 继续询问

### Requirement: 粒度必须限制个人距离和路线

系统 MUST 只允许 DEVICE、POI、ADDRESS 粒度用于个人距离排序和路线规划。CITY 与 DISTRICT 只能用于城市范围查询或展示，不能替代设备位置。

#### Scenario: 城市代表点请求距离排序

- **WHEN** 距离或路线用例收到 `granularity=CITY` 的位置
- **THEN** 系统拒绝个人距离或路线计算，并提示需要设备、POI 或地址位置

### Requirement: 精确用户坐标不得持久化或传播

系统 MUST 仅在当前请求和 D 的短期进程内距离上下文保存精确用户坐标；不得写入 MySQL、Redis、日志、URL、画像、快照、Agent Prompt、Agent 轨迹或 SSE。

#### Scenario: 外部 Provider 完成或失败

- **WHEN** 路线、地理编码或附近查询完成、超时或失败
- **THEN** 系统清除本次请求的精确用户坐标，且持久化、缓存、日志和响应以外的轨迹不包含该坐标

### Requirement: Provider 专用位置标识不得进入业务契约

系统 MUST 在 Provider 基础设施层内把统一坐标转换为高德天气 `adcode` 或高德路线 `lng,lat`。业务层、数据库、公开 DTO 与 Agent Tool Schema 不得使用 `adcode`，坐标字符串只允许在 HTTP 适配层出现。

#### Scenario: 根据影院查询天气

- **WHEN** 天气查询收到有效 `cinemaId`
- **THEN** 系统读取已确认的影院数值坐标，逆地理得到 `adcode` 并以 `adcode` 缓存天气；不能取得 `adcode` 时才按已登记的 `cinemaArea` 映射回退并标记降级

### Requirement: 影院坐标必须独立于用户输入

系统 MUST 通过 `CinemaLocationQueryService` 按 `cinemaId` 读取已确认的影院坐标。缺失或越界的影院坐标必须导致路线、天气或附近餐饮明确不可用，不能以地址猜测或使用 `0,0`。

影院坐标和逆地理失败时允许使用的 `area`，都只允许来自 `source.type=LIVE` 的真实内容。通用内容查询回退到 Demo 时，即使 Demo 影院含有坐标或区域，系统 MUST 把该影院位置视为不可用；内容目录无法读取时仍必须返回 `303004`，不得伪装成空位置。

#### Scenario: 影院坐标缺失

- **WHEN** 影院详情没有合法经纬度
- **THEN** 系统返回明确的不可用结果，不调用天气、路线或餐饮 Provider

#### Scenario: 仅有 Demo 影院坐标

- **WHEN** 真实影院目录缺失，内容查询回退到带合法坐标和区域的 Demo 影院
- **THEN** `CinemaLocationQueryService` 不返回坐标或区域；路线、天气和附近餐饮不调用各自 Provider
