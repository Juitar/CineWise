# 真实排期候选接入

## 背景

现有 NetStart Provider 已能同步影片和影院基础资料，但不提供真实排期候选。A 的本地票务系统负责 `movie_show`、影厅、座位、价格、余座、锁座、订单、支付、电子票和退款；这些本地交易数据不能由 D 的内容 Provider 直接写入或覆盖。

已确认采用“D 获取并标准化外部排期候选，A 导入并管理本地票务事实”的方案。外部排期只能说明某影片在某影院可能有某个放映时间，不能当作实时余座、可交易价格或下单承诺。

## 本次范围

- D 在现有 NetStart 基础上增加排期候选 Provider：外部调用、输入校验、限流、超时、一次短重试、字段标准化、质量隔离和脱敏审计。
- D 以稳定的外部影片、影院、场次 ID 关联既有 `content_identity_mapping`，形成带本地 `movieId/cinemaId` 的外部排期快照。
- D 提供公开的 Application DTO/Port，返回 `source`、`dataAt`、`expiresAt` 和降级状态；A 只能通过该 Port 读取。
- D 保存排期快照并在 Provider 不可用时返回未过期快照；快照过期后不再作为可导入候选。
- 由 A 在自己的 Application Service 校验、隔离并导入候选，创建本地沙箱场次、影厅、座位和本地价格；A 的 `SaleableShowBatchQueryService` 继续只返回本地可售结果。

## 不在本次范围

- D 不访问或写入 A 的 Mapper、Repository、Entity、`movie_show`、影厅、座位、价格、订单、支付或退款表。
- 不把外部余座、票价、座位图或支付状态作为本地交易事实；不增加选座、建单、支付、退票或对外购票 REST 接口。
- “按 movieId 查询可售影院”继续由 A 的 `movie-available-cinemas-purchase-entry` change 负责，不混入本 change。
- 不宣称 NetStart 为官方合作、商业授权、实时票务数据或具备 SLA；生产/商业环境仍不得启用该学习用途 Provider。

## Owner 与验收

- D：NetStart 排期 Provider、外部字段适配、限流/超时/重试、快照与降级、城市到本地影院的映射、外部影片/影院/场次身份映射，以及公开候选快照 Port。
- A：本地票务事实、候选导入 Application Service、沙箱场次/影厅/座位/本地价格、交易校验，以及本地可售查询。
- C：仅在 A 提供正式购票入口接口后接入页面；本 change 不要求 C 增加临时 Mock。

验收以 D 的 Provider 定向测试、快照/降级/身份隔离测试、A 对公开 Port 的导入测试及双方受控 MySQL 验证为准；尚未取得的 Provider 脱敏样例、稳定外部场次 ID、价格语义和刷新频率不得假定为已完成。
