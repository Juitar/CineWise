# real-content-coverage Specification

## Purpose
TBD - created by archiving change real-content-coverage-completion. Update Purpose after archive.
## Requirements
### Requirement: 影片媒体和说明字段必须完整经过标准化链路

系统 SHALL 将 Provider 返回且通过规则校验的影片海报、简介、上映状态和上映日期作为影片基础资料处理。`posterUrl` 必须在 D 的 Provider 适配层规范化为 HTTPS 绝对地址；不合法、空白、非 HTTPS 或无法解析的地址不得写入 `MovieContent`、缓存、快照、数据库或 REST 响应。简介仅保存和展示 Provider 的短简介，不保存影评正文或用户评论。

#### Scenario: Provider 返回合法 HTTPS 海报

- **GIVEN** Provider 返回了可解析的 HTTPS 海报地址和合格影片最低字段
- **WHEN** D 同步并查询该影片
- **THEN** `MovieContent`、快照、缓存和 `MovieResponse.posterUrl` 返回同一规范化 HTTPS 地址
- **AND** C 的影片卡片显示图片，图片加载失败时显示占位而不发起 HTTP 图片请求

#### Scenario: Provider 返回 HTTP 或非法海报地址

- **GIVEN** Provider 返回 HTTP、相对地址、空白或无法解析的海报地址
- **WHEN** D 标准化该影片
- **THEN** 系统不保存该地址，REST 返回 `posterUrl=null`
- **AND** 其他已通过校验的影片基础字段仍可按规则保存和展示

### Requirement: 地点字符串必须经本地城市目录解析为 Provider 城市标识

系统 SHALL 将 NetStart `cities.json` 的经核验最小城市目录作为版本化本地 JSON 随应用发布。应用启动时必须校验每条城市名和 Provider `ci` 非空且唯一，并构建只读索引；地点解析、页面查询和同步不得在用户请求中访问 NetStart 城市列表。C 使用 `POST /api/v1/content/cities/resolve` 提交 `{locationText:string}`，响应固定为 `{status:"RESOLVED"|"UNRECOGNIZED"|"SELECTION_REQUIRED", cityName:string|null}`；只有 `RESOLVED` 返回 `cityName`。D 只能在唯一城市名匹配时得到 Provider `ci`。公开结果不返回候选地点、`providerCityId` 或 `ci`，地点原文不能持久化。目录损坏或无法读取时返回 `503/303004`，与正常的 `UNRECOGNIZED` 区分。

#### Scenario: 地点字符串解析为长沙

- **GIVEN** 本地城市目录包含 `长沙 -> 70`
- **WHEN** C 向城市解析接口或 B 向 D 的受控调用提交 `湖南省长沙市岳麓区` 作为 `locationText`
- **THEN** D 返回 `RESOLVED + cityName=长沙`，并仅在内部使用 `ci=70` 查询或同步影院
- **AND** 不访问 NetStart 城市列表，不保存该地点原文

#### Scenario: 地点无法解析或存在多个城市候选

- **GIVEN** 地点字符串未包含目录中的唯一城市名，或同时包含多个城市名
- **WHEN** C 或 B 请求城市解析
- **THEN** D 返回明确的不可用或需选择结果
- **AND** 不调用影院 Provider，不以相近名称、地址或坐标猜测城市

#### Scenario: 城市目录不可用

- **GIVEN** 随应用发布的城市目录损坏、缺少元数据或城市名、`ci` 存在重复
- **WHEN** C 或 B 请求城市解析
- **THEN** D 返回 `503/303004`，不将其表示为 `UNRECOGNIZED`
- **AND** 响应和日志不包含地点原文、候选列表或 Provider 原始内容

#### Scenario: 用户查询已同步城市的真实影院

- **GIVEN** D 已同步城市名为 `杭州`、Provider `ci=50` 的影院资料
- **WHEN** 页面按城市名查询影院
- **THEN** 查询能取得杭州真实影院资料，返回城市名 `杭州`
- **AND** Provider `ci` 不出现在公开 DTO、缓存键、URL 或页面状态中

#### Scenario: Agent 对话地点文本不会进入长期数据

- **GIVEN** 用户在 Agent 对话正文中提供地点文本
- **WHEN** B 将它用于一次 D 城市解析调用
- **THEN** 调用结束后原始地点文本被丢弃，并在保存用户消息、事件、槽位快照、长期上下文、日志和缓存前被剔除或替换
- **AND** 可以保存 D 返回的城市名或标准 `cityCode`，但不能保存地点原文、精确位置、经纬度或 NetStart 内部城市 ID

