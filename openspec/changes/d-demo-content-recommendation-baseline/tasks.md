## 1. 文档确认

### D 对 A 的确认记录（2026-08-02）

- D 同意 A 维护 `movie/cinema` 迁移顺序和共享固定 Mock 种子；D 保留内容业务所有权。
- D 已确认 T09 `movie`、T10 `cinema` 的字段清单；固定 Mock 数据的 `source_movie_id`、`source_cinema_id` 必须非空。
- D 同意来源 ID 保持可空，但来源 ID 为空的记录不得依赖 `source + source_*_id` 去重或幂等更新；真实 Provider 接入前另行确定身份识别规则。
- D 确认提供 `ContentSummaryQueryPort`；Port 未完成前，A 可使用经 D 确认、明确标识 `MOCK/demo-seed` 的临时 Demo Adapter，且不得维护第二套内容数据或生成票务事实。
- D 确认 A 不访问 D 的 Entity、Mapper、Repository，也不实现真实外部内容 Provider。

### Demo 数据维护交接（2026-08-03）

- A 已说明此前 10 部影片、4 家影院仅为场次演示临时数据；D 自本变更起接管该清单并维护唯一的 `demo-content-v1`。
- A 保留 `DemoSeedInitializer` 的编排和票务种子；影片、影院清单及内容种子规则由 D 维护，A 继续只消费 `ContentSeedCatalog` 返回的实际数据库 ID。

- [x] 1.1 D 已逐项确认 proposal、两份 spec 和 design 覆盖 Demo 内容、快照缓存、内部查询、固定候选和回归数据；确认日期：2026-08-02。
- [x] 1.2 A 已确认 D 不创建 `movie_show`、票价、座位和库存，并确认共享固定数据、`ContentSummaryQueryPort` 和场次公开 Application 查询的协作方式；确认日期：2026-08-02。
- [x] 1.3 B 已确认推荐工具名称、类型化 Command 和 `ToolResult<T>` 结果字段；确认结果已作为后续工具适配器输入；确认日期：2026-08-02。
- [x] 1.4 C 已确认前端展示所需的 `source/dataTime/expiresAt/isExpired/degraded/fallbackType` 字段和当前 REST 协作范围；确认日期：2026-08-02。
- [x] 1.5 D 已确认本阶段文档通过，允许进入迁移阶段；确认日期：2026-08-02。

## 2. 数据库迁移与固定数据

- [x] 2.1 D 已对照总后端设计复核 T09 `movie`、T10 `cinema`、T11 `external_data_snapshot`、T12 `data_sync_log` 的字段、可空性、唯一约束、索引和清理规则；确认日期：2026-08-02。
- [x] 2.2 D 已审核 V001 `movie/cinema` 迁移的字段、索引和约束；审核日期：2026-08-02；迁移提交：`0e7f158`。
- [x] 2.3 D 已向 A 提交 T11 `external_data_snapshot`、T12 `data_sync_log` 的迁移申请材料；A 分配版本 `V004`，文件为 `backend/src/main/resources/db/migration/V004__create_content_snapshot_and_sync_log_tables.sql`。D 已按两轮复审补齐时间顺序、状态统计、清理索引和生命周期规则。
- [x] 2.4 A 已于 2026-08-02 复核雪花 ID、`DATETIME(3)`、跨模块逻辑关联和迁移顺序，完成 V004 最终静态审查并授权专用空 MySQL 8.4 验证。
- [x] 2.5 A 已于 2026-08-02 在 MySQL 8.4.11 专用空库执行 V001 至 V004；首次 migrate、重复 migrate、Flyway 历史、字符集、排序规则、唯一键、索引和 16 个 CHECK 正反用例全部通过。证据见 `docs/V004_MIGRATION_VALIDATION_2026-08-02.md`。
- [x] 2.6 D 与 A 已确认唯一 `demo-content-v1` 的数据维护边界：沿用 A 演示阶段的 10 部影片、4 家影院，来源 ID 清单见 `demo-content-v1-manifest.md`。A 保留 `DemoSeedInitializer` 的初始化编排和票务种子，D 维护影片、影院清单及内容种子规则；确认日期：2026-08-03。
- [x] 2.7 D 已将唯一的 `demo-content-v1` 落为 classpath JSON，目录只保存来源 ID 和内容字段；`ContentSeedApplicationService` 首次写入使用雪花 ID，重复初始化按来源 ID 查回既有记录并经 `ContentSeedCatalog` 交给 A 的票务种子。验证：`backend\mvnw.cmd verify` 通过；固定时钟下两次初始化保持 10 部影片、4 家影院、8 个影厅、168 个场次、13440 个座位，已锁座位状态不变，日期：2026-08-03。
- [x] 2.8 A 已于 2026-08-02 完成共享 `cinewise` 的历史、checksum、备份和兼容性检查，并使用独立 `cinewise_migrator` 执行 V004；首次应用 1 个迁移，重复执行应用 0 个迁移，两张空表、索引、6 个 CHECK 和排序规则检查通过，证据见 `docs/V004_SHARED_MIGRATION_2026-08-02.md`。应用环境的 `FLYWAY_ENABLED` 保持 `false`。
- [ ] 2.9 D 使用日常应用账号完成依赖 V004 的持久层和真实 MySQL 集成测试；验证方式：覆盖两张表的正常写入、唯一键和 CHECK 拒绝场景，不执行 Flyway，不使用迁移账号。

