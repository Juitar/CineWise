## ADDED Requirements

### Requirement: A 交易页面使用可预测的面包屑导航

系统 SHALL 在 A 负责的场次、选座、订单确认、订单、支付、电子票和退票页面使用语义化面包屑。上级项目 SHALL 指向规范业务路径，当前项目 SHALL 为不可点击文本；系统 MUST NOT 以浏览器历史作为面包屑导航实现。

#### Scenario: 用户从选座回到场次

- **GIVEN** 用户正在访问带有效 `movieId` 与 `cinemaId` 的选座页面
- **WHEN** 用户点击“选择场次”面包屑
- **THEN** 系统导航至 `/shows?movieId={movieId}&cinemaId={cinemaId}`
- **AND** 不重新发送建单、支付、取消或退款请求

#### Scenario: 用户从退款页返回订单详情

- **GIVEN** 用户正在访问 `/orders/{orderNo}/refund`
- **WHEN** 用户点击“订单详情”面包屑
- **THEN** 系统导航至 `/orders/{orderNo}`
- **AND** 当前“申请退票”项目不可点击

### Requirement: 交易页面不堆叠内容资料标签

系统 SHALL 将内容资料来源、时效、过期和降级的详细展示保留给影片、影院浏览页面。订单、支付、电子票和退票页面仅在内容资料不可用时显示紧凑降级提示与只读重试，且 SHALL 明确订单交易事实不受影响。

#### Scenario: 内容资料可用但含来源状态

- **GIVEN** 订单关联的影片和影院资料可读取
- **WHEN** 用户访问订单列表、订单详情或电子票
- **THEN** 页面不渲染逐条“内容资料来源与时效”标签

#### Scenario: 内容资料不可用

- **GIVEN** 订单关联的影片或影院资料请求失败
- **WHEN** 用户访问交易页面
- **THEN** 页面显示订单信息不受影响的紧凑提示和重试入口
