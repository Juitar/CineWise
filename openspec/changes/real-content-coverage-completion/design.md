## Context

现有真实 Provider 已完成“NetStart → 标准化 → 快照/缓存 → 内容查询”的基础通路，但 `MovieContent` 只保存标题、类型、时长和评分；`ContentController` 将 `posterUrl`、`summary` 固定为 `null`。影院同步把硬编码的 Provider `ci=70` 直接作为 `cityCode`，且映射器未读取可用坐标。同步只拉取热映目录。前端影片页已能消费 `posterUrl`，但影院页和首页仍是静态展示。

这次变更以真实基础资料为范围，不改变 A 对票务事实的责任。任何新增电影字段、公开 REST 字段或前端类型都要先获得 C 确认；任何新增表字段或索引都由 A 分配 Flyway 版本并在独立 MySQL 库验证。

## 本次调整后的真实资料版本和回退规则

影片、影院基础资料是相对稳定的目录资料，不再用 6 小时 `expiresAt` 或 7 天最大陈旧期决定能否展示。每个公开查询键在 MySQL 保留两份完整成功 LIVE 版本：最新成功版本和上一成功版本；二者都记录实际 `dataTime`（同步完成时间）和版本号。新一次同步只有在该查询的完整版本校验通过后，才替换最旧版本。

| 实际读取位置 | 选择条件 | 页面提示 |
| --- | --- | --- |
| Redis | 命中任一已验证真实版本 | 显示真实来源和该版本同步时间 |
| MySQL 最新真实版本 | Redis 未命中或损坏 | 显示真实来源和最近成功同步时间 |
| MySQL 上一真实版本 | 最新版本损坏或不可读 | 显示“正在展示最近成功同步资料”及上一版本同步时间 |
| Demo | 从未有成功真实版本、明确演示模式，或两份真实版本均不可恢复 | 显示“演示数据版本”，不得伪装成刚同步的真实资料 |
| 全部不可用 | Demo 目录也无匹配内容 | 返回 `303004`；详情接口映射为 404 |

Redis TTL 只用于限制缓存空间和自然清理，不能表示资料真假、有效期或决定是否回退 Demo。稳定目录资料保留现有 `source/sourceType/dataTime/expiresAt/isExpired/degraded/fallbackType` 契约；可选版本与回退原因只能新增，不能删除或改变已有字段含义。Demo 的 `dataTime` 改为 Demo 目录版本的检查时间，不再在每次查询时伪造“刚刚更新”；Demo 不需要内容有效期。

## Data Flow

```text
NetStart 城市列表（受控更新后写入本地 JSON）
  -> D 城市目录加载器：启动时校验并构建只读城市名 -> ci 索引
C/B 临时 locationText
  -> D 城市解析服务：识别唯一城市名，不保存地点原文
  -> 本地城市目录：取得 Provider ci
  -> D 已同步影院索引：取得本地 cinemaIds
  -> A 公开只读场次 API：以业务日期和 cinemaIds 查询可售场次快照
NetStart 影片/影院响应
  -> D Provider Mapper：字段校验、HTTPS 海报规范化、城市标识转换、坐标/上映资料映射
  -> MovieContent / CinemaContent：不包含影评、用户数据和票务事实
  -> MySQL 内容表 + 标准化快照 + Redis 缓存
  -> D REST：影片、影院、来源状态和受控同步
  -> C 模块 API/Hook：首页、影片页、影院页（默认长沙、手动选城或地点解析后的城市）
```

## Decisions

### 1. 海报只保存规范化 HTTPS 地址

D 在 Mapper 内将 Provider 海报字段转换为绝对 HTTPS URL。不能解析、协议不是 HTTPS、主机为空或超出已确认规则的地址一律转为 `null`，不让前端承担协议修复。C 仍保留同源/HTTPS 二次校验和加载失败占位，作为不可信外部输入的最后一道保护。

### 2. 影片详情字段按可选基础资料处理

`summary`、上映状态和上映日期必须在领域模型、持久化、缓存/快照编解码、REST DTO 和前端类型中一致。简介只接受短简介字段；影评、评论和原始富文本不进入模型。Provider 未提供的可选字段返回 `null`，不会为了完整卡片填造内容。

### 3. 地点字符串、城市目录和坐标保持三个不同概念

