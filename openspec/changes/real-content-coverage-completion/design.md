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

目录加载失败、元数据缺失或城市名/`ci` 重复时，应用保留“城市目录不可用”状态，不记录原始文件内容或地点文本；解析请求返回 `503/303004`。这与目录可用但地点无匹配的 `UNRECOGNIZED` 不同，页面应提示稍后重试。

B 的对话地点信息只作为该次 D 城市解析调用的内存参数，调用结束立即丢弃。Agent 会持久化用户消息，因此 B 必须在保存用户消息、事件、槽位快照、长期上下文、日志和缓存前剔除或替换原始地点文本；不能只依靠 D 的 DTO 不落库。地点原文、浏览器位置和候选列表不得写入 MySQL、Redis、日志、快照、URL、画像或 Agent 轨迹；允许保存城市名或标准 `cityCode`，但不保存经纬度或 NetStart 内部城市 ID。

`ci` 仅用于 D 调用 NetStart，不能返回给 C/B。页面查询和展示只使用城市名；影院持久化与同步审计另存规范化 `city_name` 和 `provider_city_id`，不再把中国行政区划代码作为新城市资料的前置条件。影院坐标仅映射 Provider 已给出的合法静态经纬度，不能通过地点字符串或地址猜测。

城市解析成功后，D 只从本地已同步且未删除的影院资料取得 `cinemaIds`。没有已同步影院是正常空结果，D 不请求 A、不用其他城市影院替代。A 新增的公开只读场次 API 只接收业务日期和 `cinemaIds`，不接收地点字符串、城市名、城市代码或 Provider `ci`；A 决定场次是否可售、返回数量、排序和查询错误，D 只消费其快照，建单时仍由 A 重查库存与状态。

城市目录更新不是运行时自动任务：D 用受控离线步骤请求 NetStart 列表、核对新增/删除/改名和唯一性后更新 JSON、测试和版本号，再随应用发布。城市不在目录或本地未同步时，页面显示“当前城市暂无法查询影院”，不能把另一个城市的影院作为替代。

### 4. 首次目录回填、每日增量与本地查询分开

实现前先用脱敏测试请求确认 NetStart 是否提供上映日期、近一年已上映范围、待映目录和分页/游标。不能根据现有单页热映接口推定这些能力存在；若验证不通过，本 change 只能同步 Provider 实际可取得范围，并须记录是否改用另一合法来源的决定。

**2026-08-05 能力核验结果：**`/index/movieOnInfoList` 可返回 12 条当前热映条目及 `rt` 上映日期；`/index/moreComingList?token=&limit=10&offset=0` 可接受 `limit/offset` 并返回 `coming` 数组，但首批数据出现 2021 年条目，不能据此证明它是可靠的待映目录。当前已验证接口没有近一年历史目录或按上映日期筛选入口。因此本 change 不得把 NetStart 的首次同步称为“近一年完整回填”；在取得可验证的合法历史目录前，只实施 Provider 实际可获取的范围，并保留本地分页查询能力。

根据 2026-08-05 能力核验和本次确认，首次目录任务只以当前可取得热映目录为目标，不实现近一年历史或可靠待映回填。系统先读取热映身份，再按不超过 10 req/min 的批次逐部获取详情；每批保存已完成外部身份、剩余身份、批次号和成功计数，便于断网、限流或进程中断后继续。重复数据按稳定外部身份幂等更新，未完成身份不写入公开目录。

每天凌晨三点的任务不重新把用户浏览请求转发到 Provider，而是重新读取当前热映目录，并补齐尚未成功的详情或更新已有资料。上映状态/日期只描述内容资料，不能作为 A 场次可售判断。Provider 限流预算、重试和失败审计沿用已有规则，并按目录、同步模式和资源记录清楚数量。

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

### 6.2 真实演示目录只提供 A 的最小排期引用

`ContentPurchaseQueryPort.findLiveDemoPurchaseCatalog(cityCode)` 是 A 唯一可读取真实影院演示目录的同进程 Application API。D 在内部通过 JDBC Adapter 同时读取 `movie` 和指定 `city_code` 的 `cinema`，筛选 `source_type=LIVE`、`source=NETSTART_MAOYAN`、未逻辑删除、来源 ID 非空、正本地 ID、未过期，影片额外要求正时长。Adapter 按本地 ID 升序读出全部影院和最多三部影片；由于 SQL 只接受单一常量来源，本期遇到其他来源只排除，不构造 `MIXED` 目录。

