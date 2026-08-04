# Admin Order Read Model Spec

## ADDED Requirements

### Requirement: 仅ADMIN可以访问管理订单

系统 SHALL 提供`GET /api/v1/admin/orders`和`GET /api/v1/admin/orders/{orderNo}`，并在应用层根据`CurrentUserAccessor`复核当前角色。系统 SHALL NOT 信任请求参数中的用户或角色。

#### Scenario: ADMIN查询管理订单

- GIVEN 当前身份角色为ADMIN
- WHEN 查询管理订单列表或详情
- THEN 系统执行只读查询并返回管理订单视图

#### Scenario: 普通用户访问管理订单

- GIVEN 当前身份角色为USER
- WHEN 访问任一管理订单接口
- THEN 返回HTTP 403和`100403`
- AND 不执行管理订单Repository查询

### Requirement: 管理订单列表使用白名单筛选

系统 SHALL 支持`orderNo/userKeyword/status/movieId/showId/dateFrom/dateTo/page/size`筛选，状态 SHALL 来自`OrderStatus`枚举，日期 SHALL 使用Asia/Shanghai左闭右开区间，分页 SHALL 有上限，排序 SHALL 固定为创建时间和订单ID倒序。

#### Scenario: 组合筛选订单

- GIVEN 存在不同用户、影片、场次、状态和创建日期的订单
- WHEN ADMIN提交合法组合筛选
- THEN 只返回满足全部条件的订单
- AND 分页顺序稳定且不接受任意排序列

#### Scenario: 用户关键字无匹配

- GIVEN C公开用户摘要能力没有找到匹配用户
- WHEN ADMIN按该`userKeyword`查询
- THEN 返回HTTP 200和空分页
- AND 不执行无界订单查询

#### Scenario: 数字用户关键字

- GIVEN ADMIN提交1至19位ASCII数字用户关键字
- WHEN C公开用户查询端口解析该条件
- THEN 只按正数用户ID精确匹配且不匹配邮箱
- AND 0、超长或超出正BIGINT范围时返回空集合而不是HTTP 500

#### Scenario: 邮箱关键字过宽

- GIVEN 非数字关键字按邮箱包含匹配达到101个不同用户
- WHEN ADMIN查询管理订单
- THEN 返回HTTP 400和`201010`
- AND 不静默截断用户ID或执行订单查询

#### Scenario: 空白用户关键字

- GIVEN 请求明确携带但trim后为空的`userKeyword`
- WHEN ADMIN查询管理订单
- THEN 返回HTTP 400和`101001`
- AND 不调用C公开用户查询端口

#### Scenario: 非法筛选条件

- GIVEN 状态未知、业务ID非正数、日期倒置、跨度超限或分页越界
- WHEN ADMIN查询列表
- THEN 返回HTTP 400和`100001`

### Requirement: 用户筛选和展示只能使用C公开摘要

系统 SHALL 仅通过C确认的公开Application API解析`userKeyword`并批量获取`emailMasked`。A SHALL NOT 查询、联表或缓存`sys_user`，也 SHALL NOT 实现第二套邮箱脱敏规则。

#### Scenario: 返回脱敏用户摘要

- GIVEN 订单关联用户存在且C返回脱敏邮箱
- WHEN ADMIN查询列表或详情
- THEN 响应包含字符串`userId`和`emailMasked`
- AND 不包含完整邮箱、密码摘要、tokenVersion或登录日志

#### Scenario: 用户摘要服务不可用

- GIVEN C公开用户摘要查询暂不可用
- WHEN ADMIN查询管理订单
- THEN 返回HTTP 503和`301002`
- AND 不使用完整邮箱、空字符串或伪造摘要降级

#### Scenario: 历史订单用户不存在

- GIVEN 当前订单页包含C已无法找到的历史用户ID
- WHEN C批量用户摘要Map缺少该用户
- THEN 系统仍返回该订单且`emailMasked=null`
- AND 不删除订单或把整个查询误报为服务不可用

### Requirement: 列表与详情聚合交易只读摘要

系统 SHALL 聚合A拥有的订单、座位快照、支付、电子票、退款和场次引用。列表 SHALL 使用批量查询避免逐订单N+1，详情不存在时 SHALL 返回`205001`。

#### Scenario: 查询已支付订单详情

- GIVEN 订单存在唯一成功支付和有效电子票
- WHEN ADMIN按`orderNo`查询详情
- THEN 返回订单、场次引用、座位、支付和电子票摘要
- AND ID为字符串、金额为两位小数字符串、时间为ISO 8601

#### Scenario: 查询已退款订单详情

- GIVEN 订单已经退款且电子票失效
- WHEN ADMIN查询详情
- THEN 返回退款号、原因、退款状态、处理时间及最新订单和票状态
- AND 不返回`impact_snapshot`、`actionId`或幂等键

#### Scenario: 管理订单不存在

- GIVEN 不存在指定`orderNo`
- WHEN ADMIN查询详情
- THEN 返回HTTP 404和`205001`

### Requirement: 管理订单能力严格只读

系统 SHALL NOT 提供管理订单状态修改接口。管理列表和详情执行前后 SHALL 不改变订单、支付、电子票、退款、座位状态或版本。

#### Scenario: 查询不修改交易状态

- GIVEN 数据库存在待支付、已支付和已退款订单
- WHEN ADMIN重复查询列表和详情
- THEN 每次返回当前权威只读结果
- AND 所有交易记录的状态、版本和更新时间保持不变

#### Scenario: 敏感交易字段隔离

- GIVEN 订单记录包含客户端请求标识、幂等键、二维码载荷或退款影响快照
- WHEN ADMIN查询列表或详情
- THEN 响应和业务日志不包含这些敏感内部字段
