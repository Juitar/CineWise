## Context

当前仓库已完成公共 Spring Boot 骨架，`content` 和 `recommendation` 仅有分层目录，没有 D 的领域实现。详细设计要求内容、缓存和快照统一携带来源与时间；影片、影院归 D，场次、票价、座位和库存归 A。参见 [proposal.md](proposal.md) 和本变更的两份规格。

本变更跨越内容数据、推荐工具、缓存、数据库和测试数据，必须先把公开边界与数据归属写清楚。本文仅确定后续实现方案，不创建迁移或代码。

## Goals / Non-Goals

**Goals:**

- 建立稳定的 Demo 影片和影院数据源，保证离线开发和演示可用。
- 建立内容标准化、快照、缓存和回退的统一处理顺序。
- 提供 `content` 模块公开的只读 Application API，供页面接口、推荐和工具适配器复用。
- 为推荐工具提供第一版可复现候选，并明确没有 A 场次事实时的返回方式。
- 固定第一版测试数据和回归用例结构，为迁移、实现和测试阶段提供同一份依据。

**Non-Goals:**

- 不设计真实内容 Provider 的厂商字段、鉴权和许可方案。
- 不在 D 模块保存或修改场次、票价、座位、库存和交易状态。
- 不在第一版实现完整推荐评分、画像融合和 AI 标签。
- 不新增对本应用 Controller 的内部 HTTP 调用。

## Decisions

### 1. 按现有模块分层实现

后续实现落在以下边界内：

```text
content/
  application/      影片和影院查询用例、公开 DTO、Provider/快照/缓存端口
  domain/           内容值对象、来源与过期规则
  infrastructure/   Demo Provider、MyBatis 存储、Redis 缓存、资源文件读取

recommendation/
  application/      固定候选查询与结果 DTO
  domain/           候选完整性、过期和可购资格规则
  infrastructure/   固定候选资源读取

agent/tool/         B 的工具协议适配器，仅调用 recommendation Application API
```

`content` 和 `recommendation` 不依赖 Web 类型。Controller、Agent 工具适配器和未来 Job 只能调用公开 Application API；MyBatis、Redis 和资源文件读取只存在于 `infrastructure`。

备选方案是在推荐工具中直接读取 JSON。该方案会绕过内容过期与来源校验，也会让 B 的工具适配器承担 D 的业务判断，因此不采用。

### 2. D 接管原有共享固定 Demo 数据

`content-and-show-selection-flow` 中已有的 10 部影片、4 家影院原本只用于 A 的场次演示。自本变更起，D 接管这份清单并以 `demo-content-v1` 维护；A 的 `DemoSeedInitializer` 仍按“内容种子先执行、票务种子后执行”编排，但 A 不再维护影片、影院清单。影片标题、影院名称、来源 ID、城市、行政区和地址保持稳定；经纬度只为后续用户主动路线能力保留，不进入日志和推荐默认评分。

`demo-content-v1` 是 D 维护的共享数据文件版本、Provider 回退数据和测试夹具；它不得与内容种子维护两套影片、影院。资源文件以 `source + sourceMovieId` 或 `source + sourceCinemaId` 表示跨环境稳定身份，不写入数据库内部 `movieId/cinemaId`。首次初始化由应用生成雪花 ID；重复初始化查询并返回既有 ID，A 的票务种子只使用 `ContentSeedCatalog` 返回的实际 ID。加载时由统一 `Clock` 生成本轮 `dataTime` 和 `expiresAt`；自动测试使用固定 `Clock`，从而同时满足演示数据不过期和回归结果可重复。

共享初始化按 `source + sourceMovieId` 或 `source + sourceCinemaId` 更新，不按运行次数生成新 ID。相同数据版本重复初始化不得产生重复记录；D 不实现第二个影片、影院初始化器。

`source_movie_id`、`source_cinema_id` 在表结构中允许为空，以兼容未来上游未提供来源 ID 的内容；但固定 Mock 数据必须非空。MySQL 联合唯一键允许多条包含 `NULL` 的记录，因此来源 ID 为空时不得以 `source + source_*_id` 作为幂等更新、去重或同一对象判定依据。第一版 `DemoContentProvider` 不生成来源 ID 为空的数据；真实 Provider 接入前必须在对应变更中定义替代身份识别规则。

备选方案是把固定数据直接写在 Java 常量中。资源文件更容易评审、生成测试夹具和比较版本差异，因此不采用 Java 硬编码。

### 3. 内容查询使用统一来源封套和公开摘要端口

公开查询建议采用以下边界：

