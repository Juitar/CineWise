# movie-available-cinemas-purchase-entry Specification

## Purpose
TBD - created by archiving change movie-available-cinemas-purchase-entry. Update Purpose after archive.
## Requirements
### Requirement: 影片入口必须在正式接口确认后进入选影院

在 A/D 确认正式按影片查询可售影院接口后，系统 SHALL 让首页和 `/movies` 卡片提供语义化、可键盘激活的入口并传递服务端字符串 `movieId`。系统 SHALL 让首页影院卡片以服务端字符串 `cinemaId` 进入 `/cinemas/:cinemaId`，不得直接构造 `/shows`。接口未确认时 MUST NOT 发猜测请求或使用临时 Mock。

#### Scenario: 从影片列表进入选影院

- **GIVEN** 首页或影片列表卡片包含合法 `movieId`
- **WHEN** 用户点击或键盘激活入口
- **THEN** 进入既有 `/movies/:movieId` 影片详情/选影院路由，并由 content、ticketing 模块 Hook 查询影片资料和影院

#### Scenario: 从首页影院卡片进入影院详情

- **GIVEN** 首页影院卡片包含合法 `cinemaId`
- **WHEN** 用户点击或键盘激活入口
- **THEN** 进入 `/cinemas/:cinemaId`，不构造 `/shows` 地址

### Requirement: 影院选择必须只跳转既有场次页

系统 SHALL 展示服务端影院摘要、可售统计、来源和时间；选择影院 SHALL 只跳转 `/shows?movieId={movieId}&cinemaId={cinemaId}`，不重组场次、价格、库存或座位。

#### Scenario: 正常列表跳转

- **GIVEN** 接口返回一个或多个合法可售影院
- **WHEN** 用户激活某影院
- **THEN** URL 同时含原 `movieId` 和所选 `cinemaId`，后续 `/shows` 重新查场次

### Requirement: 页面必须覆盖异常和来源状态

系统 SHALL 处理加载、空结果、失败、重试、过期、降级和离线。合法但未知、下线或无排期影片在本接口中均返回空结果，页面显示“暂无可售影院”且隐藏购票入口；故障不得伪装为空。`contentExpired=true` 仅表示影院基础资料过期，页面必须提示资料时效，但仍允许用户进入场次页重新查询权威可售场次。

#### Scenario: 空、失败和过期资料

- **GIVEN** 合法影片分别返回空数组、可重试错误或 `contentExpired=true` 的影院记录
- **WHEN** 页面处理响应
- **THEN** 空数组显示“暂无可售影院”且无购票入口，失败可重试；过期资料显示时效提示但保留使用原 `movieId` 与记录 `cinemaId` 进入场次页的入口

#### Scenario: 影片详情不存在或查询失败

- **GIVEN** `GET /api/v1/movies/{movieId}` 返回 404 或可重试错误
- **WHEN** 页面处理响应
- **THEN** 404 显示影片不存在；失败显示重试入口，且不得用可售影院空结果替代影片详情错误

### Requirement: PC、移动端和键盘均可用

系统 SHALL 共享 API、DTO、Hook 和状态处理；入口、返回、重试支持键盘和可见焦点，移动触控目标不小于 44px。

#### Scenario: 移动端键盘操作

- **GIVEN** 用户使用窄屏设备或键盘浏览
- **WHEN** 激活影院入口
- **THEN** 页面不产生阻塞操作的横向溢出，结果与鼠标点击一致

