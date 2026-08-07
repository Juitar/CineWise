# 真实影院本地演示排期兜底

## 背景

D 已通过 `ContentPurchaseQueryPort.findLiveDemoPurchaseCatalog` 提供未过期的真实影片和影院引用。A 当前仍只为固定演示影院生成排期，导致其他真实影院没有可浏览的本地票务数据。

## 范围

- A 在开发/演示种子初始化时消费 D 的公开目录 API。
- 为目录中的全部真实影院生成 A 管理的 `demo-seed` 影厅、场次、座位和价格。
- 真实 `external-sandbox` 场次按影院和业务日期优先，隐藏同日 `demo-seed`。
- 目录为空或已过期时不新增 Mock 排期，保留已有交易事实。

## 非范围

- 不访问 D 的表、Mapper、Repository、缓存、Provider 或 Controller。
- 不把外部余座、价格或影厅当作交易事实。
- 不新增数据库表、Flyway 迁移、定时导入或真实座位库存。

## Owner 与验收

- Owner：A；依赖 D 已合入的公开 Application API。
- 验收：全部真实影院可得到本地 Mock 排期；真实沙箱同日优先；重复初始化不覆盖交易状态；目录过期后不新增排期。