### Requirement: D 必须提供批量影院摘要供 A 聚合可售影院

系统 SHALL 提供同进程 Java Application API `ContentSummaryQueryPort`。A 输入一组本地 `cinemaIds` 后，D 必须批量返回每个有效影院的 `cinemaId`、`name`、`address`、`source`、`dataTime`、`expiresAt`、`isExpired`，不得暴露 D 的 Entity、Mapper、Repository、数据库表、缓存键、Provider 原始 ID、坐标或地点原文。

批量结果 MUST 同时包含有效摘要与 `missingCinemaIds`。空输入、部分 ID 无结果或全部 ID 无结果都是正常结果；`missingCinemaIds` 表示不存在、逻辑删除或尚未同步的本地影院，A 必须只聚合有效摘要。内容摘要存储整体不可用时，D 必须返回 `303004`，不得把整体故障表示为空结果。

#### Scenario: A 批量取得可售影院的展示资料

- **GIVEN** A 已通过自己的公开只读场次 API 得到多个本地影院 ID
- **WHEN** A 调用 `ContentSummaryQueryPort` 批量查询
- **THEN** D 一次返回每个有效影院的名称、地址、来源和最近成功同步时间
- **AND** A 不访问 D 的 Entity、Mapper、Repository、数据库表或缓存

#### Scenario: 批量中部分影院没有内容资料

- **GIVEN** A 查询的影院 ID 中一部分已逻辑删除、尚未同步或不存在
- **WHEN** D 批量查询影院摘要
- **THEN** 有效影院继续返回，缺失 ID 出现在 `missingCinemaIds`
- **AND** D 不因单个缺失 ID 使整批失败，不以同名影院替代

#### Scenario: 内容摘要整体不可用

- **GIVEN** 内容摘要存储不可读，无法安全构造批量结果
- **WHEN** A 查询影院摘要
- **THEN** D 返回 `303004`
- **AND** A 将其与正常空结果区分，不展示为“暂无可售影院”

#### Scenario: Provider 未给出坐标

- **GIVEN** Provider 的影院资料没有合法经纬度
- **WHEN** D 标准化和保存该影院
- **THEN** 经纬度保持 `null`
- **AND** 页面只显示名称和地址，不显示伪造距离或路线

### Requirement: 影院浏览必须支持城市选择和地点解析且不得伪造距离

系统 SHALL 在首页和影院页提供手动城市选择，并支持使用 C/B 已得到的临时地点字符串解析当前城市。本期影院浏览 MUST NOT 计算距离或提供“距离优先”排序；页面不得以“附近”或距离文案暗示未实现的定位结果。基础路线的用户主动定位不属于本 change。

#### Scenario: 用户手动选择长沙

- **GIVEN** 用户在首页或影院页选择长沙
- **WHEN** 页面查询影院资料
- **THEN** 页面以城市名长沙查询并展示长沙影院，不展示杭州影院
- **AND** 页面不显示距离或“附近”

### Requirement: 未选择城市时必须使用长沙作为演示默认城市

系统 SHALL 在用户没有地点输入、没有手动选择城市且当前未提供常用城市功能时，使用配置的默认城市长沙。默认值只属于当前页面会话和演示初始状态，用户手动切换或地点解析成功后覆盖它；系统不得将默认值、用户选择或地点原文保存为用户偏好。

#### Scenario: 首次打开影院页且未定位

- **GIVEN** 用户首次打开首页或影院页，未提供地点且未手动选择城市
- **WHEN** 页面加载影院资料
- **THEN** 页面以城市名长沙查询影院并明确显示当前城市为长沙
- **AND** 不把长沙或地点原文写入用户画像、账户资料或服务端持久化存储

### Requirement: 当前热映目录必须可恢复地分批同步

实现前，D SHALL 实测并记录 Provider 可取得的目录范围、上映日期及分页/游标能力。NetStart 当前只确认可取得热映目录，未确认近一年历史目录或可靠待映目录；系统不得假称已完成近一年回填。

系统 SHALL 在首次或管理员受控同步时读取当前可取得的热映目录，并对每部影片分批查询详情补齐基础字段。同步不得因为本地 10 req/min 保护值只固定处理前 8 部影片；应保存已完成详情的稳定外部身份，并在不超过限流的前提下恢复未完成批次。页面只浏览已成功同步的本地热映目录；同步失败、重复或字段不合格的记录不得覆盖已有合格资料。