NetStart `cities.json` 已实测返回 1151 条 `id/nm/py`，其中长沙为 `70`、杭州为 `50`。D 将经过人工核验的最小城市目录以版本化 JSON 放在应用资源中，记录来源、检查时间、城市名和 Provider `ci`；应用启动时一次性加载并校验城市名、`ci` 均唯一。日常用户请求、页面加载和影院查询绝不访问城市列表接口。

C 的手动选择或浏览器侧已经得到的地点信息通过 `POST /api/v1/content/cities/resolve` 向 D 传临时 `{locationText}`，避免它进入 URL。D 按规范化后的地点字符串匹配本地城市名：唯一命中才返回 `RESOLVED + cityName` 并在内部取得 `ci`；零命中返回 `UNRECOGNIZED`，多命中返回 `SELECTION_REQUIRED`。公开响应不返回候选地点、`providerCityId` 或 `ci`；页面会话只能保存返回的城市名。

B 的对话地点信息只作为该次 D 城市解析调用的内存参数，调用结束立即丢弃。Agent 会持久化用户消息，因此 B 必须在保存用户消息、事件、槽位快照、长期上下文、日志和缓存前剔除或替换原始地点文本；不能只依靠 D 的 DTO 不落库。地点原文、浏览器位置和候选列表不得写入 MySQL、Redis、日志、快照、URL、画像或 Agent 轨迹；允许保存城市名或标准 `cityCode`，但不保存经纬度或 NetStart 内部城市 ID。

`ci` 仅用于 D 调用 NetStart，不能返回给 C/B。页面查询和展示只使用城市名；影院持久化与同步审计另存规范化 `city_name` 和 `provider_city_id`，不再把中国行政区划代码作为新城市资料的前置条件。影院坐标仅映射 Provider 已给出的合法静态经纬度，不能通过地点字符串或地址猜测。

城市解析成功后，D 只从本地已同步且未删除的影院资料取得 `cinemaIds`。没有已同步影院是正常空结果，D 不请求 A、不用其他城市影院替代。A 新增的公开只读场次 API 只接收业务日期和 `cinemaIds`，不接收地点字符串、城市名、城市代码或 Provider `ci`；A 决定场次是否可售、返回数量、排序和查询错误，D 只消费其快照，建单时仍由 A 重查库存与状态。

城市目录更新不是运行时自动任务：D 用受控离线步骤请求 NetStart 列表、核对新增/删除/改名和唯一性后更新 JSON、测试和版本号，再随应用发布。城市不在目录或本地未同步时，页面显示“当前城市暂无法查询影院”，不能把另一个城市的影院作为替代。

### 4. 首次目录回填、每日增量与本地查询分开

实现前先用脱敏测试请求确认 NetStart 是否提供上映日期、近一年已上映范围、待映目录和分页/游标。不能根据现有单页热映接口推定这些能力存在；若验证不通过，本 change 只能同步 Provider 实际可取得范围，并须记录是否改用另一合法来源的决定。

**2026-08-05 能力核验结果：**`/index/movieOnInfoList` 可返回 12 条当前热映条目及 `rt` 上映日期；`/index/moreComingList?token=&limit=10&offset=0` 可接受 `limit/offset` 并返回 `coming` 数组，但首批数据出现 2021 年条目，不能据此证明它是可靠的待映目录。当前已验证接口没有近一年历史目录或按上映日期筛选入口。因此本 change 不得把 NetStart 的首次同步称为“近一年完整回填”；在取得可验证的合法历史目录前，只实施 Provider 实际可获取的范围，并保留本地分页查询能力。

验证通过后，首次目录任务以“近一年已上映影片 + Provider 可取得待映目录”为目标，按日期倒序分页或游标分批拉取，再逐部获取详情。每批在字段校验成功后提交，并保存游标/页码、范围、批次号和成功计数，便于断网、限流或进程中断后从已确认位置恢复；重复数据按稳定外部身份幂等更新。首次回填不会因影片离开热映列表而删除已同步历史记录。

每天凌晨三点的任务不重新把用户浏览请求转发到 Provider，而是扫描新上映、待映状态或上映日期变化，以及已有资料更新。同步分别处理热映、待映目录并请求详情；上映状态/日期描述内容资料，不能作为 A 场次可售判断。Provider 限流预算、重试和失败审计沿用已有规则，并按目录、同步模式（首次回填/每日增量）和资源记录清楚数量。

