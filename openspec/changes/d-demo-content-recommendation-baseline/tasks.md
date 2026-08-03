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
- [x] 1.3 B 已确认工具类 `RankMoviePlanTool`、`ToolRegistry.targetName=rankMoviePlan`、`RankMoviePlanCommand(movieId, cinemaId, date, timeFrom?, timeTo?)` 和 `ToolResult<T>` 公共结果字段；时段成对传入且 `timeFrom < timeTo`，不传用户或票务事实字段；确认日期：2026-08-03。
- [x] 1.4 C 已确认前端展示所需的 `source/dataTime/expiresAt/isExpired/degraded/fallbackType` 字段和当前 REST 协作范围；确认日期：2026-08-02。
- [x] 1.5 D 已确认本阶段文档通过，允许进入迁移阶段；确认日期：2026-08-02。

## 2. 数据库迁移与固定数据

- [x] 2.1 D 已对照总后端设计复核 T09 `movie`、T10 `cinema`、T11 `external_data_snapshot`、T12 `data_sync_log` 的字段、可空性、唯一约束、索引和清理规则；确认日期：2026-08-02。
- [x] 2.2 D 已审核 V001 `movie/cinema` 迁移的字段、索引和约束；审核日期：2026-08-02；迁移提交：`0e7f158`。
- [x] 2.3 D 已向 A 提交 T11 `external_data_snapshot`、T12 `data_sync_log` 的迁移申请材料；A 分配版本 `V004`，文件为 `backend/src/main/resources/db/migration/V004__create_content_snapshot_and_sync_log_tables.sql`。D 已按两轮复审补齐时间顺序、状态统计、清理索引和生命周期规则。
- [x] 2.4 A 已于 2026-08-02 复核雪花 ID、`DATETIME(3)`、跨模块逻辑关联和迁移顺序，完成 V004 最终静态审查并授权专用空 MySQL 8.4 验证。
- [x] 2.5 A 已于 2026-08-02 在 MySQL 8.4.11 专用空库执行 V001 至 V004；首次 migrate、重复 migrate、Flyway 历史、字符集、排序规则、唯一键、索引和 16 个 CHECK 正反用例全部通过。证据见 `docs/V004_MIGRATION_VALIDATION_2026-08-02.md`。
- [x] 2.6 D 与 A 已确认唯一 `demo-content-v1` 的数据维护边界：沿用 A 演示阶段的 10 部影片、4 家影院，来源 ID 清单见 `demo-content-v1-manifest.md`。A 保留 `DemoSeedInitializer` 的初始化编排和票务种子，D 维护影片、影院清单及内容种子规则；确认日期：2026-08-03。
- [x] 2.7 D 已将唯一的 `demo-content-v1` 落为 classpath JSON，目录只保存来源 ID 和内容字段；数据库持久化业务键保持既有 `demo-seed`，首次写入使用雪花 ID，重复初始化按来源 ID 查回既有记录并经 `ContentSeedCatalog` 交给 A 的票务种子。验证：`backend\mvnw.cmd verify` 通过；固定时钟下已有 `demo-seed` 数据再次初始化后保持 10 部影片、4 家影院、8 个影厅、168 个场次、13440 个座位，已锁座位状态不变，日期：2026-08-03。
- [x] 2.8 A 已于 2026-08-02 完成共享 `cinewise` 的历史、checksum、备份和兼容性检查，并使用独立 `cinewise_migrator` 执行 V004；首次应用 1 个迁移，重复执行应用 0 个迁移，两张空表、索引、6 个 CHECK 和排序规则检查通过，证据见 `docs/V004_SHARED_MIGRATION_2026-08-02.md`。应用环境的 `FLYWAY_ENABLED` 保持 `false`。
- [ ] 2.9 D 使用日常应用账号完成依赖 V004 的持久层和真实 MySQL 集成测试；验证方式：覆盖两张表的正常写入、唯一键和 CHECK 拒绝场景，不执行 Flyway，不使用迁移账号。

## 3. Demo 内容、快照和缓存实现

- [x] 3.1 D 已在 `content` 模块定义标准化影片、影院、来源封套、查询条件以及 Provider、快照、缓存端口；Application 和 Domain 不依赖 Web、MyBatis 或 Redis 类型。验证：`backend\mvnw.cmd -Dtest=ModuleArchitectureTest test` 通过，日期：2026-08-03。
- [x] 3.2 D 已实现版本化 Demo 内容资源读取和 `DemoContentProvider`，统一通过业务 `Clock` 生成时间；验证：固定时钟、相同版本和条件下重复查询保持内容、JSON 顺序、`DEMO_CONTENT/MOCK` 来源与时间封套一致，日期：2026-08-03。
- [x] 3.3 D 已实现 `movie`、`cinema`、`external_data_snapshot`、`data_sync_log` 的 JDBC 持久化适配，不访问其他模块 Mapper；验证：H2 集成测试覆盖重复内容初始化、快照/同步日志唯一键及 V004 的时间、统计 CHECK 拒绝，日期：2026-08-03。真实 MySQL 日常账号验证仍由 2.9 单独完成。
- [x] 3.4 D 已实现 Redis 内容缓存，使用 `ext:content:{city}:{resource}:{idOrHash}` 键和配置化 TTL；缓存只保存标准 DTO，连接或序列化失败按未命中继续回退。验证：设置 `REDIS_INTEGRATION_ENABLED=true` 和本地 `REDIS_PASSWORD` 后，Docker Redis 真实读写测试通过；普通 Maven 校验不依赖外部 Redis，日期：2026-08-03。
- [x] 3.5 D 已实现影片和影院内部只读 Application 用例及公开 `ContentSummaryQueryPort`，按“有效缓存、有效快照、允许的过期快照、Demo、`303004`”处理；`CinemaSummary` 返回 `name/area/source/dataTime/expiresAt/isExpired`，A 只能通过 Port 查询内容摘要。验证：固定回退顺序、`303004`、公开摘要区域和时效字段测试通过，日期：2026-08-03。
- [x] 3.6 D 已确保过期快照仅用于只读展示且不进入新的可购推荐；验证：允许陈旧快照返回 `expired=true` 和 `fallbackType=SNAPSHOT`，超出最大陈旧期后继续回退，日期：2026-08-03。

