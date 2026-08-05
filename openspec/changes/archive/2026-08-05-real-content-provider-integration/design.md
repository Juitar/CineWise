## Context

见 `proposal.md`。`d-demo-content-recommendation-baseline` 已实现唯一 `demo-content-v1`、标准化内容结果、Redis 缓存、MySQL 快照和 `ContentSummaryQueryPort`，但其任务 2.9、5.6、6.1 至 6.5 尚未完成。本 change 只为真实影片和影院基础信息制定后续实现方案；实际代码必须等待内容基线对应提交已进入开发基线，并使用独立分支。

## Goals / Non-Goals

**Goals:**

- 把第三方字段隔离在 D 的 Provider 适配层，向内容、推荐、A、B、C 返回与 Demo 相同的标准化内容结果。
- 通过许可、配置和契约测试门槛避免未授权或字段不明的数据源进入运行环境。
- 通过每天一次的受控同步更新真实快照和真实缓存，使页面查询不访问外部服务，网络失败时仍能稳定回退到最近真实数据或 `demo-content-v1`。
- 使数据来源、时间、有效期、降级原因和质量异常可以核查，同时不泄露 Key、原始载荷或受限正文。

**Non-Goals:**

- 不改变现有 Demo 种子、缓存键、缓存 TTL、最大陈旧时间、`303004` 含义或 `ContentSummaryQueryPort.findCinemaSummaries(Set<Long>)` 的影院摘要职责；本 change 不新增影片摘要 Port。若实际需要变更，先列明方法、字段、缺失结果和过期语义，再由受影响消费者在新 OpenSpec 确认。
- 不实现影评正文、海报版权处理、场次/价格/库存/座位/交易数据，亦不通过 HTTP 调用本应用 Controller。
- 不保存 Key、创建 Flyway 文件或新增数据表；NetStart 的公开学习用途说明仅用于开发/演示规划，不等于生产或商业授权。

## Decisions

### 1. 以基线提交和独立分支作为实现前置条件

实现前先核对 `d-demo-content-recommendation-baseline` 的内容实现、测试和迁移证据已由对应提交进入目标开发基线；之后从该基线创建 `feat/real-content-provider-integration`。未满足前置条件时只可更新本 change 的规划，不得把真实 Provider 代码混入内容基线未提交改动。

不采用在内容基线分支并行修改同一内容查询流程的方式，因为会让缓存、快照和 Demo 回退的责任难以区分，也无法独立验证。

### 2. Provider 通过 D 的端口和标准化封套进入业务层

实现时在 `content` 模块的 Infrastructure 层为候选供应商建立适配器；Application Service 只依赖 D 定义的 Provider 查询端口和标准化影片、影院 DTO。适配器负责鉴权请求、字段映射、最小字段校验和错误分类，不能把第三方 SDK 类型、原始 JSON 或 Key 传出该层。标准化结果继续带 `source`、`sourceType`、`dataTime`、`expiresAt`、`isExpired`、`degraded`、`fallbackType`。

不把 Provider 逻辑放进 Controller、A 的票务模块、B 的 Tool Adapter 或 C 的前端，因为这些调用方不应承担第三方字段变化和密钥风险。

### 3. 学习模式采用三道门：用途声明、运行配置、契约验证

本次将 NetStart 明确限定为学习/演示来源。公开材料为：来源 <https://apis.netstart.cn/maoyan/>，访问日期 2026-08-04，文档名称“猫眼(M站)API接口文档”，页面声明“本项目仅供学习交流使用，请勿用于商业用途。”D 记录该公开说明、接口范围、字段映射和停用条件；公开文档未说明的 Key、配额和服务稳定性登记为未知风险，不伪造确认结果。Key 只通过被 Git 忽略的运行配置注入；无 Key 要求时也必须保持显式启用开关、本地保守限流、超时和随时关闭能力。Provider Mock 必须覆盖成功、超时、429、5xx、字段缺失、空外部 ID、重复内容和配置缺失。契约测试、Mock 异常测试、Owner 确认和学习用途文案完成后，才可在开发/演示环境打开；正式生产或商业环境不打开。

不采用“发现 Key 就自动启用”或把样例/Key 写进资源文件的方式；前者可能绕过许可与配额确认，后者会泄露运行凭据。

### 4. 以来源身份键优先，空 ID 隔离处理

非空外部 ID 使用 `provider + resourceType + externalId` 进行 Provider 内去重和幂等更新；保存前还要校验来源、资源类型和时间。空 ID 的记录进入隔离质量流程：仅在该 Provider 已确认稳定候选键、规范化规则、冲突阈值和人工复核责任后，才生成待确认映射；候选匹配不自动覆盖已有电影或影院。名称、地址、坐标等只能作为候选依据，不能单独视为全局唯一键。

不采用空字符串补位或“标题/名称相同即同一对象”，因为重名影片、连锁影院和地址格式变化会污染已有内容。

### 5. 每日同步写入真实数据，页面读取保持缓存优先

每天定时同步一次：NetStart → 标准化 → 更新真实快照 → 清掉旧缓存或写入新的真实缓存。页面查询固定为“真实缓存 → 真实快照 → 未超过最大陈旧时间的过期真实快照 → 唯一 `demo-content-v1` → 无数据”，不在用户请求中调用 NetStart。缓存或快照只保存标准化 DTO 与必要的来源/时间元数据，不保存原始 Provider 载荷、Key、影评正文、用户输入、定位或票务事实。固定 Demo 仅做读取回退和测试夹具：不更新、不写入电影/影院表、不创建第二份种子，也不进入缓存；后续必须移除当前 `ContentQueryService` 从 Demo 得到结果后写缓存的行为。