#### Scenario: 当前热映目录跨批同步

- **GIVEN** Provider 返回当前热映目录，目录中有超过单分钟详情预算的有效影片身份
- **WHEN** 系统首次或管理员受控同步执行
- **THEN** 系统按不超过 10 req/min 的批次保存已完成详情，并记录未完成身份供后续恢复
- **AND** 中断后恢复时不重复创建影片，也不把未完成身份伪造成已同步资料

#### Scenario: Provider 不支持近一年完整范围

- **GIVEN** D 实测发现 Provider 无法按上映日期、分页或游标取得近一年完整影片范围
- **WHEN** 系统同步当前可取得热映目录
- **THEN** 系统不把该目录标为“近一年完整目录”或可靠待映目录
- **AND** 用户翻页只读取已同步的热映资料，不触发 Provider 调用

### Requirement: 真实基础资料必须保留两次成功版本且优先于 Demo

影片和影院基础资料 SHALL 不再使用 `expiresAt`、固定 TTL 或最大陈旧天数决定是否可展示。每个公开查询键必须保留最新两次完整成功的 LIVE 资料版本，记录版本号和同步时间；最新版本不可读时使用上一成功版本。Redis 可以设置仅用于容量控制的物理过期时间，但 Redis 过期不得删除或降低 MySQL 中的两份真实版本优先级。为兼容现有前端，`expiresAt/isExpired` 保留在公开响应中；它们不决定真实基础资料是否让位给 Demo。

页面使用历史真实版本时必须显示最近成功同步时间和“正在展示最近成功同步资料”；它不是实时票务承诺，但不得因为资料较旧自动改用 Demo。Demo 只可用于该查询从未成功保存真实版本、明确处于演示模式，或两份真实版本都损坏/不可恢复的极端情况。

#### Scenario: 半年前的真实影院资料是唯一可用资料

- **GIVEN** 某城市最近一次完整真实影院同步距今半年，且之后同步持续失败
- **WHEN** 用户查询该城市影院
- **THEN** 系统返回该次真实资料及其实际同步时间
- **AND** 不返回固定 Demo 影院替代它

#### Scenario: 最新真实版本损坏

- **GIVEN** 最新一次真实影片或影院版本无法解码，但上一成功版本完整
- **WHEN** 用户查询对应内容
- **THEN** 系统返回上一成功版本并记录可排查的损坏分类
- **AND** 不直接回退到 Demo

### Requirement: 开发环境必须每天凌晨三点同步真实目录

系统 SHALL 在 `dev` 环境默认启用已确认的 NetStart 真实内容 Provider，并在每天 `03:00`（Asia/Shanghai）执行一次影片、影院目录同步。`dev` 环境允许通过显式环境变量关闭 Provider 用于排障；`prod` 或商业环境必须拒绝启用该学习用途 Provider。启动完成时不自动同步，避免每次重启都额外访问 Provider。

#### Scenario: 开发环境凌晨三点同步失败

- **GIVEN** `dev` 环境已保存至少一份真实内容版本
- **WHEN** 每天凌晨三点的 Provider 同步超时、限流或字段校验失败
- **THEN** 本次同步记录失败原因，但不删除或覆盖已有真实版本
- **AND** 后续页面继续展示最近成功同步的真实资料

### Requirement: 前端必须能浏览全部已同步真实目录

系统 SHALL 将已同步的真实影片目录作为 `GET /api/v1/movies` 的分页、关键字、类型和上映状态筛选来源，并默认按上映日期倒序；不能只暴露同步批次前几个影片。页面翻页、关键字搜索或继续筛选只能查询本地已同步目录，不在普通用户请求中直接调用 Provider；需要补充新目录时由首次回填、每日定时同步或受控管理员同步完成。

#### Scenario: 用户不喜欢第一页影片

- **GIVEN** 已同步目录包含多页真实热映或待映影片
- **WHEN** 用户切换页码或输入新的关键字、类型
- **THEN** 接口从完整已同步目录返回匹配的后续真实影片
- **AND** 不访问 Provider，也不因为默认同步上限只剩固定几部候选

### Requirement: 内容来源状态和手动同步必须受权限保护

