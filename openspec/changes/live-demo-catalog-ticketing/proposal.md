# 真实影院本地演示排期兜底

## 背景

D 已通过 `ContentPurchaseQueryPort.findLiveDemoPurchaseCatalog` 提供未过期的真实影片和影院引用。A 当前仍只为固定演示影院生成排期，导致其他真实影院没有可浏览的本地票务数据。

## 范围

- A 每日 03:10 通过 D 的公开目录 API 生成真实影院的本地演示排期；启动时只补齐固定 Demo。
- 为目录中的全部真实影院生成 A 管理的 `demo-seed` 影厅、场次、座位和价格。
- 真实 `external-sandbox` 场次按影院和业务日期优先，隐藏同日 `demo-seed`。
- D 已于 2026-08-09 确认：为 A 的本地演示排期引用，NetStart 每日内容同步的 `dataAt + 24h` 可以覆盖内容表中更早的原始 `expiresAt`；影片、影院页面仍按 D 的原始 `expiresAt` 展示。目录在该 24 小时引用窗口结束后不新增 Mock 排期，保留已有交易事实。
- 每家影院只生成 1 个影厅、当天和次日每天 2 场、每场 40 座。
- 清理已结束且无订单、无锁座的 `demo-seed` 场次，不删除任何交易关联数据。

## 非范围

- 不访问 D 的表、Mapper、Repository、缓存、Provider 或 Controller。
- 不把外部余座、价格或影厅当作交易事实。
- 不新增数据库表、Flyway 迁移或真实座位库存。

## Owner 与验收

- Owner：A；依赖 D 已合入的公开 Application API。
- 验收：全部真实影院可在每日任务中得到小规模本地 Mock 排期；真实沙箱同日优先；重复执行不覆盖交易状态；目录过期后不新增排期。
