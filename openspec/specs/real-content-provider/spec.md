# real-content-provider Specification

## Purpose
在数据源合法、可配置且可验证时，为影片和影院页面提供真实基础信息；在任何外部条件不满足或调用失败时，继续稳定返回现有缓存、快照或 Demo 数据，并明确数据来源和时效。
## Requirements
### Requirement: 学习模式 Provider 必须明确用途和限制
系统 SHALL 将 NetStart 标识为学习/演示模式外部来源，只能在开发或演示环境按已公开的非商业学习用途使用；不得宣称官方合作或实时官方票务数据。公开记录为：来源 <https://apis.netstart.cn/maoyan/>，访问日期 2026-08-04，文档名称“猫眼(M站)API接口文档”，页面声明“本项目仅供学习交流使用，请勿用于商业用途。”D MUST 记录接口范围、字段映射、公开使用声明、Key 是否有要求、配额是否公开和停用条件；Key 或配额未在公开文档中说明时，系统 MUST 将其记录为未知风险、设置本地保护限流并保持可关闭，不得把未知内容描述为已获授权或无限配额。正式生产或商业模式 MUST 不启用该 Provider，除非另有正式授权。

#### Scenario: 学习用途和字段范围已公开
- **GIVEN** 已记录上述公开网址、访问日期、页面声明，并确认其列出影片和影院查询接口
- **WHEN** D 将其配置为开发/演示环境的外部来源
- **THEN** 系统可以调用影片和影院基础信息接口
- **AND** 页面和对外说明必须标明学习用途、第三方来源和非官方数据，不得把结果称为猫眼官方实时票务数据

#### Scenario: 许可或字段映射未确认
- **GIVEN** 候选数据源尚未确认允许的使用范围或影片/影院字段映射
- **WHEN** 系统收到内容查询或同步请求
- **THEN** 系统不得向该数据源发出请求或把数据标识为真实内容
- **AND** 系统继续按既有缓存、快照和 Demo 规则返回可用结果

#### Scenario: 运行环境缺少 Provider Key
- **GIVEN** Provider 需要 Key 但当前环境缺少有效 Key，或运行配置未显式启用
- **WHEN** 系统启动或收到内容查询
- **THEN** 系统不得在响应、日志、缓存、快照或同步记录中暴露 Key
- **AND** 系统保持 Demo 与离线演示可用

#### Scenario: 配额未公开
- **GIVEN** NetStart 公开文档未说明每日配额或每分钟限制
- **WHEN** 系统在开发/演示环境启用 NetStart
- **THEN** 系统必须使用本 change 新增的本地 10 req/min 限流、超时、一次短重试和随时关闭开关
- **AND** 不得宣称 Provider 具有无限配额；触发 429、持续失败或服务条款变化时立即回退到缓存、快照和 Demo

#### Scenario: 开发环境启动后受控执行一次同步
- **GIVEN** 当前 profile 为 `dev` 或 `demo`，并同时设置 `CINEWISE_CONTENT_NETSTART_ENABLED=true`、`CINEWISE_CONTENT_NETSTART_SYNC_ON_STARTUP=true`
- **WHEN** 应用启动完成
- **THEN** 系统只调用一次真实内容同步，不新增公开刷新接口
- **AND** `CINEWISE_SCHEDULING_ENABLED=false` 时不注册自动定时任务，测试窗口只保留该次启动同步
- **AND** 任一 NetStart 开关为 false 时不得访问 Provider、MySQL 或 Redis，页面继续使用原有回退

### Requirement: 真实基础信息必须标准化并具有时效
系统 SHALL 只接收和返回标准化的影片、影院基础信息；每条结果 MUST 包含可展示的来源、数据时间、有效期、过期标识、降级标识和回退类型。影片至少按标题、类型、片长、评分等已确认基础字段标准化；影院至少按名称、城市、行政区域和地址等已确认基础字段标准化。字段不满足对应资源的最低质量规则时，系统 MUST 拒绝该条实时数据，不得将其写为可用缓存或快照。

#### Scenario: Provider 返回合格的影片和影院基础信息
- **GIVEN** 已启用 Provider 返回的基础字段、时间和来源均通过校验
- **WHEN** 系统查询或同步影片、影院
- **THEN** 系统返回标准化结果并标识真实来源与有效期
- **AND** 返回 `isExpired=false`、`degraded=false`，且不包含 Provider 原始字段

#### Scenario: Provider 返回缺少最低必要字段的数据
- **GIVEN** Provider 返回的某条影片或影院缺少其最低必要字段，或数据时间、有效期不合法
- **WHEN** 系统校验该条数据
- **THEN** 系统不得把该条数据写入可用缓存、快照或影片/影院基础信息
- **AND** 系统使用该查询其他可用来源或返回明确不可用结果

### Requirement: 外部 ID 为空时必须使用受控身份识别
系统 SHALL 将非空的 `provider + resourceType + externalId` 作为该 Provider 内的优先身份键。外部 ID 为空时，系统 MUST 不使用空外部 ID 进行幂等更新、去重或覆盖已有影片、影院记录；只有在 D 定义并记录该 Provider 的专用稳定候选键、规范化规则、冲突处理和人工复核方式后，才可以将其作为待确认映射处理。

#### Scenario: Provider 返回空外部 ID
- **GIVEN** 已启用 Provider 返回一条外部 ID 为空的影片或影院
- **WHEN** 系统准备识别、去重或保存该条数据
- **THEN** 系统不得以空值或名称单字段覆盖既有记录
- **AND** 在专用身份识别规则与人工复核流程未确认前，该条数据不得写入标准内容快照或影片、影院基础信息

