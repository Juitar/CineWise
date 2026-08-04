## 1. 实现前置条件与协作确认

- [x] 1.1 D 已于 2026-08-04 核对 `d-demo-content-recommendation-baseline` 的 2.9、5.6、6.1 至 6.5 仍未勾选；内容实现提交 `20c7c1e`、`5d715f7`、`82129d1`、`ea53a1e`、`88d67eb`、`a546ce8`、`1de9291`、`40544f3`、`47cda31` 均已进入 `origin/dev`。当前 `openspec list` 仍显示基线为 29/36，与 `origin/dev` 的归档提交 `88158bd` 不一致；实现前必须复核这 7 项的最终验收记录。验证：`git merge-base --is-ancestor <commit> origin/dev` 对上述提交均返回成功；当前分支为 `feat/real-content-provider-integration`，工作区无未提交改动。
- [x] 1.2 D 已于 2026-08-04 选定 NetStart 作为学习/演示环境的影片与影院基础信息 Provider。公开文档确认其接口范围包含城市、热映/待映、电影详情、影院搜索和影院详情，并声明“仅供学习交流使用，请勿用于商业用途”；本项目据此标明第三方非官方来源，不宣称猫眼官方合作或实时官方票务数据。公开文档未说明 API Key、配额和长期字段稳定性，已登记为风险；开发/演示实现必须使用显式开关、本地保守限流、超时、一次短重试和随时关闭，正式生产/商业环境不启用。验证：已读取公开 README、城市接口、热映接口和影院搜索接口；接口返回影片/影院外部 ID 样例，未保存 Key、影评正文或完整原始响应。
- [x] 1.3 A 已于 2026-08-04 有条件确认：`ContentSummaryQueryPort` 仅保留 `findCinemaSummaries(Set<Long>)`；`CinemaSummary` 固定为 `cinemaId/name/area/source/dataTime/expiresAt/expired`，真实 Provider 接入后字段和含义不变。A 仍只通过该 Port 查询，不访问 D 的 Entity、Mapper、Repository、缓存、快照或内部查询实现。本 change 不新增 `MovieSummary` 或影片查询方法；如未来需要，先由 D 列出准确方法、字段、缺失结果和过期语义，再由受影响消费者确认。A 同时确认 Provider 不得创建、修改或推断票务事实。验证：无需新增票务 DTO 或跨模块持久化访问。
- [x] 1.4 C 已于 2026-08-04 确认展示字段和文案：继续使用 `source/sourceType/dataTime/expiresAt/isExpired/degraded/fallbackType`；LIVE、未过期、未降级时显示来源和更新时间；过期显示“数据已过期，仅供参考”；降级显示“当前为降级数据”，并按 MOCK/CACHE/SNAPSHOT 显示演示数据/缓存数据/历史快照；来源缺失、无法识别或未通过运行时校验时显示“来源尚未验证”。来源未验证、过期和降级分别判断且可同时展示。前端不读取、保存或展示 Key、原始响应或影评正文。验证：确认前不修改 C 的公共请求层或前端类型。
- [x] 1.5 B 已于 2026-08-04 确认：B 只调用 `RankMoviePlanTool.execute`，读取 D 返回的标准化 `FixedRecommendationResult`，不读取 Provider 原始响应、Provider 实现、Controller、Repository 或持久化对象，不触发内容同步、不新增或透传 Provider 私有字段。`RankMoviePlanCommand` 仅包含 `movieId/cinemaId/date/timeFrom?/timeTo?`，不接收票务或用户字段；结果中的 `showId/price/startTime` 若存在，只能由 D 从 A 的公开场次查询原样取得，B 不补全、不推断、不作为下单依据，交易仍由 A 重新校验。验证：当前无需调整代码边界。

## 2. Provider 与标准化实现

- [ ] 2.1 D 在 `content` 模块按现有分层增加 Provider 查询端口、候选 Provider Infrastructure Adapter 和显式启用检查；Key 只从被 Git 忽略的运行配置读取，日志与状态查询仅返回已配置状态。验证：模块架构测试通过，仓库、测试输出和日志样例均不含 Key。
- [ ] 2.2 D 实现影片、影院基础字段映射、最低字段校验、来源/时间/有效期封套和字段质量摘要；业务层、A、B、C 均不接收第三方 SDK 类型或原始 JSON。验证：Provider Mock 的合格响应返回标准化结果，缺失字段或非法时间响应不进入可用缓存、快照或基础内容。
- [ ] 2.3 D 实现非空 `provider + resourceType + externalId` 的幂等识别；为外部 ID 为空的记录实现隔离、冲突记录和人工复核前禁止写入规则。验证：重名影片、同名影院、空外部 ID 和候选匹配多个对象时均不自动覆盖、合并或删除已有内容。
- [ ] 2.4 D 实现实时请求的输入校验、本地限流、500ms 连接超时、1500ms 读取超时，以及仅连接失败或 5xx 可 200ms 退避重试一次的规则。验证：429、不可重试 4xx、字段校验失败和重复同步均不盲目重试，记录不含 Key 和完整原始载荷。