- `MovieQueryService`、`CinemaQueryService`：D 内部按城市、关键字或 ID 查询内容的 Application 用例。
- `ContentSummaryQueryPort`：D 对 A 公开的只读 Application API，按影片或影院 ID 批量或单个查询摘要；`CinemaSummary` 固定返回影院名称、区域、来源、数据时间、过期时间和过期标识。
- `ContentResult<T>`：包装 `data/source/dataTime/expiresAt/isExpired/degraded/fallbackType`。
- `MovieView`、`CinemaView`：只暴露标准化字段，至少包含影片标题或影院名称、影院区域、来源、数据时间、过期时间和过期标识；业务 ID 对外按十进制字符串表示。

这里的 Service 和 Port 都是同一 Spring Boot 应用内的 Application API，不是 HTTP 服务。需要 REST 时，由 API 层 Controller 调用这些用例；A 只能调用 `ContentSummaryQueryPort` 和其 DTO，不访问 D 的 Entity、Mapper、Repository、缓存实现或内部查询用例。找不到内容时，Port 返回明确的未找到结果；已过期内容保留来源和时间标识，A 可用于只读展示，不得把它当作新的可购事实。

`ContentSummaryQueryPort` 尚未实现时，A 可以使用经 D 确认、明确标识 `MOCK/demo-seed` 的临时 Demo Adapter。该 Adapter 只返回场次展示所需的内容摘要，必须复用共享固定数据的稳定 ID；不得写入 `movie/cinema`、维护另一套内容数据，或生成场次、票价、座位和库存。

### 4. 缓存、快照和 Demo 回退顺序固定

查询流程固定为：

```text
校验输入
  → 读取未过期缓存
  → 读取未过期标准化快照
  → 读取未超过最大陈旧时间的过期快照
  → 读取 Demo 固定数据
  → 返回 303004
```

缓存键采用 `ext:content:{city}:{resource}:{idOrHash}`；影片和影院内容默认 TTL 为 6 小时，快照最大陈旧时间为 7 天。具体数值进入配置，不散落在业务代码中。

缓存只保存标准化 DTO，不保存 Provider 原始响应。命中缓存、快照或 Demo 时分别返回 `fallbackType=CACHE|SNAPSHOT|MOCK`，并设置 `degraded=true`。缓存不可用不能阻断数据库快照和 Demo 查询。

备选方案是查询失败后直接返回 Demo。固定回退顺序能保留最近一次有效数据，也能稳定验证缓存和过期行为，因此不采用直接跳到 Demo 的方案。

### 5. D 确认 T09 至 T12，A 负责迁移版本与执行

迁移阶段只考虑以下 D 表，不增加重复表：

- `movie`：标准化影片快照；`source_movie_id` 非空时，业务唯一键为 `source + source_movie_id`。
- `cinema`：标准化影院快照；`source_cinema_id` 非空时，业务唯一键为 `source + source_cinema_id`。
- `external_data_snapshot`：保留最小必要 Provider 载荷，唯一键为 `provider + external_id + data_type`；包含 `version`、`create_time`、`update_time`，并限制 `expire_time` 为空或不早于 `data_time`。
- `data_sync_log`：记录初始化或同步结果，唯一键为 `provider + request_id`；包含 `version`、`create_time`、`update_time`，并有按 `create_time` 查询和清理满 180 天日志的索引。

字段、类型、索引和生命周期以总后端设计的 T09 至 T12 为基线。`external_data_snapshot.expire_time` 在 Java DTO 中映射为 `expiresAt`；不因此增加第二个过期字段。

`data_sync_log` 的状态只允许 `RUNNING`、`SUCCESS`、`FAILED`、`PARTIAL`。`RUNNING` 可以记录已处理数量，但 `finished_at` 必须为空；结束状态的 `finished_at` 必须不早于 `started_at`。结束时，`SUCCESS` 必须全部成功、`FAILED` 必须全部失败、`PARTIAL` 必须同时存在成功和失败记录，且成功数与失败数之和等于总数。三个统计数不得为负，处理中成功数与失败数之和不得超过总数。日志由 D 后续通过 Application Service 每日分批清理 `create_time` 满 180 天的记录，每批最多 500 条。

本变更不创建物理外键。所有主键使用应用生成的 BIGINT 雪花 ID，外部返回时转换为字符串；数据库时间使用 `DATETIME(3)`，应用通过统一时钟处理。

T09、T10 的迁移草案由 A 提供，D 只审查字段、索引、约束和生命周期。T11、T12 如纳入本变更，D 向 A 提交表清单、字段、索引、唯一约束、检查约束和验证场景；A 分配版本并生成或审核最终 SQL。D 不自行占用版本、不创建最终 Flyway 文件、不启用 Flyway，也不执行空 MySQL 迁移。空 MySQL 验证及证据由 A 完成。

### 6. 固定推荐先验证工具结果，不冒充完整推荐算法

第一版候选版本使用 `fixed-rec-v1`。推荐 Application API 接收类型化条件，返回固定顺序的 `RecommendationCandidate` 列表和 `algorithmVersion`；工具适配器再包装为 B 定义的 `ToolResult<T>`。