电影或影院任一侧没有合格记录时，返回两个空列表，避免 A 用不完整目录写出只含影院或只含影片的 Mock 排期。读取故障统一转为 `303004`；`cityCode` 在 Application 层按六位正行政区划编码校验，非法值为 `100001`。目录 `dataAt/expiresAt` 取所有返回记录的最早值，确保 A 不会把一部分已经到期的资料继续当成有效目录。这个目录不走普通内容查询的缓存、快照或 Demo 回退，也不调用 Provider。

### 7. 管理同步为按城市执行的受控写操作

来源状态是管理员只读接口。管理员手动同步提交稳定 `clientRequestId` 和城市名，D 必须先在本地城市目录中解析 `ci`，再同步该城市资料；不接受地点原文、任意 `ci` 或任意 Provider URL。同步开始先持久化 `PENDING` 记录，以 `(provider, request_id)` 唯一键阻止重复请求；获得租约的实例再条件更新为 `RUNNING` 并调用 Provider。网络响应未知时只能按原 `clientRequestId` 查询，不得重新调用 Provider。

公开同步结果固定为 `syncId/clientRequestId/cityName/status/startedAt/finishedAt/successCount/failureCount/failureCategory`；`finishedAt` 未完成时可为空，`failureCategory` 无失败时可为空。按请求查询复用同一结构。来源状态每条返回 `provider/resourceType/cityName/status/startedAt/finishedAt/lastSuccessAt/successCount/failureCount/failureCategory/dataTime/expiresAt/isExpired/licenseNotice`。Provider 城市 ID 仅可留在服务端审计数据，不得出现在任何公开响应。

两个管理员 GET 不要求 CSRF，POST 必须带 `X-XSRF-TOKEN`。同一 `clientRequestId + cityName` 返回原任务；同一请求标识提交不同城市返回 `409/100409`；不存在的原任务返回 `404/100404`。Provider 已受理后的超时或失败通过任务状态 `PARTIAL/FAILED` 表示，不改写 POST 的 HTTP 返回；POST 超时或断网后，C 进入 `RESULT_UNKNOWN`，只查询原请求，不能重新 POST 或生成新请求标识。

`data_sync_log` 的基础计数规则始终是三个计数非负，且 `success_count + failure_count <= total_count`。V014 只新增 `PENDING`：它表示请求已登记、尚未取得执行租约，三个计数均为 0，`error_code/error_summary/failure_category/finished_at/lease_owner/lease_until` 全为 NULL。V014 同时只要求 `lease_owner/lease_until` 成对为空或非空；现有 `RUNNING`、`SUCCESS`、`FAILED`、`PARTIAL` 保持 V004 的写入规则。这样当前 `JdbcContentPersistenceAdapter` 即使尚未写入失败分类和租约字段，也不会被新 CHECK 拒绝。

以下是 D 新同步写入器发布、共享库只读预检和历史兼容处理完成后，V015 才启用的严格规则：获得租约后转为 `RUNNING` 时，`finished_at` 和三个错误字段均为 NULL，`lease_owner` 和 `lease_until` 必须非空；`SUCCESS/PARTIAL/FAILED` 是终态，持有者和租约必须清空，`finished_at` 必须非空且不早于 `started_at`。`SUCCESS` 必须 `success_count=total_count`、`failure_count=0` 且错误字段均为 NULL；`FAILED` 必须 `success_count=0`、`failure_count=total_count`，允许三个计数均为 0，以记录尚未获得候选项就失败的外部请求，但 `error_code/failure_category` 必须非空；`PARTIAL` 必须成功数、失败数均大于 0、两者之和等于总数，并且 `error_code/failure_category` 非空。`error_summary` 只能是脱敏固定摘要或 NULL，绝不记录原始异常。

同样在 V015 的新写入器中，取得租约时按数据库当前时间设置 `lease_until=now+90 秒`；执行实例独立于 Provider I/O 每 20 秒续租一次。Provider 的一次同步调用（包含允许的一次短重试）必须有不超过 60 秒的总超时；因此正常存活实例即使 Provider 较慢，也会在租约到期前续租。续租和终态写入必须使用 `status=RUNNING AND lease_owner=当前执行标识 AND lease_until>数据库当前时间` 的条件更新，并要求影响一行。资料事务开始、每项资料/快照写入前和事务提交前都通过同一条件更新续租，并由数据库行锁保持到事务结束；任一次未命中即回滚本轮影片、影院、快照和审计日志，提交后的 Redis 发布也要再次复核。恢复器必须等待资料事务释放任务行锁，不能在旧 Worker 写资料与提交之间插入 `FAILED`。续租或复核未命中时，实例立即丢弃本次 Provider 响应，不得写入内容资料、缓存或覆盖已有终态。