`GET /api/v1/movies` 始终只查询本地持久化目录，支持按上映日期倒序分页、关键字、类型和上映状态筛选。用户翻页或搜索仍会请求后端，但不会触发外部 Provider 调用，因此外部限流、超时或失败不会直接影响用户继续浏览已同步目录。

开发环境的已确认 NetStart Provider 默认开启，并在 Asia/Shanghai 每天凌晨 3 点执行定时同步；启动同步保持关闭，避免开发重启反复请求外部服务。开发排障可用显式环境变量关闭 Provider。生产或商业 profile 必须拒绝启用该学习用途 Provider，即使错误配置了开关也不能发起外部调用。同步失败只写审计，绝不删除最近两份成功真实版本。

### 5. 本期影院浏览支持默认城市、手动选择和临时地点解析

当前没有常用城市或用户城市偏好能力，C 在没有城市输入时默认展示长沙。用户手动选择城市时直接提交城市名；C/B 已提供地点字符串时调用 D 解析，再以返回城市名查询影院。城市值只存在当前页面或会话，不能写入用户资料、画像或服务端持久化。

本期影院浏览不计算或展示距离。若 C 在用户授权后取得地点字符串，它只交给 D 做当次城市解析；用户主动发起基础路线时仍按路线模块既有规则处理，精确坐标不传给内容接口、不写入 URL、日志或持久化。

### 6. 公开身份解析只提供身份，不提供票务事实

根据 A 的确认，当前 change 不实现真实排期。D 提供公开 Application API，让未来 A 的真实排期 change 以同一 `provider + resourceType` 批量解析 1 至 100 个 `externalId`，逐项取得唯一内部 `movieId/cinemaId` 或稳定失败码。该 API 只解释内容身份，既不返回 D 的持久化对象，也不读取或生成场次、价格、库存、座位、订单或支付数据。

调用整体仅在参数格式错误时以 `100001` 失败：provider/resourceType/ID 规范化后必须非空，provider 最大 64 字符，externalId 最大 128 字符，资源类型仅限 MOVIE/CINEMA，批量大小 1 至 100。同批重复 ID 去重后按首次出现顺序返回，避免 A 为同一场次候选重复查询。

每条映射有 ACTIVE/INVALID 状态和失效原因。只有 ACTIVE 的 `provider + resourceType + externalId` 可以返回内部 ID；未匹配为 `303005`、多记录冲突为 `303006`、已失效为 `303007`。内容被逻辑删除、来源身份被替换或发现身份冲突时，D 将旧映射标记 INVALID 并保留最小审计原因，不把旧外部身份重新指向另一个内部 ID。A 收到单项失败仅隔离未来排期候选，不修改既有 Mock 场次、座位、订单或电子票。

映射状态需要独立于内容缓存和两版本资料快照保存；具体表、索引、生命周期和前向迁移由 D 在 A 分配版本后实现。真实排期 Provider、`movie_show`、Mock 隔离和交易边界由 A 后续 `real-showtime-read-model`、`real-showtime-ticketing` 两个独立 change 决定。

### 6.1 公开批量影院摘要只提供展示资料

`ContentSummaryQueryPort` 是 A 调用 D 的同进程 Java Application API，不是 HTTP 接口。它以一组 `cinemaIds` 批量返回 `cinemaId/name/address/source/dataTime/expiresAt/isExpired`；不返回 D 的 Entity、Mapper、缓存键、Provider 原始 ID、坐标或地点原文。

空输入返回空批量结果。部分不存在、逻辑删除或尚未同步的影院 ID 通过 `missingCinemaIds` 返回，其他有效影院继续返回；这不是整体失败。只有内容摘要存储不可读、结果无法安全构造等整体故障时，端口返回固定 `303004` 内容不可用错误，A 不得把它伪装成“没有可售影院”。`source` 是 D 已确认的固定来源标识，`dataTime` 是该影院资料最近一次成功同步时间；A 原样转发展示，不把它当作票务实时承诺。

