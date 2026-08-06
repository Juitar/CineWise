## ADDED Requirements

### Requirement: 支付期限必须来自权威订单时间

系统 SHALL 使用订单查询返回的 `expireTime` 生成支付期限和剩余时间展示，SHALL NOT 使用固定十五分钟或页面进入时间伪造支付期限。浏览器倒计时仅用于展示，订单状态仍以服务端为准。

#### Scenario: 展示待支付订单剩余时间

- **GIVEN** 待支付订单返回合法的 `expireTime`
- **WHEN** 用户打开支付页面
- **THEN** 页面展示由该截止时间计算的剩余时间并按秒更新
- **AND** 不重新生成支付期限或改变订单状态

#### Scenario: 本地时间到达支付截止时间

- **GIVEN** 本地展示时间已达到或超过订单 `expireTime`
- **WHEN** 支付页面继续显示该订单
- **THEN** 页面提示用户刷新订单状态
- **AND** 不在浏览器内把订单改成 `EXPIRED`，不自动发起支付、取消或查询以外的写请求

#### Scenario: 支付期限缺失或非法

- **GIVEN** 订单截止时间缺失或不能按业务时区解析
- **WHEN** 页面渲染支付期限
- **THEN** 显示“支付期限以订单信息为准”
- **AND** 不回退到固定十五分钟

### Requirement: 交易展示使用统一状态、时间和座位语义

系统 SHALL 在 `modules/order` 集中维护订单、支付、电子票和退款状态文案，并按 `Asia/Shanghai` 格式化交易时间。仅有 `seatIds` 时 SHALL 标注为“座位编号”，不得伪造物理排号和座号。

#### Scenario: 订单详情展示交易时间与座位编号

- **GIVEN** 订单查询返回 `seatIds`、`expireTime` 和 `updatedAt`
- **WHEN** 用户查看订单详情
- **THEN** 截止时间和更新时间按统一业务时区展示
- **AND** `seatIds` 以“座位编号”呈现，不显示为排号座位标签

#### Scenario: 电子票展示座位编号

- **GIVEN** 电子票响应只包含 `seatIds`
- **WHEN** 用户查看电子票
- **THEN** 页面按“座位编号”展示这些十进制字符串
- **AND** 不根据 ID 推断排、列或座位名称