系统 SHALL 提供 `GET /api/v1/admin/content/sources`、`POST /api/v1/admin/content/sync` 和 `GET /api/v1/admin/content/sync/by-request/{clientRequestId}`。三个接口都要求管理员 Cookie；两个 GET 不要求 CSRF，POST 必须携带 `X-XSRF-TOKEN`。未登录返回 `401/201006`，非管理员返回 `403/201007`，CSRF 无效返回 `403/201009`，参数缺失、空城市或目录不支持的城市返回 `400/100001`，服务整体不可用返回 `503/303004`。

管理员发起同步时，POST 请求只能提交 `{clientRequestId, cityName}`；D 必须先由本地城市目录解析 `ci`，先创建 `PENDING` 审计记录；只有用随机内部 `leaseOwner` 条件取得 `RUNNING` 租约的实例才调用 Provider。租约为 90 秒，存活实例每 20 秒续租；Provider 一次同步调用（含一次短重试）总超时不得超过 60 秒。续租、内容资料写入和终态写入均须校验当前 `leaseOwner` 与未到期租约；未命中时丢弃响应，不得覆盖终态或写入资料。同一 `clientRequestId + cityName` 必须返回原任务且不再次同步；同一 `clientRequestId` 携带不同城市名返回 `409/100409`。请求不存在时，按请求查询返回 `404/100404`。

POST 响应和按请求查询统一返回 `syncId/clientRequestId/cityName/status/startedAt/finishedAt/successCount/failureCount/failureCategory`；`status` 只允许 `PENDING/RUNNING/SUCCESS/PARTIAL/FAILED`，未完成时 `finishedAt=null`，无失败时 `failureCategory=null`。Provider 在任务受理后超时或失败时，查询结果返回 `PARTIAL` 或 `FAILED`，不改写 POST 的 HTTP 返回。来源状态每条返回 `provider/resourceType/cityName/status/startedAt/finishedAt/lastSuccessAt/successCount/failureCount/failureCategory/dataTime/expiresAt/isExpired/licenseNotice`。公开响应不得返回 `providerCityId`、`ci`、Provider URL、原始异常或原始响应。

#### Scenario: 管理员查看同步状态

- **GIVEN** 已存在真实内容同步记录
- **WHEN** 管理员查询内容来源状态
- **THEN** 返回最近同步结果和时效信息，不返回 Key 或完整原始响应

#### Scenario: 管理员同步结果未知

- **GIVEN** 管理员已带稳定 `clientRequestId` 提交同步，但页面收到超时或断网
- **WHEN** 页面进入 `RESULT_UNKNOWN`
- **THEN** 页面禁用再次同步，只能调用按请求查询接口查询原 `clientRequestId`
- **AND** 不生成新的请求标识、不重新 POST，也不以来源状态接口的最近结果替代原任务

#### Scenario: 普通用户尝试手动同步

- **GIVEN** 当前请求不是管理员
- **WHEN** 请求内容同步接口
- **THEN** 系统拒绝请求，且不会发起 Provider 调用或写入同步记录

### Requirement: D 必须提供公开内容身份解析契约

系统 SHALL 提供仅供本应用模块调用的公开 Application API。A 传入同一 `provider`、同一 `resourceType`（`MOVIE` 或 `CINEMA`）和一组 `externalId` 后，D 返回每个外部 ID 的唯一内部 `movieId` 或 `cinemaId`。A 只能依赖该 API，不得访问 D 的 Controller、Entity、Mapper、Repository、缓存或内容表。

`provider`、`resourceType` 和每个 `externalId` 必须去除首尾空白后非空；`provider` 最长 64 个字符，`externalId` 最长 128 个字符；`resourceType` 只允许 `MOVIE`、`CINEMA`；一次调用必须包含 1 至 100 个 ID。参数不合法、资源类型不支持、批量超过上限时，调用整体返回通用参数错误 `100001`。同批重复 ID 只解析一次，结果按首次出现顺序返回。

单项解析只接受仍为 ACTIVE 的 `provider + resourceType + externalId` 映射。未匹配、同一身份映射到多个内部对象、映射已失效时，系统分别返回 `303005`、`303006`、`303007`；不得按影片标题、影院名称、地址或坐标猜测匹配，也不得自动合并记录。单项失败不影响同批其他有效结果。