`movie_tag`、`recommendation_record` 不属于本次真实内容覆盖范围。推荐历史的输入快照、候选快照、结果快照、用户隐私、唯一键、CHECK、保留期和清理机制必须在独立推荐历史 change 完整确认后，才可以提出新迁移。

### 7. 管理同步为按城市执行的受控写操作

来源状态是管理员只读接口。管理员手动同步提交稳定 `clientRequestId` 和城市名，D 必须先在本地城市目录中解析 `ci`，再同步该城市资料；不接受地点原文、任意 `ci` 或任意 Provider URL。同步开始前先持久化 `RUNNING` 记录，以 `(provider, request_id)` 唯一键阻止重复请求；网络响应未知时只能按原 `clientRequestId` 查询，不得重新调用 Provider。

公开同步结果固定为 `syncId/clientRequestId/cityName/status/startedAt/finishedAt/successCount/failureCount/failureCategory`；`finishedAt` 未完成时可为空，`failureCategory` 无失败时可为空。按请求查询复用同一结构。来源状态每条返回 `provider/resourceType/cityName/status/startedAt/finishedAt/lastSuccessAt/successCount/failureCount/failureCategory/dataTime/expiresAt/isExpired/licenseNotice`。Provider 城市 ID 仅可留在服务端审计数据，不得出现在任何公开响应。

两个管理员 GET 不要求 CSRF，POST 必须带 `X-XSRF-TOKEN`。同一 `clientRequestId + cityName` 返回原任务；同一请求标识提交不同城市返回 `409/100409`；不存在的原任务返回 `404/100404`。Provider 已受理后的超时或失败通过任务状态 `PARTIAL/FAILED` 表示，不改写 POST 的 HTTP 返回；POST 超时或断网后，C 进入 `RESULT_UNKNOWN`，只查询原请求，不能重新 POST 或生成新请求标识。

### 8. C 页面只展示 D 内容和 A 公开票务查询的实际结果

C 负责移除首页/影院页的静态影片和影院数据，并用模块 API、Hook 替换。距离只在用户主动授权并请求路线后显示；场次、价格和余座只能来自 A 的公开查询。D 不修改 C 的共享请求层或页面视觉规范，A 不读取 D 的内部持久化对象。

## Migration and Compatibility

本节是 D 提交 A 审查的完整结构设计，不是 Flyway SQL 或执行授权。A 已正式分配 V014；迁移只做向后兼容的新增列、新表、索引和 CHECK，不修改 V001～V012、不包含数据回填或演示种子、不建立物理外键。现有表和新增字符串列须在空 MySQL 验证中确认 `utf8mb4_0900_ai_ci`。

