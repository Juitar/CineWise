## ADDED Requirements

### Requirement: 个人订单查询返回权威场次上下文

系统 SHALL 在 `GET /api/v1/orders` 和 `GET /api/v1/orders/{orderNo}` 的订单记录中返回 `movieId`、`cinemaId` 和 `showStartTime`。ID SHALL 为十进制字符串，时间 SHALL 为带业务时区偏移的 ISO 8601 字符串。字段 SHALL 来自订单 `showId` 所引用的 A 权威 `movie_show` 记录，不得由前端猜测或由 A 复制 D 的内容事实。

#### Scenario: 本人订单列表返回场次上下文

- **GIVEN** 当前认证用户存在引用有效场次的历史订单
- **WHEN** 用户调用 `GET /api/v1/orders`
- **THEN** 每条记录返回与对应 `movie_show` 一致的 `movieId`、`cinemaId` 和 `showStartTime`
- **AND** 列表保持原分页、筛选、倒序和座位批量加载规则

#### Scenario: 本人订单详情返回场次上下文

- **GIVEN** 当前认证用户拥有指定订单号
- **WHEN** 用户调用 `GET /api/v1/orders/{orderNo}`
- **THEN** 响应返回该订单的权威状态、座位快照及场次上下文

#### Scenario: 订单归属和空结果语义不变

- **GIVEN** 订单属于其他用户或筛选条件没有命中本人订单
- **WHEN** 用户查询详情或列表
- **THEN** 跨用户详情与不存在仍统一返回 404
- **AND** 空列表返回空分页，不生成任何影片、影院或场次字段

### Requirement: 场次上下文查询不得扩大交易锁边界

系统 SHALL 仅在个人订单只读投影中关联 `movie_show`。建单、取消、支付和退款所使用的 `FOR UPDATE`、条件更新和幂等恢复查询 SHALL 保持原交易边界，不得因展示字段加入场次表锁定。

#### Scenario: 交易查询不连接展示投影

- **GIVEN** 建单、取消、支付或退款正在读取或锁定订单权威状态
- **WHEN** 交易服务执行原有持久化操作
- **THEN** 交易查询仍只使用原订单交易投影
- **AND** 场次上下文只由个人订单列表与详情的只读查询返回

### Requirement: 响应增量不复制内容事实

系统 SHALL 只增加 `movieId`、`cinemaId` 和 `showStartTime`，不得在本变更中返回或持久化影片标题、海报、影院名称、地址或其他 D 拥有的内容字段。

#### Scenario: 前端补充展示内容

- **GIVEN** 前端取得个人订单的场次上下文
- **WHEN** 页面需要展示影片标题、海报或影院名称
- **THEN** 前端使用 D 的公开内容 API 查询
- **AND** A 的订单接口不生成第二份内容数据