#### Scenario: 受控候选键与已有内容冲突
- **GIVEN** 外部 ID 为空的记录按已确认候选键匹配到多个已有内容
- **WHEN** 系统处理该记录
- **THEN** 系统不得自动合并、删除或覆盖任一内容
- **AND** 系统记录不含 Key 和原始敏感载荷的质量异常，并等待人工复核

### Requirement: 查询必须保持既有回退顺序和离线演示
页面查询 SHALL 按“真实缓存 → 有效真实快照 → 未超过最大陈旧时间的过期真实快照 → 唯一 `demo-content-v1` → `303004`”选择结果，且不得在页面请求中调用 NetStart。每天一次的同步 SHALL 按“NetStart → 标准化 → 更新真实快照 → 清掉旧缓存或写入新的真实缓存”执行。固定 Demo 直接返回且不进入缓存。Provider 超时、限流、网络失败、可重试服务端失败或字段校验失败 MUST 不改变页面读取顺序；缓存、快照和 Demo 仍使用既有内容基线的 TTL、最大陈旧时间及来源封套规则。

#### Scenario: Provider 调用超时
- **GIVEN** 每日同步中的 NetStart 在规定连接或读取时限内未返回
- **WHEN** 同步完成允许的一次短重试后仍失败
- **THEN** 页面仍依次尝试真实缓存、真实快照、允许陈旧快照和 `demo-content-v1`
- **AND** 返回的非实时结果保留其原有来源、时间、过期和降级标识

#### Scenario: 外网断开但 Demo 数据可用
- **GIVEN** Provider、缓存和快照都不可用，且 `demo-content-v1` 有匹配内容
- **WHEN** 用户或下游模块查询影片、影院基础信息
- **THEN** 系统返回同一份 Demo 内容并标识 `source=DEMO_CONTENT`、`sourceType=MOCK`、`degraded=true`、`fallbackType=MOCK`
- **AND** 离线演示不依赖外部 Provider

#### Scenario: 同步成功后刷新真实读取结果
- **GIVEN** 每日同步已取得并通过校验的真实影片或影院基础信息
- **WHEN** 系统更新对应真实快照
- **THEN** 系统清掉对应旧缓存或写入新的真实缓存
- **AND** 后续页面查询不因旧缓存继续返回被替换的真实数据

#### Scenario: 过期真实基础资料和变化快信息
- **GIVEN** 页面只能取得未超过最大陈旧时间的过期真实快照
- **WHEN** 页面展示影片或影院基础资料
- **THEN** 系统返回该快照并标记“数据已过期，仅供参考”
- **AND** 热映、待映等变化快的信息不得表述为当前信息，场次、价格、库存仍只由 A 决定

### Requirement: 实时调用必须受控且可追溯
系统 SHALL 在调用真实 Provider 前执行输入校验和本地限流，并使用已确认的连接、读取超时以及最多一次仅针对连接失败或 5xx 的短重试。上游 429、不可重试 4xx、字段校验失败和重复同步请求 MUST 不进行盲目重试。同步或调用记录 MUST 记录来源、资源类型、结果、耗时、数据时间、字段质量摘要和降级层级，但 MUST 不记录 Key、完整原始响应、影评正文或个人数据。

#### Scenario: Provider 返回 429
- **GIVEN** Provider 或本地限流拒绝本次请求
- **WHEN** 系统处理该拒绝
- **THEN** 系统不得立即重试或扩大请求频率
- **AND** 系统继续执行既有回退顺序并记录不含 Key 的限流结果

### Requirement: 真实内容不得越过内容和票务边界
真实 Provider 只可提供影片和影院基础信息。系统 MUST NOT 接入或保存影评正文，MUST NOT 根据 Provider 响应生成、更新或推断影厅、场次、价格、库存、座位、订单、支付或退款事实。A 查询影院摘要时 MUST 继续只使用 `ContentSummaryQueryPort.findCinemaSummaries(Set<Long>)` 返回的 `CinemaSummary(cinemaId/name/area/source/dataTime/expiresAt/expired)`；本 change MUST NOT 新增影片摘要或影片查询方法。B 只能通过 `RankMoviePlanTool.execute` 读取标准化 `FixedRecommendationResult`，不读取 Provider 原始响应或实现；其中存在的 `showId`、`price`、`startTime` 必须原样来自 A 的公开场次查询，B 不补全、不推断且不作为下单依据。C 的前端展示只能使用已确认的来源和时效字段。

#### Scenario: Provider 响应包含排期或影评正文
- **GIVEN** Provider 响应包含场次、价格、库存、座位信息或影评正文
- **WHEN** 系统标准化该响应
- **THEN** 系统不得持久化、缓存或向 A、B、C 返回这些字段
- **AND** 场次和交易事实仍只能由 A 的公开能力提供

#### Scenario: 前端同时展示来源、过期和降级状态
- **GIVEN** 前端收到包含 `source`、`sourceType`、`dataTime`、`expiresAt`、`isExpired`、`degraded` 和 `fallbackType` 的标准化内容结果
- **WHEN** 前端展示该内容
- **THEN** `sourceType=LIVE`、`isExpired=false`、`degraded=false` 时显示“来源：{source}”和更新时间；`isExpired=true` 时显示“数据已过期，仅供参考”
- **AND** `degraded=true` 时显示“当前为降级数据”，并按 `fallbackType=MOCK|CACHE|SNAPSHOT` 分别显示“演示数据”“缓存数据”“历史快照”
- **AND** `source` 缺失、无法识别或未通过运行时校验时显示“来源尚未验证”，且该状态与过期、降级状态分别判断并可同时展示
