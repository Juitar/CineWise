## ADDED Requirements

### Requirement: 真实影院目录驱动本地演示排期

系统 SHALL 通过 `ContentPurchaseQueryPort.findLiveDemoPurchaseCatalog` 获取真实影院和影片引用，并仅在目录未过期时为全部返回影院生成 `demo-seed` 本地演示排期。

#### Scenario: 全部真实影院生成排期

- **GIVEN** D 返回至少一部未过期影片和多家真实影院
- **WHEN** 每日 03:10 演示排期任务执行
- **THEN** A 为每家真实影院生成可查询的 Mock 影厅、当天和次日每天 2 场、每场 40 座的排期和价格
- **AND** 不读取 D 的持久化实现

#### Scenario: 真实沙箱优先

- **GIVEN** 同一影院同一业务日期同时存在 `external-sandbox` 和 `demo-seed`
- **WHEN** 查询可售场次
- **THEN** 只展示 `external-sandbox`，不删除或覆盖任一来源的交易数据

#### Scenario: 目录为空或过期

- **GIVEN** D 返回空目录，或 `expiresAt` 不晚于当前业务时间
- **WHEN** 种子初始化执行
- **THEN** A 不新增真实影院 Mock 排期，并保留已有数据

#### Scenario: 原始内容过期早于演示引用窗口

- **GIVEN** D 已确认 A 可将 `dataAt + 24h` 作为本地演示排期引用窗口，且内容表的原始 `expiresAt` 更早
- **WHEN** A 查询真实目录以生成本地演示排期
- **THEN** A 可在该 24 小时窗口内引用目录
- **AND** A 不修改 D 面向影片、影院页面的原始内容过期语义

#### Scenario: 重复任务

- **GIVEN** 已存在场次或座位且部分座位已锁定/售出
- **WHEN** 每日任务再次执行
- **THEN** 不创建重复业务键，不改变已有座位交易状态

#### Scenario: 清理无交易关联的过期演示场次

- **GIVEN** `demo-seed` 场次已结束，且不存在订单，所有座位均为未锁定的 AVAILABLE
- **WHEN** 每日演示排期任务执行
- **THEN** 系统可以分批删除该场次及其可再生座位图
- **AND** 不删除影厅、影片、影院、订单、支付、电子票或退款记录

#### Scenario: 清理期间发生锁座

- **GIVEN** 清理任务已读取一个过期 `demo-seed` 场次候选
- **AND** 另一事务正在对该场次的座位加锁并创建订单
- **WHEN** 清理任务取得场次和座位锁后复核交易引用
- **THEN** 系统不得删除该场次或其座位图
- **AND** 返回值只统计实际删除成功的场次
