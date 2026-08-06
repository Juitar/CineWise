# Requirements: ticketing-canonical-business-id

## ADDED Requirements

### Requirement: 票务公开查询使用 canonical 业务 ID

系统 MUST 要求 `movieId`、`cinemaId` 和 `showId` 使用无前导零的正十进制字符串 `^[1-9]\\d*$`，且值处于 Java `long` 正数范围。

#### Scenario: canonical ID 查询成功

- **当** 调用方传入例如 `70001` 的 canonical 场次 ID
- **则** 座位图接口按该 ID 查询权威场次与座位数据
- **并且** 响应 ID 继续使用十进制字符串

#### Scenario: 非 canonical ID 被拒绝

- **当** 调用方向场次、日期、可售影片或座位图接口传入 `01`、`0`、`show-70001`、带符号值或超出 long 范围的 ID
- **则** 系统返回 HTTP 400 与既有错误码 `100001`
- **并且** 系统不得调用领域查询或座位服务