| 数值码 | 名称 | 含义 | A 的处理 |
| --- | --- | --- | --- |
| `100001` | `INVALID_PARAMETER` | 批量输入格式、长度、资源类型或数量不合法 | 修正调用，不写排期 |
| `303005` | `CONTENT_IDENTITY_NOT_FOUND` | 没有 ACTIVE 映射 | 隔离该排期候选，不写入 |
| `303006` | `CONTENT_IDENTITY_AMBIGUOUS` | 一个外部身份对应多个内部内容 | 隔离并人工复核，不写入 |
| `303007` | `CONTENT_IDENTITY_INVALIDATED` | 曾存在的映射已失效 | 隔离后续候选，不修改已有订单或 Mock 数据 |

#### Scenario: A 解析唯一的真实影院身份

- **GIVEN** D 已保存 `provider + CINEMA + externalId` 对应的唯一真实影院
- **WHEN** A 调用公开身份解析 API
- **THEN** D 返回该影院唯一的内部 `cinemaId`
- **AND** A 无需读取 D 的持久化实现

#### Scenario: A 批量解析影片身份

- **GIVEN** A 提供同一 `provider`、`MOVIE` 资源类型和多个外部影片 ID
- **WHEN** A 调用公开批量身份解析 API
- **THEN** D 按首次出现顺序逐项返回唯一内部 ID 或对应数值码
- **AND** 某一条未匹配、歧义或失效不影响同批其他影片的解析

#### Scenario: 身份未匹配或存在歧义

- **GIVEN** 请求的外部身份不存在或匹配到多个内部对象
- **WHEN** A 调用公开身份解析 API
- **THEN** D 返回不可解析结果和固定原因分类
- **AND** 不返回名称相近的影片或影院作为替代结果

#### Scenario: 映射在内容更新后失效

- **GIVEN** D 已将某外部身份标记为失效，例如对应内容被逻辑删除、来源身份被替换或发现身份冲突
- **WHEN** A 解析该外部身份
- **THEN** D 返回 `303007`
- **AND** A 只隔离未来真实排期候选，不修改已有 Mock 场次、座位、订单或电子票

### Requirement: A 必须通过真实演示目录取得排期引用

D SHALL 在 `ContentPurchaseQueryPort` 提供 `findLiveDemoPurchaseCatalog(cityCode)`。它返回 `DemoPurchaseCatalog(movies, cinemas, source, dataAt, expiresAt)`，仅供 A 在 dev/demo 为真实影院创建本地 `demo-seed` 排期。A 不得读取 D 的表、Mapper、Repository、缓存、Provider 或 Controller，也不得将目录资料当作真实票价、座位、场次、订单或支付事实。

`cityCode` 必须为六位正行政区划编码，否则返回 `100001`。`movies` 和 `cinemas` 两个 List 均不可为 `null`，构造时按本地 ID 升序去重；同一本地 ID 或来源 ID 映射到不同引用时拒绝目录。每个本地 ID 为正，来源 ID 非空，影片 `durationMinutes` 为正。目录只接受未删除且 `expiresAt > now` 的 `LIVE/NETSTART_MAOYAN` 资料；到期时间等于当前时刻也必须排除。本期若出现多个实际来源，D 拒绝构造目录，不以 `MIXED` 掩盖来源差异。影片最多三部，影院返回指定城市全部合格记录。目录无合格影院或影片时正常返回两个空列表；内容存储不可读时返回 `303004`，不得伪装成空目录。`dataAt` 和 `expiresAt` 分别取返回记录中最早的资料时间和最早的到期时间。

#### Scenario: 长沙真实目录可用于本地 Mock 排期

- **GIVEN** 长沙存在两家未过期 `LIVE/NETSTART_MAOYAN` 影院和四部合格影片
- **WHEN** A 调用 `findLiveDemoPurchaseCatalog("430100")`
- **THEN** D 返回两家影院和本地 ID 最小的三部影片，均按本地 ID 升序
- **AND** `source` 为 `NETSTART_MAOYAN`，`expiresAt` 不晚于任一返回记录的到期时间

#### Scenario: 内容目录没有可用真实资料

- **GIVEN** 指定城市没有合格真实影院，或没有合格真实影片
- **WHEN** A 查询演示目录
- **THEN** D 返回 movies 和 cinemas 均为空的正常目录
- **AND** 不返回固定 Demo 内容

#### Scenario: 内容目录存储不可读

- **GIVEN** 读取真实影片或影院目录失败
- **WHEN** A 查询演示目录
- **THEN** D 返回 `303004`
- **AND** A 可以与正常空目录区分

### Requirement: 真实内容迁移必须保持历史内容与同步记录兼容

