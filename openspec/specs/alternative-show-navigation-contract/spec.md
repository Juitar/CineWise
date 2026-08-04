# alternative-show-navigation-contract Specification

## Purpose
定义替代场次查询返回影片 ID 的兼容契约，使用户可从替代场次恢复到正确的选座上下文。
## Requirements
### Requirement: 替代场次必须返回可恢复选座上下文的影片 ID

系统 SHALL 在本人订单替代场次响应的每个候选中返回 `showId`、`movieId` 和 `cinemaId`。三个业务 ID MUST 为十进制字符串；`movieId` MUST 来自订单原场次的权威影片 ID，并与候选查询使用的同影片条件一致。系统不得由影片标题、页面状态或 D 的内容数据反推影片 ID。

#### Scenario: 返回同影片替代场次

- **GIVEN** 当前用户拥有订单且原场次影片 ID 有效
- **WHEN** 用户调用 `GET /api/v1/orders/{orderNo}/alternative-shows`
- **THEN** 每个候选包含字符串 `showId`、`movieId` 和 `cinemaId`
- **AND** 每个候选的 `movieId` 等于原场次影片 ID
- **AND** 页面可使用三个 ID 进入正常选座流程，并在刷新后重新查询权威场次和座位状态

#### Scenario: 没有替代场次

- **GIVEN** 有效日期窗口内没有同影片可售候选
- **WHEN** 用户查询替代场次
- **THEN** 系统返回空 `shows`
- **AND** 不伪造 `movieId`、场次或余座数据

### Requirement: 新字段必须保持现有替代场次契约兼容

系统 SHALL 仅向每个候选追加 `movieId`，不得改变接口路径、请求参数、鉴权、原字段含义、稳定排序或空结果语义。影片标题、海报和影院内容字段 MUST 继续由 D 的公开内容能力提供，不得加入 A 的票务响应。

#### Scenario: 旧消费者忽略新增字段

- **GIVEN** 消费者只读取既有替代场次字段
- **WHEN** 服务端返回新增 `movieId` 的响应
- **THEN** 既有字段和值保持兼容
- **AND** 订单归属、日期范围和可售过滤规则不变