影片、影院的过期真实快照可以继续展示，但必须显示“数据已过期，仅供参考”。热映、待映等变化快的信息过期后不得表述为当前信息；场次、价格、库存仍只由 A 的公开能力决定。这样既保留最近可验证的基础资料，也不让真实 Provider 覆盖 A 的场次或种子数据。

### 6. NetStart 使用本 change 新增的默认配置

NetStart 使用本 change 新增的默认配置，不复用现有内容参数，也不代表 NetStart 官方配额：`netstart.enabled=false`、每日同步时间、连接超时 500ms、读取超时 1500ms、本地限流 10 req/min、重试次数 1 次和退避时间 200ms。每天同步一次的调用量很低，10 req/min 只用于本地保护 NetStart；仅连接错误或 5xx 可按退避时间重试一次，429、校验失败和不可重试 4xx 不重试。调用和同步使用现有 `data_sync_log` 记录来源、资源、结果、耗时、数据时间和质量摘要，异常日志脱敏。只有新增或修改数据库持久化字段、索引、约束等表结构时，D 才先定义生命周期并向 A 提交迁移申请；A 分配版本、审核 SQL 并在空 MySQL 验证。纯 Provider 映射、DTO、内存质量标识或配置变化不进入 Flyway。

### 6.1 开发验证的单次启动同步

默认 `netstart.enabled=false`、`netstart.sync-on-startup=false`，Spring Boot 的环境变量松散绑定分别对应 `CINEWISE_CONTENT_NETSTART_ENABLED`、`CINEWISE_CONTENT_NETSTART_SYNC_ON_STARTUP`。开发或演示环境同时显式设置为 true 时，`NetStartStartupSyncRunner` 在应用启动完成后仅调用一次 `ContentSyncService.synchronizeDailyContent()`；它不新增 REST 刷新接口，也不绕过 Provider 的 dev/demo 校验、限流、超时、身份隔离、事务和审计规则。

验证窗口可设置 `CINEWISE_SCHEDULING_ENABLED=false`，使公共 `@EnableScheduling` 配置不注册任何自动任务；一次性启动同步不依赖调度器，仍可执行。窗口结束后移除两个 NetStart 开关并恢复调度默认值。该配置只控制触发时机，不授权连接共享数据库、Redis 或执行 Flyway；真实环境仍须先取得 A 的独立环境和时间确认。

### 7. 跨模块消费者保持只读和兼容

A 继续只通过 `ContentSummaryQueryPort.findCinemaSummaries(Set<Long>)` 获取标准化影院摘要；现有 `CinemaSummary` 字段和含义不变，本 change 不新增影片摘要。C 按确认规则展示 LIVE 来源与更新时间、过期、降级、回退类型和“来源尚未验证”，不读取 Provider Key、原始内容或影评正文。B 只通过 `RankMoviePlanTool.execute` 读取标准化 `FixedRecommendationResult`，不触发同步、不读取 Provider 原始数据；其中的场次事实只能原样来自 A 的公开查询，B 不补全、不推断且不作为下单依据。Provider 不提供场次、价格、库存、座位或订单信息。

## Risks / Trade-offs

- [NetStart 仅声明学习用途，Key/配额和字段稳定性未公开] → 只在开发/演示环境使用，标明第三方非官方来源，以 10 req/min 本地限流和关闭开关保护调用；不用于商业或生产，不以抓取或临时 Key 绕过。
- [第三方字段变化或返回不完整] → 适配层校验后隔离无效记录，回退至缓存、快照或 Demo，并以契约测试发现变化。
- [外部 ID 为空或候选匹配歧义] → 不自动合并或覆盖，等待人工复核，避免污染现有内容。
- [Provider 持续失败、超时或 429] → 限流、一次短重试和固定回退顺序，保证离线演示不依赖外网。
- [真实内容被误认为票务事实] → DTO、接口和前端文案只表述基础信息；A 的公开场次能力仍是唯一票务来源。
- [需要新增或修改表结构] → 先补 OpenSpec 数据生命周期和兼容说明，由 A 分配 Flyway 版本、审核并验证；纯 Provider 映射、DTO、内存质量标识或配置变更不走迁移，也不修改发布迁移。

## Migration Plan

1. D 核对内容基线未完成项完成情况及其实现提交已进入目标开发基线；未满足时停止在本 change 中实现。
2. D 已确认 A、B、C 已完成公开摘要兼容性、票务边界、前端展示与 Agent 只读边界确认；私下确认记录不上传。D 记录 NetStart 的公开学习用途、接口范围、已知未知项和停用条件。
3. 从内容基线稳定提交创建 `feat/real-content-provider-integration`，实现 Provider Mock、标准化、每日同步、缓存/快照写入与异常分类，再补数据质量测试。
4. 若新增表、字段或索引，D 提交迁移材料给 A；A 分配版本、审核最终 SQL 并在 `cinewise_migration_check` 完成验证。D 不创建或执行迁移。
5. 先在 Provider 关闭和外网断开条件验证 Demo 回退，再在受控真实环境验证限流、超时、缓存、快照、数据质量和来源展示；未通过时关闭 Provider，应用继续使用既有来源。
6. 发布时保持可一键关闭真实 Provider；回退仅关闭 Provider 并保留已验证的缓存、快照和 Demo，若已发布迁移有问题只新增向前修复迁移。

## Open Questions

- NetStart 的每日配额、每分钟限制、长期字段稳定性和服务协议未在公开文档中说明；实现前只需完成开发/演示本地限流和关闭策略，正式生产/商业接入仍需另行取得正式授权。
- 若某 Provider 长期返回空外部 ID，候选键、冲突阈值、人工复核入口和映射生命周期尚未确定；在确定前不保存此类记录。
