# Specification: 购票主线前端规范整改

## MODIFIED Requirements

### Requirement: 场次与选座展示

系统 SHALL 在场次、选座和订单确认页面按统一前端分层与响应式组件规则展示数据。

#### Scenario: 移动端使用移动组件
- **WHEN** viewport 小于 1024px 且场次或座位正在加载、失败或需要主要操作
- **THEN** 页面分别使用 antd-mobile 的加载、错误和按钮组件
- **AND** 不新增第二套请求层或路由守卫

#### Scenario: 选座导航保留权威 ID
- **WHEN** 用户确认已选座位
- **THEN** 使用服务端返回的 `showId/movieId/cinemaId` 和重复 `seatId` 参数导航
- **AND** 不从标题、数组位置或本地猜测业务 ID

### Requirement: 订单确认与恢复

系统 SHALL 通过 order module 管理订单确认、幂等和结果恢复。

#### Scenario: 页面只组合订单模块
- **WHEN** 订单确认页加载、提交或恢复建单
- **THEN** 页面调用模块 Hook 并渲染展示组件
- **AND** 幂等会话、金额规则和结果未知状态不在页面内重复实现

#### Scenario: 结果未知不重复建单
- **WHEN** 建单响应因网络、超时或 502/504 无法确认
- **THEN** 页面进入保护态，只能使用原 `clientRequestId` 查询
- **AND** 不重新发送 POST、不生成新幂等键

#### Scenario: 成功出口使用服务端结果
- **WHEN** 建单成功
- **THEN** 展示订单成功组件并通过 `orderNo` 提供去支付和查看订单出口
- **AND** 不显示过期的“下一迭代”提示

### Requirement: 格式与降级

系统 SHALL 使用统一 formatter 和金额工具处理购票页面的服务端时间与金额。

#### Scenario: 时间和金额安全展示
- **WHEN** 时间或金额缺失、非法或边界异常
- **THEN** 使用统一 formatter/金额工具的明确降级结果
- **AND** 不使用浮点比较、页面私有日期拼接或伪造数据