V015 的恢复任务每分钟扫描 `PENDING` 和租约过期的 `RUNNING`：多实例只能通过状态、版本和新的 `lease_owner` 条件更新取得同一条 PENDING 的租约，取得者才可调用一次 Provider；PENDING 不会产生重复调用。对租约过期的 RUNNING，恢复任务只在 `lease_until<数据库当前时间` 时条件更新为 `FAILED + INTERNAL`，设置 `total_count=1/success_count=0/failure_count=1`、固定 `303004` 错误码、脱敏摘要、`finished_at` 并清空持有者和租约，绝不重新调用 Provider。这样进程在 Provider 调用中退出时，原请求最终可查询；慢 Provider 的存活实例会续租且能保存结果，同一请求标识永不触发第二次 Provider 调用；管理员只有使用新的 `clientRequestId` 才能发起新的同步。

### 8. C 页面只展示 D 内容和 A 公开票务查询的实际结果

C 负责移除首页/影院页的静态影片和影院数据，并用模块 API、Hook 替换。距离只在用户主动授权并请求路线后显示；场次、价格和余座只能来自 A 的公开查询。D 不修改 C 的共享请求层或页面视觉规范，A 不读取 D 的内部持久化对象。

## Migration and Compatibility

本节记录已发布 V014 的结构设计和后续代码兼容要求。V014 只做向后兼容的新增列、新表、索引和 CHECK，不修改 V001～V013、不包含数据回填或演示种子、不建立物理外键。A 已于 2026-08-06 完成静态复核、MySQL 8.4.11 空库验证和共享 `cinewise` 发布；结构、索引、CHECK、字符集和排序规则证据见 `docs/database-migrations/V014_MIGRATION_VALIDATION_2026-08-06.md`、`docs/database-migrations/V014_SHARED_MIGRATION_2026-08-06.md`。