## 3. 缓存、快照与数据边界

- [ ] 3.1 D 将通过校验的真实影片、影院基础信息写入现有标准化缓存和快照，不修改 `demo-content-v1`、不创建第二份电影/影院种子。验证：缓存与快照只包含标准化 DTO 和来源/时间元数据，Demo 仍只作读取回退和测试夹具。
- [ ] 3.2 D 将读取顺序实现为“实时 Provider 成功 → 有效缓存 → 有效快照 → 允许陈旧快照 → `demo-content-v1` → `303004`”，保留内容基线的缓存键、TTL、最大陈旧时间和来源封套。验证：关闭 Provider、断网、缓存失败、快照过期和全部不可用时，返回层级及 `source/dataTime/expiresAt/isExpired/degraded/fallbackType` 与规格一致。
- [ ] 3.3 D 补齐同步或调用记录的来源、资源、结果、耗时、数据时间、字段质量和降级层级；不记录 Key、完整原始响应、影评正文、用户输入、位置或票务事实。验证：成功、超时、429、5xx、字段不合格和空 ID 冲突的记录均可审查且不泄露受限数据。
- [ ] 3.4 D 如确认需要新增或修改数据库持久化字段、索引、约束等表结构，先补充本 change 的字段、索引、生命周期、兼容与回滚说明并提交 A 审查。验证：A 分配 Flyway 版本、审核最终 SQL 并在 `cinewise_migration_check` 验证；纯 Provider 映射、DTO、内存质量标识或配置变更不走 Flyway，D 不自行定版本、不修改已发布迁移、不执行 Flyway。

## 4. 测试与真实环境验证

- [ ] 4.1 D 编写 Provider Mock 和契约测试，覆盖合格影片/影院、配置缺失、许可未确认、超时、连接失败、429、5xx、不可重试 4xx、字段缺失、非法时间、空外部 ID、重名和冲突匹配。验证：每种异常均不将未校验数据当作真实内容，且不会暴露 Key 或原始受限载荷。
- [ ] 4.2 D 编写缓存、快照和回退测试，覆盖实时成功写入、有效缓存、有效快照、允许陈旧快照、Redis 故障、Demo 回退和 `303004`。验证：真实 Provider 失败时现有 Demo 离线演示保持可用，过期数据不被当作当前票务事实。
- [ ] 4.3 D 编写数据质量与身份识别测试，覆盖标准化字段、来源/时效封套、非空外部 ID 幂等更新、空 ID 隔离、候选冲突不自动合并，以及 Provider 响应中影评正文、场次、价格、库存、座位和订单字段被过滤。验证：A 继续仅通过 `ContentSummaryQueryPort`，B、C 不会取得被禁止字段。
- [ ] 4.4 D 在 Provider 关闭、外网断开和受控学习 Provider 环境分别执行验证；NetStart 仅限开发/演示环境，配额未知时使用本地保守限流，不进入正式生产或商业环境。验证：记录脱敏环境信息、Provider 成功率/耗时/限流、缓存命中、快照时效、降级层级、数据质量结果和缺陷编号；不执行或改写 Flyway。
- [ ] 4.5 D 在 `backend` 执行 `mvnw.cmd verify`，执行 `openspec validate real-content-provider-integration --strict`、`git diff --check` 并核对变更范围。验证：构建、测试、架构检查、Checkstyle、SpotBugs、JaCoCo 和 OpenSpec 严格校验通过；未通过项明确到负责人和复现步骤。

## 5. 交付与发布准备

- [ ] 5.1 D、A、B、C 分别复核本 change 中的 Provider 启用材料、公开摘要兼容性、Agent 只读边界和前端来源展示；未取得确认时 Provider 保持关闭。验证：确认记录、消费者夹具、OpenAPI/展示样例和验证结果与实现一致。
- [ ] 5.2 D 准备独立实现分支的 PR 说明，列出内容基线依赖提交、Provider 启用状态、Owner 确认、验证命令、未验证项和关闭 Provider 的回退方式。验证：不包含 Key、未授权数据、无关改动或第二份内容种子；用户明确要求后才提交、推送或创建 PR。
