## ADDED Requirements

### Requirement: 真实影院目录驱动本地演示排期

系统 SHALL 通过 `ContentPurchaseQueryPort.findLiveDemoPurchaseCatalog` 获取真实影院和影片引用，并仅在目录未过期时为全部返回影院生成 `demo-seed` 本地演示排期。

#### Scenario: 全部真实影院生成排期

- **GIVEN** D 返回至少一部未过期影片和多家真实影院
- **WHEN** 开发/演示种子初始化执行
- **THEN** A 为每家真实影院生成可查询的 Mock 影厅、场次、座位和价格
- **AND** 不读取 D 的持久化实现

#### Scenario: 真实沙箱优先

- **GIVEN** 同一影院同一业务日期同时存在 `external-sandbox` 和 `demo-seed`
- **WHEN** 查询可售场次
- **THEN** 只展示 `external-sandbox`，不删除或覆盖任一来源的交易数据

#### Scenario: 目录为空或过期

- **GIVEN** D 返回空目录，或 `expiresAt` 不晚于当前业务时间
- **WHEN** 种子初始化执行
- **THEN** A 不新增真实影院 Mock 排期，并保留已有数据

#### Scenario: 重复初始化

- **GIVEN** 已存在场次或座位且部分座位已锁定/售出
- **WHEN** 初始化再次执行
- **THEN** 不创建重复业务键，不改变已有座位交易状态
