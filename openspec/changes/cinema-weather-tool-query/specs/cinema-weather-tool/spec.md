## ADDED Requirements

### Requirement: 按已登记影院查询天气

系统 MUST 仅接受 `cinemaId` 查询天气。系统读取该影院的坐标，经行政区转换得到高德 `adcode` 后查询天气；不得接收模型传入的地址、坐标、区域名或 userId。

#### Scenario: 已登记影院有坐标

- **当** Agent 调用 `getWeather(cinemaId)`
- **那么** 系统返回该影院行政区的天气及来源、有效期和降级标识。

#### Scenario: 影院不存在或坐标缺失

- **当** `cinemaId` 不存在或没有完整坐标
- **那么** 工具返回参数或数据不可用错误，不调用高德。

### Requirement: 只读 Agent 工具

`getWeather` MUST 登记为只读工具，执行期间不得创建任务、刷新快照、写入 Agent 数据、发送 SSE 或发起确认动作。

#### Scenario: 执行天气查询

- **WHEN** Agent 调用已登记的 `getWeather(cinemaId)`
- **THEN** 工具只返回天气查询结果，不创建或修改任务、快照、Agent 数据和 SSE 事件
