# 任务

## 1. 契约确认

- [x] 1.1 A 已确认本期定位为“外部真实开场参考 + A 本地沙箱交易”，不得冒充真实库存。（Owner: A；验证：proposal/design）
- [x] 1.2 D 已修复 `code=0` 响应包裹解析，并提供 `durationMinutes`、`auditoriumText` 与本地沙箱参考可用状态。（Owner: D；验证：PR #152、公开 DTO 与测试）
- [x] 1.3 A/D 已冻结候选状态、字段可空性、时效和截断语义。（Owner: A/D；验证：PR #152 与本 change design 复核）

## 2. A 迁移与导入实现

- [x] 2.1 A 实现不依赖 D DTO 的内部候选、准入校验和本地预计结束时间计算。（Owner: A；验证：固定 Clock 单元测试覆盖正常、过期、降级、无时长和已开场候选）
- [x] 2.2 A 审查并验证 V019 外部三元键到本地场次的幂等映射表。（Owner: A；验证：静态审查与空 MySQL 8.4 验证，记录：docs/database-migrations/V019_MIGRATION_VALIDATION_2026-08-07.md）
- [x] 2.3 A 在 D DTO 合入后实现显式适配、本地价格和影厅/座位创建。（Owner: A；验证：10 个候选、计划与本地写入单元测试）
- [x] 2.4 A 实现映射唯一约束、重复/并发导入恢复和历史交易场次保护。（Owner: A；验证：MySQL 8.4 两并发导入只有一条映射、一场次和 80 座位；既有映射路径零写入）
- [x] 2.5 A 保持现有场次查询、锁座、订单、支付、电子票和退票均使用本地票务事实。（Owner: A；验证：MySQL 场次查询回归与完整 `mvn verify`）

## 3. 联调与交付

- [x] 3.1 A 实现管理员手动导入入口：日期和最多 100 家影院、Spring Security 路径复用、应用层 ADMIN 复核、OpenAPI 与权限/参数测试；不实现定时或自动导入。（Owner: A；验证：Controller/Application 单元测试与后端 verify）
- [ ] 3.2 A/D 在授权的隔离环境验证候选读取、零写入跳过、本地沙箱创建和可售查询。（Owner: A/D；验证：运行记录与无残留检查）
- [ ] 3.3 同步前端管理页、展示文案、Mock/夹具与验收测试。（Owner: A；验证：后端 verify、前端 check、OpenSpec 严格校验）
