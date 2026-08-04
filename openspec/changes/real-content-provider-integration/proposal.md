## Why

现有 `demo-content-v1` 已能在离线场景稳定提供影片和影院基础信息，但无法在取得合法数据源后更新真实内容。本变更为 D 的内容模块规划可替换的真实 Provider 接入，同时保持现有 Demo、缓存、快照与降级行为不受影响。

## What Changes

- 新增学习/演示模式下的真实影片与影院基础信息 Provider 接入规则：选定 NetStart 作为首选外部源，记录其“仅供学习交流、不得商业使用”的公开声明、接口范围和已知限制；配额、Key 和字段稳定性未公开的部分登记为风险，并限制在开发/演示环境使用。
- 新增影片、影院标准化、来源、更新时间、有效期、字段质量、外部 ID 身份识别和去重规则；Provider 原始响应不向业务调用方暴露。
- 保持并细化既有读取顺序：真实 Provider 成功后写入标准化缓存和快照；失败、超时、限流、字段不合格或未配置时继续使用既有缓存、快照和唯一 `demo-content-v1`，不得影响离线演示。
- 明确真实数据只覆盖影片和影院基础信息，不接入影评正文，不生成或覆盖场次、价格、库存、座位、订单或支付事实。
- 约束数据持久化和迁移协作：不创建第二份电影/影院种子，不绕过 `ContentSummaryQueryPort`；涉及表结构或索引时由 A 分配 Flyway 版本、审核并执行迁移，D 不自行定版本或修改已发布迁移。
- 记录实现前需要 A、B、C 确认的公开摘要接口影响、前端来源与时效展示、Agent 工具只读边界，并拆分 Provider Mock、异常、缓存、快照、数据质量和真实环境验证任务。

### 非范围

- 不在本次 change 中编写 Java、SQL、配置、测试代码，不提交、不推送。
- 不把 NetStart 宣称为猫眼官方合作或实时官方票务数据，不把学习接口用于商业服务；不抓取登录后页面或绕过验证码。正式生产或商业使用仍保持 `DemoContentProvider`，除非另有正式授权。
- 不接入影评正文、用户评论、版权受限媒体或任何个人数据。
- 不创建场次、影厅、票价、库存、座位、订单、支付或退款事实；A 的票务数据仍是这些事实的唯一提供方。
- 不新增第二份电影或影院种子，不复制 A 的票务种子，也不让其他模块访问 D 的 Entity、Mapper、Repository、缓存或快照实现。

## Capabilities

### New Capabilities

- `real-content-provider`: 在满足许可与运行门槛后，获取、标准化、校验并回退真实影片和影院基础信息。

### Modified Capabilities

无。本 change 的规则与尚未归档的 `d-demo-content-recommendation-baseline` 同时维护；真实 Provider 的新增行为集中定义在本 change 的新能力中，内容基线提交后再进入实现。

## Impact

- 计划影响 D 的 `content` 模块 Provider 适配、标准化 DTO、快照/缓存写入、同步日志、数据质量检查和回归测试；实现必须以内容基线已提交到开发基线为前置条件，并在独立 `feat/real-content-provider-integration` 分支完成。
- `ContentSummaryQueryPort` 仍是 A 获取影院摘要的唯一公开 Java Application API；现有 `findCinemaSummaries(Set<Long>)` 和 `CinemaSummary(cinemaId/name/area/source/dataTime/expiresAt/expired)` 保持不变。本 change 不新增影片摘要 Port，也不允许 A 访问 D 的持久化或缓存实现。
- C 已确认前端使用 `source/sourceType/dataTime/expiresAt/isExpired/degraded/fallbackType` 分别展示来源、时效和降级；B 已确认只通过 `RankMoviePlanTool.execute` 读取标准化 `FixedRecommendationResult`；A 已有条件确认影院摘要兼容性、票务事实边界，并仅在表结构变更时负责 Flyway 分配、审核和验证。
- 外部 Provider 的 URL、Key、配额、许可证明和原始响应都属于运行期或受控审查材料，不写入 OpenSpec、仓库、日志、缓存或快照。
