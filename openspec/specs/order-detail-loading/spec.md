# order-detail-loading Specification

## Purpose
TBD - created by archiving change order-detail-loading-skeleton. Update Purpose after archive.
## Requirements
### Requirement: 订单详情首次资料加载使用连续骨架屏

订单详情 SHALL 在订单权威数据或首次影片/影院补充资料仍在查询期间显示骨架屏，并 SHALL NOT 显示内容加载提示或资料不可用兜底。

#### Scenario: 订单正在首次查询

- **WHEN** 用户进入订单详情且订单查询尚未完成
- **THEN** 页面显示带可访问名称的订单详情骨架屏

#### Scenario: 订单已返回但影片影院资料仍在查询

- **WHEN** 订单权威数据已返回且对应影片或影院首次补充查询尚未结束
- **THEN** 页面继续显示骨架屏，不显示“正在获取影片和影院信息”或资料不可用兜底

#### Scenario: 补充资料查询完成

- **WHEN** 影片和影院资料均已返回
- **THEN** 页面退出骨架屏并一次性展示真实订单详情

### Requirement: 内容失败不阻断订单详情

影片或影院补充查询明确失败后，订单详情 SHALL 展示订单权威字段和现有安全降级提示，并 SHALL 保留手动重试入口。

#### Scenario: 补充资料明确不可用

- **WHEN** 影片或影院补充查询结束且至少一项不可用
- **THEN** 页面退出骨架屏，展示资料不可用兜底及重试入口，订单交易状态和操作保持由订单数据决定

### Requirement: 双端使用既有 Skeleton 组件

订单详情骨架屏 SHALL 在 PC 使用 Ant Design Skeleton，在移动端使用 antd-mobile Skeleton，并共享同一业务加载状态。

#### Scenario: 不同宽度加载订单详情

- **WHEN** 页面在桌面或移动宽度进入加载状态
- **THEN** 对应端渲染既有组件库 Skeleton，且不显示 Spin 加载文案

