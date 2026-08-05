## ADDED Requirements

### Requirement: 公开只读建单预检

系统 SHALL 提供 `CreateOrderTool.validate(CreateOrderPrecheckCommand)` 公开 Application API。该 API SHALL 只读取场次和座位权威状态，不创建订单、不锁座、不生成业务 ID、不返回金额、库存细节或订单数据。

#### Scenario: 当前选座仍可执行

- **GIVEN** `showId` 对应未来 `ON_SALE` 场次，且全部 seatIds 属于该场次并为 `AVAILABLE`
- **WHEN** B 调用 `validate`
- **THEN** 返回 `executable=true` 且 `errorCode=null`

#### Scenario: 场次或座位不可执行

- **GIVEN** 场次不存在、停售、已开场，或任一 seatId 不属于场次/当前不可锁定
- **WHEN** B 调用 `validate`
- **THEN** 返回 `executable=false` 和稳定错误码，不返回具体金额、库存、座位状态或订单信息

#### Scenario: 参数非法

- **GIVEN** showId/seatIds 为空、非正数、重复或座位数不在 1..6
- **WHEN** B 调用 `validate`
- **THEN** 返回 `executable=false` 和 `CommonErrorCode.INVALID_PARAMETER`（100001）

#### Scenario: 查询不可用

- **GIVEN** 票务只读查询依赖不可用
- **WHEN** B 调用 `validate`
- **THEN** 返回 `executable=false` 和 `TicketingErrorCode.QUERY_UNAVAILABLE`（306003），不得伪装为参数错误或可执行

### Requirement: 预检与写建单隔离

系统 SHALL 保持 `CreateOrderTool.execute` 的原有事务内复核不变；预检结果 SHALL 仅作为 B 的确认前提示，不能作为锁座或建单的授权凭证。

#### Scenario: 预检成功后状态发生变化

- **GIVEN** 预检返回可执行后其他请求先锁定座位
- **WHEN** B 随后调用写 Tool
- **THEN** 写 Tool 仍以事务内数据库条件更新为最终结果并可返回座位冲突，预检不得阻止或伪造写结果

#### Scenario: 预检不产生写副作用

- **GIVEN** B 对有效或无效选座调用预检
- **WHEN** 预检完成
- **THEN** `show_seat`、订单、订单座位和业务 ID 生成器均无写入或调用

### Requirement: 跨模块访问边界

B SHALL 只依赖 `CreateOrderTool.validate` 和 `CreateOrderPrecheckCommand/OrderPrecheckResult`，不得依赖 A 的 OrderApplicationService、Entity、Mapper、Repository 或 Controller。

#### Scenario: 适配器依赖公开入口

- **GIVEN** recommendation/agent 代码调用建单预检
- **WHEN** 编译和架构检查运行
- **THEN** 调用方向只指向 A 的公开 API，禁止依赖 ticketing/order persistence 和 web 包