系统 MUST 保持 V014 前的影片、影院和同步记录可读取，并且不得通过后续代码或 SQL 修改已执行的 V014。V014 已在 V013 之后完成 A 静态复核、MySQL 8.4.11 空库验证和共享 `cinewise` 发布。迁移仅新增 `movie` 的可空资料字段、`content_identity_mapping`、`cinema.city_name/provider_city_id` 和 `data_sync_log.city_name/provider_city_id/failure_category/lease_owner/lease_until`，未修改 V001～V013、未建立物理外键、未写入演示种子或按地址、名称、区域、坐标猜测历史城市/身份。已执行 V014 SQL 自发布起冻结，后续调整必须使用更高版本的前向迁移。

`content_identity_mapping` 必须以 `provider/resource_type/external_id` 唯一标识外部身份，以生成的 ACTIVE 内部内容 ID 约束同一 Provider、资源类型和内部内容最多一个 ACTIVE 外部 ID；`ACTIVE` 映射不得有失效字段，`INVALID` 映射必须有固定失效分类和失效时间。V014 只建表，不做 SQL 回填；D 在迁移后以 V001 的 `source + source_movie_id/source_cinema_id` 运行受控、可重复的应用回填，并将批次、成功数和冲突数记入同步审计。`movie.release_status` 只能为 `NOW_SHOWING`、`COMING_SOON` 或 `NULL`。`data_sync_log` 必须保留 V004 的计数约束：三个计数非负，且 `success_count+failure_count<=total_count`。V014 仅增加 PENDING 的零计数和全空字段规则，并允许 `lease_owner/lease_until` 成对为空或非空；它保留当前 RUNNING、FAILED/PARTIAL 的写入形态，避免未升级的同步代码被 CHECK 拒绝。D 发布新同步写入器、完成共享库只读预检和历史兼容处理后，V015 才收紧为：RUNNING 必须有 90 秒租约且每 20 秒按持有者续租；FAILED/PARTIAL 必须有错误码和固定失败分类；SUCCESS 不得有错误字段；终态持有者和租约均为空。FAILED 允许 `total_count=0/success_count=0/failure_count=0`，用于尚未获得候选项即失败的外部请求，但仍必须有错误码和失败分类。续租及资料/终态写入均须命中当前未到期持有者；真正过期的 RUNNING 才可转为 `FAILED + INTERNAL`，且不重调 Provider。公开接口不得返回 Provider 城市 ID 或持有者。

#### Scenario: 迁移后读取 V001 历史内容

- **GIVEN** V001 已存在没有真实资料增量字段、城市名或 Provider 城市 ID 的影片、影院和同步记录
- **WHEN** 执行新的向前迁移并发布兼容代码
- **THEN** 历史行保持可读取，新增可空字段为 NULL，不删除或重写 `city_code`
- **AND** 系统不通过 SQL 或应用按标题、地址、区域、坐标猜测补齐身份或城市

#### Scenario: 身份映射冲突

- **GIVEN** 同一 Provider、资源类型和内部内容已存在 ACTIVE 外部身份
- **WHEN** 迁移后的受控应用回填或后续同步尝试写入第二个 ACTIVE 外部身份
- **THEN** 唯一约束拒绝该写入，应用隔离该条并记录固定失败分类
- **AND** 不改变既有 ACTIVE 映射或票务数据

#### Scenario: 同步日志包含按城市状态

- **GIVEN** 管理员按城市发起真实内容同步
- **WHEN** D 创建或更新同步审计记录
- **THEN** 记录规范化城市名、内部 Provider 城市 ID、状态和固定失败分类
- **AND** 管理端只收到城市名和脱敏状态字段，不收到 Provider 城市 ID、地点原文或原始异常

#### Scenario: V014 保持旧同步日志兼容并允许 PENDING

- **GIVEN** V004 已有未写入 `lease_owner/lease_until` 的 RUNNING 日志，或未写入 `failure_category`、`error_code` 的 FAILED/PARTIAL 日志
- **WHEN** V014 已发布且未升级的同步代码继续写入相同形态的日志
- **THEN** 旧记录和新写入均不被 V014 的 CHECK 拒绝
- **AND** 新建 PENDING 仅在三个计数均为 0、错误字段和租约字段均为空时可写入

#### Scenario: V015 的 PENDING 请求取得唯一执行租约