## 3. Demo 内容、快照和缓存实现

- [ ] 3.1 D 在 `content` 模块定义标准化影片、影院、来源封套、查询条件以及 Provider、快照、缓存端口；验证方式：Application 和 Domain 不依赖 Web、MyBatis 或 Redis 类型。
- [ ] 3.2 D 实现版本化 Demo 内容资源读取和 `DemoContentProvider`，统一通过业务 `Clock` 生成时间；验证方式：相同版本、条件和固定时钟返回相同内容与顺序。
- [ ] 3.3 D 实现 `movie`、`cinema`、`external_data_snapshot`、`data_sync_log` 的持久化适配，不访问其他模块 Mapper；验证方式：持久化集成测试覆盖唯一键和重复初始化。
- [ ] 3.4 D 实现 Redis 内容缓存，使用 `ext:content:{city}:{resource}:{idOrHash}` 键和配置化 TTL；验证方式：缓存只保存标准 DTO，Redis 失败可继续回退。
- [ ] 3.5 D 实现影片和影院内部只读 Application 用例及公开 `ContentSummaryQueryPort`，按“有效缓存、有效快照、允许的过期快照、Demo、`303004`”处理；验证方式：每种结果均返回正确来源、时间和降级字段，A 只能通过 Port 查询内容摘要。
- [ ] 3.6 D 确保过期内容仅用于只读展示且不进入新的可购推荐；验证方式：过期内容用例返回 `isExpired=true` 并被推荐资格校验排除。

## 4. 第一版固定推荐候选

- [ ] 4.1 D 建立 `fixed-rec-v1` 固定候选数据和类型化 Application DTO；验证方式：相同输入和固定时钟返回相同候选与顺序。
- [ ] 4.2 D 实现固定候选查询，校验候选完整性、过期时间、两位小数价格和来源；验证方式：缺字段或过期候选不生成可购方案。
- [ ] 4.3 D 在 A 的公开场次查询不可用或无可购结果时返回 `purchaseEligible=false` 和 `missingFactors=[SHOWTIME]`，不生成 `PLAN_CARD`；验证方式：结果中不存在 D 自行生成的 `showId`、价格和库存。
- [ ] 4.4 A 的公开场次 Application 查询可用后，D 只引用返回的 `showId/movieId/cinemaId/price/startTime/expiresAt`；验证方式：固定候选与 A 的共享 ID 和事实字段一致，不维护第二份场次数据且不调用本应用 Controller。
- [ ] 4.5 D 按 B 确认的工具名称和 Command 实现工具适配器，返回公共 `ToolResult<T>`；验证方式：工具不追问、不发布 SSE、不调用模型，也不访问 Mapper。

## 5. 测试数据与回归用例

- [ ] 5.1 D 建立固定时钟、`demo-content-v1`、`fixed-rec-v1` 和期望结果夹具；验证方式：业务初始化和测试引用同一数据版本。
- [ ] 5.2 D 编写内容单元测试，覆盖固定影片、固定影院、输入校验、来源封套和稳定排序；验证方式：相关测试全部通过。
- [ ] 5.3 D 编写缓存和快照测试，覆盖缓存命中、缓存不可用、有效快照、允许的过期快照和全部不可用；验证方式：回退顺序、`303004` 和降级标识符合规格。
- [ ] 5.4 D 编写固定推荐测试，覆盖可复现顺序、不完整候选、过期候选、A 场次查询不可用和引用 A 公开查询结果；验证方式：不存在虚构可购事实。
- [ ] 5.5 D 建立第一版回归用例清单，包含 `caseId/module/priority/preconditions/input/mockProfile/expectedTool/expectedBusinessRefs/expectedResult/forbiddenResult/dataSourceExpectation/timeoutMs/owner`；验证方式：字段完整且不保存敏感输入。
- [ ] 5.6 D 在 A 已完成迁移验证的 MySQL 8 和 Redis 环境执行内容查询、缓存降级与推荐集成测试；验证方式：记录环境、命令、通过数、失败数和缺陷编号，不在此任务中执行 Flyway 结构迁移。

## 6. 验收与归档

- [ ] 6.1 D 执行 `backend\mvnw.cmd verify`；验证方式：编译、单元测试、架构检查、Checkstyle、SpotBugs 和 JaCoCo 均通过，失败项有负责人和复现步骤。
- [ ] 6.2 A、B、C 分别复核场次边界、工具结果和展示字段；验证方式：所有影响当前范围的问题均有结论。
- [ ] 6.3 D 核对实现、配置、固定数据、用例和设计文档一致，并记录关联提交和验证结果；验证方式：无未说明的行为差异。
- [ ] 6.4 D 执行 `openspec validate d-demo-content-recommendation-baseline --strict`；验证方式：严格校验通过。
- [ ] 6.5 D 在全部任务和验收完成后归档变更；验证方式：主规格已同步，变更进入归档目录。
