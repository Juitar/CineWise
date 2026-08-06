## Why

B 已将 `card + BUSINESS_INTENT + SELECT_SEATS` 合入 `dev`，A 已提供受登录保护的 `/shows/:showId/seats`。当前 Agent 工作区只能展示卡片，用户不能安全进入已确认场次的选座页。

## What Changes

- 对已通过 C 校验的 `SELECT_SEATS` 卡片显示“去选座”入口。
- 入口只使用无前导零的正十进制 `businessRef.showId`、`movieId` 和 `cinemaId` 构造 `/shows/{showId}/seats?movieId={movieId}&cinemaId={cinemaId}`，不猜测或补齐其他参数。
- 缺少完整外层计划字段或任一业务 ID 的卡片维持安全错误/占位，不显示入口。

## Non-Goals

- 不建单、支付、确认、锁座或提交座位。
- 不修改 A/B 后端、DTO、数据库或选座页业务逻辑。

## Impact

- Owner：C；复用 B 已合入的 SSE 卡片协议和 A 已有选座路由。
