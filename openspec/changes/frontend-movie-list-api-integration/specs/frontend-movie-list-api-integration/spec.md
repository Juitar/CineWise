## ADDED Requirements

### Requirement: 影片列表必须读取后端公开接口

系统 SHALL 通过前端唯一公共 `apiRequest<T>()` 调用 `GET /api/v1/movies`，并使用后端返回的 `records/total/page/size` 渲染 `/movies`。页面 MUST NOT 使用硬编码影片数组作为成功数据，也 MUST NOT 建立第二套 REST 客户端。

#### Scenario: 首次打开影片列表

- **GIVEN** 用户打开不带筛选参数的 `/movies`
- **WHEN** 页面开始加载影片
- **THEN** content 模块请求 `GET /api/v1/movies?page=1&size=20`
- **AND** 页面使用响应中的 `movieId/title/posterUrl/genres/durationMinutes/rating` 渲染影片卡片
- **AND** 每张卡片使用字符串 `movieId` 作为稳定标识

#### Scenario: 后端返回空页

- **GIVEN** 影片查询成功且 `records` 为空
- **WHEN** 页面完成加载
- **THEN** 页面显示影片空数据提示
- **AND** 保留当前筛选条件
- **AND** 不回填本地演示影片

### Requirement: 页面只能提交后端已支持的查询条件

系统 SHALL 支持 `keyword`、`genre`、`page`、`size`，并将可分享的筛选与分页状态保存到 URL。页面 MUST NOT 把榜单、地区、年份、上映状态或客户端排序作为已生效的后端筛选展示。

#### Scenario: 用户修改关键词或分类

- **GIVEN** 用户正在查看影片列表
- **WHEN** 用户提交 `keyword` 或选择 `genre`
- **THEN** 页面将合法条件写入 URL
- **AND** 将 `page` 重置为 1
- **AND** 使用新条件重新请求后端

#### Scenario: 用户翻页后刷新页面

- **GIVEN** URL 中存在合法的 `keyword/genre/page/size`
- **WHEN** 用户刷新或直接访问该 URL
- **THEN** 页面从 URL 恢复条件并发起同一查询
- **AND** 不依赖上一个页面的内存状态

#### Scenario: URL 参数非法

- **GIVEN** URL 中的 `page` 小于 1、`size` 不在 1 至 50 内或筛选字段超过后端限制
- **WHEN** 页面解析查询条件
- **THEN** 页面使用安全默认值或显示可修正的参数提示
- **AND** 不发出已知会被后端拒绝的请求

### Requirement: 页面必须如实展示内容来源和时效

系统 SHALL 分别判断 `source/sourceType/dataTime/expiresAt/isExpired/degraded/fallbackType`。页面 MUST NOT 将 `MOCK`、`CACHE`、`SNAPSHOT`、过期或无法验证的结果称为实时数据。

#### Scenario: 返回有效实时来源

- **GIVEN** 响应为 `sourceType=LIVE`、`isExpired=false`、`degraded=false`
- **WHEN** 页面展示影片列表
- **THEN** 页面显示来源和更新时间
- **AND** 不显示演示或降级文案

#### Scenario: 返回降级来源

- **GIVEN** 响应为 `degraded=true`
- **WHEN** 页面展示影片列表
- **THEN** 页面显示“当前为降级数据”
- **AND** `fallbackType=MOCK/CACHE/SNAPSHOT` 分别显示“演示数据/缓存数据/历史快照”

#### Scenario: 返回过期或无法验证的来源

- **GIVEN** 响应 `isExpired=true`，或来源字段缺失、无法识别、未通过运行时校验
- **WHEN** 页面展示来源信息
- **THEN** 过期数据显示“数据已过期，仅供参考”
- **AND** 无法验证的来源显示“来源尚未验证”
- **AND** 过期、降级和来源未验证可以同时显示

### Requirement: 查询状态必须可恢复且旧响应不得覆盖新条件

系统 SHALL 处理首次加载、保留旧数据刷新、失败、手动重试、取消和离线状态。条件变化时，旧请求结果 MUST NOT 覆盖当前条件对应的结果。

#### Scenario: 快速连续修改筛选

- **GIVEN** 旧筛选请求尚未完成
- **WHEN** 用户提交新的筛选条件
- **THEN** 前端取消旧请求或通过查询标识丢弃旧响应
- **AND** 页面最终只显示最新 URL 条件对应的数据

#### Scenario: 查询失败后手动重试

- **GIVEN** 后端返回错误或网络请求失败
- **WHEN** 页面展示错误状态
- **THEN** 页面保留当前 URL 条件和可用的 `traceId`
- **AND** 用户可以手动重试同一个只读查询
- **AND** 页面不显示硬编码影片作为降级结果

#### Scenario: 已有数据时断网

- **GIVEN** 页面已成功加载影片且随后离线
- **WHEN** 刷新查询失败
- **THEN** 页面保留已加载影片并标记为当前页面内存中的只读快照
- **AND** 不把内存快照持久化到浏览器业务缓存

### Requirement: 影片列表必须覆盖桌面和移动展示

系统 SHALL 复用同一 DTO、API 和查询 Hook，在桌面与移动布局中展示相同的影片结果和来源状态。

#### Scenario: 切换到移动视口

- **GIVEN** 同一影片查询已经成功
- **WHEN** 页面在小于 1024px 的视口展示
- **THEN** 页面使用移动单列或适合触控的网格布局
- **AND** 筛选控件的触控目标不小于 44px
- **AND** 不发起一份独立的移动端影片请求
