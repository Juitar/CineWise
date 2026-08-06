## ADDED Requirements

### Requirement: 影片入口必须在正式接口确认后进入选影院

在 A/D 确认正式按影片查询可售影院接口后，系统 SHALL 让 `/movies` 卡片提供语义化、可键盘激活的入口并传递服务端字符串 `movieId`。接口未确认时 MUST NOT 发猜测请求或使用临时 Mock。

#### Scenario: 从影片列表进入选影院

- **GIVEN** 影片卡片包含合法 `movieId`
- **WHEN** 用户点击或键盘激活入口
- **THEN** 进入该影片的详情/选影院路由，并由确认后的模块 Hook 查询影院

### Requirement: 影院选择必须只跳转既有场次页

系统 SHALL 展示服务端影院摘要、可售统计、来源和时间；选择影院 SHALL 只跳转 `/shows?movieId={movieId}&cinemaId={cinemaId}`，不重组场次、价格、库存或座位。

#### Scenario: 正常列表跳转

- **GIVEN** 接口返回一个或多个合法可售影院
- **WHEN** 用户激活某影院
- **THEN** URL 同时含原 `movieId` 和所选 `cinemaId`，后续 `/shows` 重新查场次

### Requirement: 页面必须覆盖异常和来源状态

系统 SHALL 处理加载、空结果、失败、重试、404、过期、降级和离线。合法空结果显示无可售影院且隐藏购票入口；故障不得伪装为空；过期/降级结果不得称为实时可售。

#### Scenario: 空、失败和 404

- **GIVEN** 合法影片分别返回空数组、可重试错误或 HTTP 404
- **WHEN** 页面处理响应
- **THEN** 空数组无购票入口，失败可重试，404 显示影片不存在/已下线并提供返回入口

### Requirement: PC、移动端和键盘均可用

系统 SHALL 共享 API、DTO、Hook 和状态处理；入口、返回、重试支持键盘和可见焦点，移动触控目标不小于 44px。

#### Scenario: 移动端键盘操作

- **GIVEN** 用户使用窄屏设备或键盘浏览
- **WHEN** 激活影院入口
- **THEN** 页面不产生阻塞操作的横向溢出，结果与鼠标点击一致
