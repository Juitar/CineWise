## Purpose

为已支付用户提供可追溯的天气和通用出发建议，并在用户主动操作时安全提供步行、骑行和公交路线及所选路线静态地图；外部服务不可用时不影响购票主流程。

## ADDED Requirements

### Requirement: 用户主动查询三种路线

系统 SHALL 仅在已支付用户主动点击路线入口、确认第三方位置共享说明且浏览器提供本次位置后，为其本人未取消的出行任务显示步行、骑行和公交三张方式卡。系统 MUST 在用户选择一种方式并提交本次坐标、`travelMode`、`taskVersion` 与共享确认后，才查询该方式路线和静态地图。系统 SHALL 不对三种方式评分、排序或推荐，不提供驾车路线、周边餐饮、持续定位、手动地点或 Agent 对话位置收集。

#### Scenario: 用户首次跳过后再次请求路线
- **GIVEN** 用户此前在出行建议页跳过路线规划
- **WHEN** 用户之后主动点击路线入口并完成位置共享确认
- **THEN** 页面才请求浏览器本次位置并展示三张方式卡，且尚不调用高德
- **AND** 用户选择一种方式后，系统才返回该方式的距离、预计时长、换乘摘要和来源时效字段

#### Scenario: 用户拒绝位置授权
- **GIVEN** 用户点击路线入口
- **WHEN** 用户拒绝位置共享说明或浏览器拒绝定位
- **THEN** 系统不调用高德且不保存位置
- **AND** 页面继续展示影院地址、天气建议和再次请求入口

### Requirement: 路线终点必须是已购票影院

系统 SHALL 以任务绑定的 `cinemaId` 查询影院静态坐标作为路线终点，并在调用路线 Provider 前校验任务属于当前用户且未取消。新支付事件创建的任务 MUST 由合法 `String cinemaId` 解析并写入 V013 新增的 `travel_task.cinema_id`；历史任务和影院 ID 非法的退款先到墓碑可保持 `NULL`。系统 MUST 不使用 `cinemaArea`、用户输入地址或默认坐标代替影院终点。

#### Scenario: 历史任务或影院没有可用坐标
- **GIVEN** 本人历史任务的 `cinema_id` 为 `NULL`，或该影院没有合法坐标
- **WHEN** 用户请求路线
- **THEN** 系统返回路线不可用且不调用高德
- **AND** 不泄露其他影院或任务的信息

#### Scenario: 支付事件缺少影院业务 ID
- **GIVEN** A 发送的支付成功事件缺少或携带非法 `cinemaId`
- **WHEN** D 在事务提交后处理该事件
- **THEN** 系统不创建新任务，并输出只含 `eventId` 的受控错误供 A 的 PAID 补偿携带合法影院 ID 后恢复
- **AND** 不回滚已提交的支付事务

### Requirement: 动态出行数据必须标明来源、时效和降级

系统 SHALL 为天气和路线响应提供 `source`、`dataTime`、`expiresAt`、`isExpired`、`degraded` 和 `fallbackType`。外部 Provider 未确定、超时或失败时，天气 MUST 使用有效缓存或版本化 Demo 数据；路线和静态地图 MUST 明确不可用，不得用 Demo 或过期路线冒充实时结果。

#### Scenario: 返回 Demo 天气
- **GIVEN** 真实天气 Provider 不可用且版本化 Demo 数据可用
- **WHEN** 系统查询天气建议
- **THEN** 返回结果标识 Demo 来源和有效时间
- **AND** `degraded=true` 且不声称为实时天气

#### Scenario: 过期动态数据
- **GIVEN** 动态结果已超过 `expiresAt`
- **WHEN** 系统读取该结果
- **THEN** 结果标记 `isExpired=true` 并只作只读参考
- **AND** 系统不将其作为新的当前路线、天气或提醒事实

### Requirement: 真实出行 Provider 必须在受控配置下启用

系统 SHALL 仅在已确认服务商授权、Key、配额、超时和允许域名后启用真实天气、路线或静态地图 Provider。Key MUST 仅从部署环境读取，不得进入仓库、日志、异常、快照、缓存或测试夹具。真实 Provider 异常时，天气 MUST 按既有缓存和 Demo 顺序降级；路线和静态地图 MUST 返回不可用，不得缓存精确起点或伪造路线。

#### Scenario: 未配置真实 Provider
- **GIVEN** Provider 开关关闭或部署环境没有对应 Key
- **WHEN** 系统查询天气、路线或静态地图
- **THEN** 天气按 Demo 或不可用规则返回，路线和静态地图返回明确不可用
- **AND** 响应不得把 Demo 或空结果标记为实时事实

#### Scenario: 真实 Provider 超时
- **GIVEN** 已启用真实 Provider 且调用超过配置的读取超时
- **WHEN** 系统处理本次查询
- **THEN** 系统不向用户暴露服务商异常原文或 Key
- **AND** 系统按该能力的既定降级规则返回来源、时效和降级标识

### Requirement: 路线和静态地图只在本次请求中使用精确位置

系统 SHALL 在用户选择一种方式后才调用对应的高德路线接口；系统 SHALL 以本次起点、影院终点和该路线折线生成静态地图，并以 `image/png` 或 `image/jpeg` 图片流从本站返回页面，响应 MUST 设置 `Cache-Control: no-store` 且不得以 302 跳转到高德地址。精确起点、路线折线、途经点和地图图片 MUST 不写入 MySQL、Redis、日志、画像、建议快照、URL 或 Agent 轨迹，且不持续定位、不自动重试。

#### Scenario: 用户选择骑行路线查看地图
- **GIVEN** 用户已获取三种路线摘要
- **WHEN** 用户选择骑行路线查看地图
- **THEN** 系统只为骑行路线请求静态地图并返回图片流
- **AND** 不向浏览器返回高德 URL 或 Web 服务 Key

#### Scenario: 路线服务失败
- **GIVEN** 用户已主动请求路线
- **WHEN** 任一种路线 Provider 或静态地图调用失败
- **THEN** 系统只标记对应方式或地图暂不可用
- **AND** 继续展示影院地址和通用交通建议，且不生成文字路线替代结果

### Requirement: 用户刷新建议必须受任务状态和频率限制

系统 SHALL 仅允许任务处于 `READY` 或 `NOTIFIED` 时刷新建议，且距上次刷新至少五分钟；任务已取消时 MUST 拒绝刷新。

#### Scenario: 高频刷新
- **GIVEN** 有效任务距上次刷新不足五分钟
- **WHEN** 用户请求刷新建议
- **THEN** 系统返回 `107001`
- **AND** 不调用外部 Provider 或覆盖现有快照

#### Scenario: 已取消任务刷新
- **GIVEN** 任务状态为 `CANCELLED`
- **WHEN** 用户请求刷新建议
- **THEN** 系统返回 `207002`
- **AND** 不生成新快照或提醒