- **GIVEN** V015 已在新同步 Writer 发布和受控兼容处理完成后收紧状态 CHECK，管理员已登记 PENDING 同步请求，三个计数为 0，错误字段、完成时间、持有者和租约均为空
- **WHEN** 多个实例同时尝试执行该请求
- **THEN** 只有一个实例条件更新为 RUNNING 并写入随机 `lease_owner` 和非空 `lease_until`
- **AND** 只有取得租约的实例调用 Provider，其他实例只返回原请求状态

#### Scenario: V015 下慢 Provider 的存活实例续租

- **GIVEN** V015 已启用严格租约 CHECK，同步已处于 RUNNING，实例仍存活且 Provider 响应较慢但仍在 60 秒总超时内
- **WHEN** Provider I/O 仍在进行，持有者每 20 秒续租
- **THEN** 只有匹配 `lease_owner` 且租约未到期的续租更新可以延长租约，恢复任务不得把该记录改为 FAILED
- **AND** Provider 返回后，只有匹配同一持有者和未到期租约的资料及终态写入可以成功

#### Scenario: V015 的 RUNNING 租约因进程中断而真正到期

- **GIVEN** V015 已启用严格租约 CHECK，同步已处于 RUNNING，Provider 调用期间实例退出，续租停止且 `lease_until` 到期
- **WHEN** 恢复任务扫描该记录
- **THEN** 恢复任务条件更新为 `FAILED`、`failure_category=INTERNAL`，写入固定 `303004`、终态时间并清空持有者和租约
- **AND** 不再次调用 Provider；同一 `clientRequestId` 的查询只能返回该终态，管理员需要新请求标识才能发起新同步

#### Scenario: 五种状态拒绝统计不一致的记录

- **GIVEN** V015 在 D 的新同步写入器发布并完成历史兼容处理后收紧同步状态计数 CHECK
- **WHEN** 尝试写入 SUCCESS 的 `success_count<total_count`、FAILED 的 `success_count>0`、PARTIAL 的成功失败之和不等于总数，或 RUNNING 的成功失败之和大于总数
- **THEN** 数据库必须拒绝这些记录
- **AND** PENDING 只允许三个计数均为 0；合法的 SUCCESS、FAILED、PARTIAL 和未完成 RUNNING 记录仍可写入

### Requirement: 页面必须展示 API 内容且不得伪造票务事实

影院页和首页的影片、影院区域 SHALL 通过 C 的模块 API、Hook 和公共请求层读取 D 的内容接口。页面不得继续将写死的影片、杭州影院、演示距离、场次数量、起价或影厅标签作为真实内容展示。真实内容没有 A 的可售场次时，只显示基础资料、来源和“暂无可售场次”，不得显示价格、余座或购票按钮。

#### Scenario: 影院页收到真实影院基础资料

- **GIVEN** `/api/v1/cinemas` 返回真实影院资料
- **WHEN** 用户打开影院页
- **THEN** 页面显示 API 返回的名称、地址和来源时效状态
- **AND** 不显示页面内写死的杭州影院、演示距离、演示场次或演示起价

#### Scenario: 真实影片没有本地可售场次

- **GIVEN** 真实影片未关联 A 的 ON_SALE 本地场次
- **WHEN** 用户浏览影片或首页内容
- **THEN** 页面显示该影片基础资料和无场次状态
- **AND** 不显示或推断价格、库存、座位和购票入口

### Requirement: 答辩受控城市切换

系统 MUST 在用户端顶栏和管理员内容同步页面提供长沙（`430100`）与杭州（`330100`）两个固定城市选项。用户端选中的城市 MUST 通过 `location` 查询参数传给首页和影院查询；页面不得保存或展示原始地点文本、Provider 城市标识、距离或定位结果。

#### Scenario: 用户切换杭州浏览真实影院

- **GIVEN** 用户当前在首页或影院页
- **WHEN** 用户选择杭州
- **THEN** 页面使用 `location=330100` 刷新对应城市的影院内容，并显示杭州为当前城市
- **AND** 无本地可售场次时只显示内容空态，不伪造价格、余座或购票入口

#### Scenario: 管理员选择城市同步

- **GIVEN** 管理员打开内容同步页面
- **WHEN** 管理员在固定下拉中选择长沙或杭州并提交同步
- **THEN** 页面向既有同步接口提交对应城市名，并保持原有 CSRF、幂等和结果未知恢复行为
- **AND** 页面不得提交或显示内部 Provider 城市标识