| 对象 | 新增字段 | 约束与索引 | 保留、兼容和写入规则 |
| --- | --- | --- | --- |
| `movie` | `poster_url VARCHAR(2048) NULL`；`summary VARCHAR(2000) NULL`；`release_status VARCHAR(16) NULL`；`release_date DATE NULL` | `CHECK (release_status IS NULL OR release_status IN ('NOW_SHOWING','COMING_SOON'))`；新增 `INDEX(release_status, release_date, deleted_at)` 支撑本地上映状态与日期查询。HTTPS、摘要内容和日期格式由 D Mapper 校验，不把 URL 正则写进数据库 CHECK。 | 四列均可空，旧影片保持 `NULL`，下一次合格同步再写入；不把缺失字段当作删除，不回填虚构内容。公开 DTO 保持可空字段，旧客户端可忽略新增值。 |
| `content_identity_mapping` 新表 | `id BIGINT NOT NULL`；`provider VARCHAR(64) NOT NULL`；`resource_type VARCHAR(16) NOT NULL`；`external_id VARCHAR(128) NOT NULL`；`internal_content_id BIGINT NOT NULL`；`status VARCHAR(16) NOT NULL`；`invalid_reason VARCHAR(32) NULL`；`invalidated_at DATETIME(3) NULL`；`active_internal_content_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN internal_content_id ELSE NULL END) STORED`；`create_time/update_time DATETIME(3) NOT NULL`。 | PK(`id`)；UNIQUE(`provider`,`resource_type`,`external_id`)；UNIQUE(`provider`,`resource_type`,`active_internal_content_id`)；INDEX(`resource_type`,`internal_content_id`,`status`)；`resource_type` 仅 `MOVIE/CINEMA`；`status` 仅 `ACTIVE/INVALID`；`invalid_reason` 仅 `SOURCE_REPLACED/CONTENT_DELETED/IDENTITY_CONFLICT/MANUAL_CORRECTION`；CHECK：`ACTIVE` 的失效字段均为 NULL，`INVALID` 的失效字段均非 NULL。无物理外键，D 应用层验证内部内容存在且类型匹配。 | 映射不可物理删除；`INVALID` 为终态，不重新激活也不静默改指向。首次发布后的受控应用回填只依据 V001 的 `source + source_movie_id/source_cinema_id` 写 ACTIVE 映射；冲突隔离并记录失败分类，不按标题、名称或地址猜测。保留 ACTIVE 和 INVALID 记录，不设自动清理。 |
| `cinema` | `city_name VARCHAR(64) NULL`；`provider_city_id VARCHAR(32) NULL`。 | 新增 `INDEX(city_name, deleted_at, id)` 供当前城市的未删除影院查询；新增 `INDEX(source, provider_city_id, deleted_at)` 供按 Provider 城市同步与核对。`provider_city_id` 仅是 D 的外部请求关联，不对 C/B/A 公开。 | 保留既有 `city_code`，不改写、不删除；新同步只写规范化 `city_name/provider_city_id`。历史行保持 NULL，不按地址、区域、坐标或旧 `city_code` 猜测回填；后续受控同步自然补齐。影院原有经纬度字段不变。 |
| `data_sync_log` | `city_name VARCHAR(64) NULL`；`provider_city_id VARCHAR(32) NULL`；`failure_category VARCHAR(32) NULL`。 | 保留 UNIQUE(`provider`,`request_id`)；新增 `INDEX(city_name, resource_type, started_at)`；把状态 CHECK 扩展为 `PENDING/RUNNING/SUCCESS/PARTIAL/FAILED`。`failure_category` 仅 `NETWORK/RATE_LIMIT/PROVIDER_RESPONSE/DATA_VALIDATION/INTERNAL` 或 NULL；PENDING/RUNNING 必须 `finished_at IS NULL`，SUCCESS/PARTIAL/FAILED 必须有不早于 `started_at` 的 `finished_at`；无失败时 `failure_category IS NULL`，有失败时必须非空。既有非负计数和完成计数关系 CHECK 保留并扩展 PENDING。 | 仅存城市名、内部 Provider 城市 ID、数值错误码和脱敏失败分类；不存地点原文、Provider URL、原始异常或原始响应。按既有 `create_time` 清理索引保留 180 天，清理只处理终态记录，RUNNING 记录不得按时间自动删除。公开 API 绝不返回 `provider_city_id`。 |

迁移发布顺序是：先新增全部可空列、新表、索引与 CHECK；随后发布能够兼容新旧字段的 D 代码；再由受控应用回填可确定的内容身份映射和后续真实同步。失败或冲突不通过 SQL 回滚历史数据，而是保留旧字段和最近成功快照；后续结构调整只能新增前向迁移。

当前已发布迁移目录最高为 V012，A 已分配后续迁移版本 V014。上述四类结构设计和过期版本描述更新完成后，D 才可提交 `V014` SQL 草案给 A 静态复核；草案通过后，A 再明确授权使用 `cinewise_migration_check + cinewise_migrator` 执行首次 migrate、validate、重复 migrate、结构/索引/CHECK、历史兼容和清理规则验证。本次不执行。`movie_tag`、`recommendation_record` 不属于本次申请，推荐历史另建 change 后再设计。

## Verification

- D：先验证 Provider 的近一年范围、上映日期及分页/游标能力；再验证 Mapper、领域模型、编解码、缓存/快照、城市映射、首次目录回填、每日增量、本地分页搜索、权限/幂等、异常/回退和 MySQL/Redis 受控环境。
- C：影片海报 HTTPS/失败占位、影院/首页 API 展示、默认长沙、手动城市、加载/空态/失败/刷新/来源提示，以及无本地场次时不展示票务事实。
- A：迁移静态审查、空 MySQL 验证，以及内容变更不改变既有订单、座位和库存。
- 全部完成后执行 `mvnw.cmd verify`、前端既有检查、`openspec validate real-content-coverage-completion --strict`、`git diff --check`。