| 对象 | 新增字段 | 约束与索引 | 保留、兼容和写入规则 |
| --- | --- | --- | --- |
| `movie` | `poster_url VARCHAR(2048) NULL`；`summary VARCHAR(2000) NULL`；`release_status VARCHAR(16) NULL`；`release_date DATE NULL` | `CHECK (release_status IS NULL OR release_status IN ('NOW_SHOWING','COMING_SOON'))`；新增 `INDEX(release_status, release_date, deleted_at)` 支撑本地上映状态与日期查询。HTTPS、摘要内容和日期格式由 D Mapper 校验，不把 URL 正则写进数据库 CHECK。 | 四列均可空，旧影片保持 `NULL`，下一次合格同步再写入；不把缺失字段当作删除，不回填虚构内容。公开 DTO 保持可空字段，旧客户端可忽略新增值。 |
| `content_identity_mapping` 新表 | `id BIGINT NOT NULL`；`provider VARCHAR(64) NOT NULL`；`resource_type VARCHAR(16) NOT NULL`；`external_id VARCHAR(128) NOT NULL`；`internal_content_id BIGINT NOT NULL`；`status VARCHAR(16) NOT NULL`；`invalid_reason VARCHAR(32) NULL`；`invalidated_at DATETIME(3) NULL`；`active_internal_content_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN internal_content_id ELSE NULL END) STORED`；`create_time/update_time DATETIME(3) NOT NULL`。 | PK(`id`)；UNIQUE(`provider`,`resource_type`,`external_id`)；UNIQUE(`provider`,`resource_type`,`active_internal_content_id`)；INDEX(`resource_type`,`internal_content_id`,`status`)；`resource_type` 仅 `MOVIE/CINEMA`；`status` 仅 `ACTIVE/INVALID`；`invalid_reason` 仅 `SOURCE_REPLACED/CONTENT_DELETED/IDENTITY_CONFLICT/MANUAL_CORRECTION`；CHECK：`ACTIVE` 的失效字段均为 NULL，`INVALID` 的失效字段均非 NULL。无物理外键，D 应用层验证内部内容存在且类型匹配。 | V014 不做数据回填。迁移后由 D 的受控应用任务按 V001 的 `source + source_movie_id/source_cinema_id` 分批、可重复地建立 ACTIVE 映射，并把批次、成功数和冲突数写入 `data_sync_log`；冲突隔离并记录失败分类，不按标题、名称或地址猜测。映射不可物理删除；`INVALID` 为终态，不重新激活也不静默改指向。保留 ACTIVE 和 INVALID 记录，不设自动清理。 |
| `cinema` | `city_name VARCHAR(64) NULL`；`provider_city_id VARCHAR(32) NULL`。 | 新增 `INDEX(city_name, deleted_at, id)` 供当前城市的未删除影院查询；新增 `INDEX(source, provider_city_id, deleted_at)` 供按 Provider 城市同步与核对。`provider_city_id` 仅是 D 的外部请求关联，不对 C/B/A 公开。 | 保留既有 `city_code`，不改写、不删除；新同步只写规范化 `city_name/provider_city_id`。历史行保持 NULL，不按地址、区域、坐标或旧 `city_code` 猜测回填；后续受控同步自然补齐。影院原有经纬度字段不变。 |
| `data_sync_log` | `city_name VARCHAR(64) NULL`；`provider_city_id VARCHAR(32) NULL`；`failure_category VARCHAR(32) NULL`；`lease_owner VARCHAR(64) NULL`；`lease_until DATETIME(3) NULL`。 | 保留 `uk_sync_request(provider, request_id)`；新增 `idx_sync_city_resource_started(city_name, resource_type, started_at)` 与 `idx_sync_recovery(status, lease_until)`。V014 保留 V004 的非负、已处理数量和 RUNNING/SUCCESS/FAILED/PARTIAL 写入形态，仅增加严格的 PENDING 全空/零计数规则及 `lease_owner/lease_until` 成对为空或非空规则；避免现有同步代码在 D 新写入器发布前被 CHECK 拒绝。D 完成新写入器、受控历史兼容处理和只读预检后，V015 再收紧：RUNNING 必须持有租约；FAILED/PARTIAL 必须有 `error_code/failure_category`；SUCCESS 不得有错误字段；终态持有者和租约为空。FAILED 在 V015 允许 `total_count=0/success_count=0/failure_count=0`，用于尚未获得候选项即失败的外部请求，但仍必须有错误码和失败分类。`failure_category` 仅 `NETWORK/RATE_LIMIT/PROVIDER_RESPONSE/DATA_VALIDATION/INTERNAL` 或 NULL。 | 仅存城市名、内部 Provider 城市 ID、数值错误码、脱敏失败分类和不对外的随机执行标识；不存地点原文、Provider URL、原始异常或原始响应。持有者每 20 秒在数据库条件更新下续租至当前时间后 90 秒；恢复任务只把真正过期的 RUNNING 条件更新为 `FAILED + INTERNAL`，不重新调用 Provider。按既有 `create_time` 清理索引保留 180 天，清理只处理终态记录，PENDING/RUNNING 不得按时间自动删除。公开 API 绝不返回 `provider_city_id` 或 `lease_owner`。 |

迁移发布顺序是：V014 先新增全部可空列、新表、索引与兼容 CHECK；随后发布能够兼容新旧字段的 D 代码和受控身份映射回填；在共享库完成只读预检及历史兼容处理后，V015 再收紧同步状态 CHECK。失败或冲突不通过 SQL 回滚历史数据，而是保留旧字段和最近成功快照；后续结构调整只能新增前向迁移。

V014 已在 V013 之后完成验证和发布，SQL checksum 为 `-1507904895`；已执行 SQL 不得修改。下一步是发布兼容新旧字段的 D 写入代码、受控身份映射回填和共享库只读预检；上述完成后，V015 才能收紧同步状态 CHECK。`movie_tag`、`recommendation_record` 不属于本次 change，推荐历史另建 change 后再设计。

## Verification

- D：先验证 Provider 的近一年范围、上映日期及分页/游标能力；再验证 Mapper、领域模型、编解码、缓存/快照、城市映射、首次目录回填、每日增量、本地分页搜索、权限/幂等、异常/回退和 MySQL/Redis 受控环境。
- C：影片海报 HTTPS/失败占位、影院/首页 API 展示、默认长沙、手动城市、加载/空态/失败/刷新/来源提示，以及无本地场次时不展示票务事实。
- A：迁移静态审查、空 MySQL 验证，以及内容变更不改变既有订单、座位和库存。
- 全部完成后执行 `mvnw.cmd verify`、前端既有检查、`openspec validate real-content-coverage-completion --strict`、`git diff --check`。