固定候选分两种：

1. 只有 D 影片和影院数据时，返回 `purchaseEligible=false` 的内容候选和 `missingFactors=[SHOWTIME]`，不得生成 `PLAN_CARD` 可购方案。
2. A 的公开 Application 查询可用后，候选从查询结果选择并引用 A 的 `showId/movieId/cinemaId/price/startTime/expiresAt`。D 只做完整性和过期校验，不维护第二份场次、价格或开场时间数据，也不通过 HTTP 调用本应用 Controller。

可购候选的 `price` 必须是两位小数字符串。缺字段或过期候选直接排除；结果为空时返回成功状态和空列表，由 B 决定展示固定购票入口或继续询问。

备选方案是 D 临时生成场次和价格以完成卡片展示。该方案会与 A 的种子和后续交易数据冲突，因此不采用。

### 7. 固定回归数据与业务数据使用同一版本

第一版用例至少覆盖：

- Demo 影片列表和详情重复查询结果一致。
- Demo 影院按城市查询结果一致。
- 有效缓存命中并返回 `CACHE`。
- 缓存不可用时返回有效快照。
- 允许的过期快照标记 `isExpired=true`，且不进入可购推荐。
- 缓存、快照和 Demo 均无数据时返回 `303004`。
- 相同输入的固定推荐候选和顺序一致。
- 未取得 A 场次时不生成 `PLAN_CARD`。
- A 的公开场次 Application 查询可用后，只引用其返回的业务事实。

每个回归用例记录 `caseId/module/priority/preconditions/input/mockProfile/expectedTool/expectedBusinessRefs/expectedResult/forbiddenResult/dataSourceExpectation/timeoutMs/owner`。测试资源引用同一 `demo-content-v1` 和 `fixed-rec-v1`，避免业务数据与期望结果分别维护后不一致。

## Risks / Trade-offs

- [共享固定数据与 A 场次数据 ID 不一致] → D 维护唯一的 `demo-content-v1`，内容种子返回实际数据库 ID 给 A；实现前记录来源 ID 清单和验证用例，确认前推荐只返回不可购内容候选。
- [固定时间导致演示数据自然过期] → 资源文件保存版本和稳定字段，初始化时通过统一时钟生成本轮时间；测试使用固定时钟。
- [Redis 不可用导致查询失败] → 缓存作为可选加速层，失败后继续查数据库快照和 Demo 数据。
- [过期快照被误当成当前事实] → `isExpired` 由服务端统一计算；推荐的可购资格校验强制排除过期候选。
- [Demo 数据被前端展示为真实内容] → 所有 Demo 结果返回 `source=DEMO_CONTENT`、`sourceType=MOCK` 和 `fallbackType=MOCK`。
- [第一版固定推荐被误认为正式算法] → 固定使用 `algorithmVersion=fixed-rec-v1`，结果中保留来源和缺失因素，后续正式算法使用新版本号。

## Migration Plan

1. D 审查并确认本变更的 proposal、两份 spec、design 和 tasks；确认前停止后续步骤。
2. D 对照总后端设计确认 T09 至 T12 的字段、索引、时间和清理规则；审查 A 已生成的 T09、T10 草案。
3. D 向 A 提交 T11、T12 的迁移申请材料；A 分配版本、生成或审核最终 SQL，并在空 MySQL 8 执行验证和保存证据。
4. D 接管 A 演示阶段已有的 10 部影片、4 家影院，固定为唯一的 `demo-content-v1`；内容种子返回实际数据库 ID，A 的票务种子只消费该返回值。
5. D 实现内容查询、`ContentSummaryQueryPort`、缓存、快照和固定推荐边界。
6. D 建立并执行应用查询、缓存降级和固定回归测试；A 的场次查询完成后，增加推荐引用其公开 Application 查询结果的集成测试。
7. 由 A、B、C 分别确认场次边界、工具结果和展示字段后完成验收。

迁移或初始化失败时，停止应用新版本并回退到上一稳定应用和数据库备份。已经共享或执行的 Flyway 脚本不得修改；修正必须追加新的向前迁移。

## Open Questions

- A 与 D 确认 `ContentSummaryQueryPort` 和场次公开 Application 查询的方法名、包位置及 DTO 字段；在实现前补入双方变更。
- `demo-content-v1` 的资源文件落位、Provider 读取和固定时钟夹具由 D 在 3.1、3.2、5.1 实现；本阶段仅以来源 ID 清单作为交接证据。
- B 最终采用的推荐工具名称和类型化 Command 名称是什么？不影响 D 的 Application API 和固定候选规则。
- C 首版页面是否需要直接调用影片/影院 REST 接口？若需要，在实现阶段补 API 层和 OpenAPI，不改变内部查询规格。
