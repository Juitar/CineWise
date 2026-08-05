## ADDED Requirements

### Requirement: 个人订单页面消费权威场次上下文

系统 SHALL 使用个人订单 GET 接口返回的 `movieId`、`cinemaId` 和 `showStartTime` 展示场次上下文。建单、取消和恢复等写接口 SHALL 保持原 `OrderResponse` 契约。

#### Scenario: 列表、详情与支付页展示开场时间

- **GIVEN** 服务端订单查询返回场次上下文
- **WHEN** 用户查看订单列表、订单详情或支付页
- **THEN** 页面按 `Asia/Shanghai` 格式化并展示 `showStartTime`
- **AND** 筛选文案为“下单日期”，不得把订单创建时间描述为开场日期

#### Scenario: 电子票按订单号补充场次上下文

- **GIVEN** 用户打开电子票页面且票据返回 `orderNo`
- **WHEN** 页面读取订单详情
- **THEN** 页面仅使用该 `orderNo` 查询订单并展示返回的开场时间
- **AND** 页面不得从 `ticketId`、`showId` 或本地缓存推断影片、影院或开场时间

#### Scenario: 内容事实不可用时安全降级

- **GIVEN** D 的公开内容接口尚未提供影片和影院展示数据
- **WHEN** 页面渲染订单或电子票
- **THEN** 页面显示“影片信息暂不可用”
- **AND** 不得把场次编号伪造成影片标题

### Requirement: 替代场次回流使用权威业务 ID

系统 SHALL 仅使用替代场次响应中的 `showId`、`movieId`、`cinemaId` 构造选座路由，并对每项 ID 进行 URL 编码。

#### Scenario: 用户选择替代场次

- **GIVEN** 替代场次响应包含三个业务 ID
- **WHEN** 用户选择该场次
- **THEN** 系统跳转到对应的选座路径
- **AND** 不得从标题、订单缓存或 URL 旧值推断 ID
