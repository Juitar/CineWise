## Why

当前后端只有公共骨架，D 负责的影片、影院内容查询和推荐工具还没有可运行的基础数据。为了让首页、推荐和后续联调在没有真实外部服务时仍能稳定开发，需要先建立可重复的 Demo 内容、快照与缓存规则、内部查询边界以及第一版固定推荐结果。

## What Changes

- 复用 A 的 `content-and-show-selection-flow` 共享固定影片、影院数据和稳定业务 ID；`DemoContentProvider` 仅作为同源回退数据或测试夹具，不再向数据库插入第二套影片、影院。
- 新增影片、影院内容快照和短期缓存行为，所有结果携带来源、数据时间、过期时间和降级信息。
- 新增 `ContentSummaryQueryPort` 作为内容模块公开只读接口，供 A 查询内容摘要；影片、影院内部查询用例仅供 D 模块实现复用。
- 新增第一版固定推荐候选结果，保证相同输入和固定时钟下结果可重复。
- 新增固定测试数据和第一版回归用例定义，覆盖正常查询、缓存、快照、过期和无数据场景。
- D 负责确认内容字段、Provider、快照缓存、推荐和测试；A 负责 Flyway 版本、最终 SQL、共享固定种子及空 MySQL 迁移执行。Java 实现和自动化测试必须在本文档经 D 确认后分阶段执行。

### 非范围

- 不接入真实内容、天气、路线或餐饮 Provider。
- 不创建或修改 A 负责的 `movie_show`、影厅、票价、座位、库存、订单和支付数据。
- 不实现推荐模型、AI 标签生成、个性化画像评分或动态权重配置。
- 不新增内部 HTTP 自调用；同一应用内只通过公开 Application API 和 DTO 协作。
- D 不自行创建或执行 Flyway 迁移；A 可以提交标明“未执行”的迁移草案，但相关 Owner 确认且 A 明确授权前不得执行。文档确认前不开始 Java 实现。

## 本次 D 确认记录（2026-08-02）

- 同意 A 统一维护 `movie`、`cinema` 的 Flyway 迁移顺序和共享固定 Mock 种子；表结构、内容语义、Provider、快照、缓存和内容业务仍由 D 负责。
- 确认 `movie` 字段：`id`、`source_movie_id`、`title`、`genres_json`、`duration_minutes`、`rating`、`source_type`、`source`、`data_time`、`expires_at`、`version`、`deleted_at`、`create_time`、`update_time`。
- 确认 `cinema` 字段：`id`、`source_cinema_id`、`name`、`city_code`、`area`、`address`、`longitude`、`latitude`、`source_type`、`source`、`data_time`、`expires_at`、`version`、`deleted_at`、`create_time`、`update_time`。
- 同意 `source_movie_id`、`source_cinema_id` 保持可空；固定 Mock 数据必须非空。为空的记录不得依赖 `source + source_*_id` 作为幂等更新或去重依据，真实 Provider 接入前另行定义其身份识别规则。
- 确认 D 提供 `ContentSummaryQueryPort`；Port 未完成前，A 可以使用经 D 确认、明确标识 `MOCK/demo-seed` 的临时 Demo Adapter。该 Adapter 仅返回内容摘要，不维护第二套影片、影院数据，也不生成场次、票价、座位或库存。
- 确认 A 不访问 D 的 Entity、Mapper 或 Repository，也不实现真实外部内容 Provider。

## Capabilities

### New Capabilities

- `demo-content-query`: 定义 Demo 影片/影院数据、快照缓存、来源标识和内部查询行为。
- `fixed-recommendation-candidates`: 定义推荐工具第一版固定候选、结果格式、数据边界和可复现回归要求。

### Modified Capabilities

无。当前仓库尚无主规格，本变更新增首批 D 规格。

## Impact

- 代码范围：`backend` 下 `content`、`recommendation` 模块；测试夹具位于对应测试目录。
- 数据范围：后续涉及 D 负责的 `movie`、`cinema`、`external_data_snapshot`、`data_sync_log`；D 确认字段和约束，A 分配 Flyway 版本、生成或审核最终 SQL 并执行空 MySQL 验证。
- 调用方：B 使用推荐工具结果；C/前端展示来源、更新时间和 Demo 标识；A 通过 `ContentSummaryQueryPort` 获取内容摘要，并提供可购场次、票价和库存的公开只读查询。
- 负责人：D 负责文档、实现和质量回归；A 负责迁移、共享固定种子和场次查询；B 审查工具输入输出；C 审查展示所需字段。

## Acceptance

- 文档明确 Demo 数据、快照、缓存、内部查询和固定推荐结果的行为。
- 固定推荐结果不会把 D 自行生成的场次、票价或库存描述成可购事实。
- 回归用例能够验证来源、时间、过期、降级和固定结果的可重复性。
- D 明确确认 proposal、spec、design、tasks 后，才进入数据库迁移阶段。