## 4. 第一版固定推荐候选

- [x] 4.1 D 已建立 `fixed-rec-v1` 固定候选目录和类型化 Application DTO；固定时钟与相同输入下候选和顺序一致。验证：`FixedRecommendationQueryServiceTest` 通过，日期：2026-08-03。
- [x] 4.2 D 已实现可购候选完整性、两位小数价格、来源和过期校验；缺字段、价格格式非法或 `expiresAt <= now` 均不通过，不生成可购方案。验证：`PurchaseCandidateValidatorTest` 通过，日期：2026-08-03。
- [x] 4.3 D 已在未取得 A 场次事实时返回 `purchaseEligible=false`、`missingFactors=[SHOWTIME]` 的内容候选；结果不含 `showId`、价格、开场时间或库存，且不生成 `PLAN_CARD`。验证：`FixedRecommendationQueryServiceTest` 通过，日期：2026-08-03。
- [x] 4.4 A 的公开场次 Application 查询已提供 `showId/movieId/cinemaId/basePrice/startTime/expiresAt`；D 仅通过 `ShowQueryService` 调用并将 `basePrice` 映射为两位小数 `price`，不维护第二份场次数据或调用 Controller。验证：`TicketingShowtimeQueryAdapterTest`、`FixedRecommendationQueryServiceTest` 通过，日期：2026-08-03。
- [x] 4.5 D 已实现 `RankMoviePlanTool` 和 `RankMoviePlanCommand`，`targetName=rankMoviePlan`，返回公共 `ToolResult<T>`；工具只调用推荐 Application Service，不追问、不发布 SSE、不调用模型、不访问 Mapper。验证：`RankMoviePlanToolTest` 通过，日期：2026-08-03。

## 5. 测试数据与回归用例

- [x] 5.1 D 已使用唯一 classpath `demo-content-v1`、`fixed-rec-v1` 和固定业务时钟建立测试夹具；内容种子、Demo Provider 与测试均读取同一目录版本。验证：相关定向测试通过，日期：2026-08-03。
- [x] 5.2 D 已补齐内容单元测试，覆盖固定影片、固定影院、输入校验、来源封套和稳定排序。验证：`ContentQueryServiceTest`、`DemoContentProviderTest` 通过，日期：2026-08-03。
- [x] 5.3 D 已补齐缓存和快照测试，覆盖缓存命中、Redis 读取失败按未命中回退、有效快照、允许的过期快照、超过陈旧期回退 Demo 和全部不可用。验证：回退顺序、`303004` 和降级标识测试通过，日期：2026-08-03。
- [x] 5.4 D 已补齐固定推荐测试，覆盖可复现顺序、不完整候选、过期候选、A 场次查询不可用和引用 A 公开查询结果。验证：没有可购场次事实时不返回 `showId`、价格或可购卡片，日期：2026-08-03。
- [x] 5.5 D 已建立第一版回归用例清单 `regression-cases.md`，字段完整且未包含敏感输入。验证：覆盖内容来源、缓存/快照、过期、`303004`、固定推荐及 A 场次不可用，日期：2026-08-03。
- [ ] 5.6 D 在 A 已完成迁移验证的 MySQL 8 和 Redis 环境执行内容查询、缓存降级与推荐集成测试；验证方式：记录环境、命令、通过数、失败数和缺陷编号，不在此任务中执行 Flyway 结构迁移。

## 6. 验收与归档

- [ ] 6.1 D 执行 `backend\mvnw.cmd verify`；验证方式：编译、单元测试、架构检查、Checkstyle、SpotBugs 和 JaCoCo 均通过，失败项有负责人和复现步骤。
- [ ] 6.2 A、B、C 分别复核场次边界、工具结果和展示字段；验证方式：所有影响当前范围的问题均有结论。
- [ ] 6.3 D 核对实现、配置、固定数据、用例和设计文档一致，并记录关联提交和验证结果；验证方式：无未说明的行为差异。
- [ ] 6.4 D 执行 `openspec validate d-demo-content-recommendation-baseline --strict`；验证方式：严格校验通过。
- [ ] 6.5 D 在全部任务和验收完成后归档变更；验证方式：主规格已同步，变更进入归档目录。
