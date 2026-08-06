## ADDED Requirements

### Requirement: 建单成功提供权威订单出口

系统 SHALL 在建单成功且收到服务端 `orderNo` 后显示进入支付页和订单详情页的操作入口。

#### Scenario: 用户从建单成功页进入支付

- **GIVEN** 建单请求返回待支付订单及非空 `orderNo`
- **WHEN** 用户点击“去支付”
- **THEN** 页面跳转到使用该 `orderNo` 编码后的支付路径
- **AND** 不发送新的建单或支付请求

#### Scenario: 用户从建单成功页查看订单

- **GIVEN** 建单请求返回订单
- **WHEN** 用户点击“查看订单”
- **THEN** 页面跳转到该订单的详情路径

### Requirement: 支付成功提供条件化电子票入口

系统 SHALL 仅在支付结果成功且服务端返回非空 `ticketId` 时显示“查看电子票”。

#### Scenario: 支付成功且电子票已生成

- **GIVEN** 支付查询返回 `paymentStatus=SUCCESS` 与非空 `ticketId`
- **WHEN** 用户点击“查看电子票”
- **THEN** 页面跳转到使用该 `ticketId` 编码后的电子票路径

#### Scenario: 结果未知或票号缺失时不提供电子票入口

- **GIVEN** 支付结果为未知、非成功状态，或成功结果尚未返回 `ticketId`
- **WHEN** 页面渲染支付结果
- **THEN** 不显示“查看电子票”
- **AND** 不生成、猜测或缓存 ticketId，也不重发支付 POST
