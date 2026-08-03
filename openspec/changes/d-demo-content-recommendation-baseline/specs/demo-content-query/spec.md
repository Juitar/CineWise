## Purpose

为影片、影院页面和推荐模块提供不依赖外网的稳定内容查询，并让每条内容都能明确说明来源、更新时间、有效期以及是否发生缓存或快照回退。

## ADDED Requirements

### Requirement: Demo 内容必须可重复
系统 SHALL 使用带版本号的固定 Demo 数据提供影片和影院内容；相同数据版本、相同查询条件和相同业务时钟 MUST 返回相同的业务字段和排列顺序。

#### Scenario: 重复查询固定影片
- **GIVEN** Demo 数据版本、查询条件和业务时钟均未变化
- **WHEN** 调用方重复查询影片列表
- **THEN** 系统返回相同的影片 ID、内容字段和排列顺序
- **AND** 每条结果明确标识为 Demo 数据

#### Scenario: 重复查询固定影院
- **GIVEN** Demo 数据版本、城市条件和业务时钟均未变化
- **WHEN** 调用方重复查询影院列表
- **THEN** 系统返回相同的影院 ID、内容字段和排列顺序
- **AND** 每条结果明确标识为 Demo 数据

### Requirement: 影片和影院固定数据必须由 D 维护且身份稳定
系统 SHALL 使用 D 维护的唯一 `demo-content-v1` 影片、影院数据作为数据库初始化基线。该数据沿用 A 演示阶段已有的 10 部影片、4 家影院；A 的票务种子只能消费内容种子返回的 `ContentSeedCatalog`。D 的 Demo 内容、测试夹具或回退数据 MUST 使用同一数据版本和来源 ID，MUST NOT 向数据库插入第二套影片、影院记录。

#### Scenario: 初始化后读取 Demo 内容
- **GIVEN** 内容种子已使用 D 维护的共享固定数据初始化影片和影院
- **WHEN** D 查询影片或影院，或者读取 Demo 回退数据
- **THEN** 返回的 `movieId` 和 `cinemaId` 必须与本次数据库初始化产生或查回的实际 ID 一致
- **AND** 不得新增同名但 ID 不同的影片或影院记录

#### Scenario: D 的 Demo 数据只用于回退或测试
- **GIVEN** 数据库中已经存在共享固定影片和影院
- **WHEN** D 加载 `demo-content-v1` 数据
- **THEN** 系统仅将其用于回退查询或测试夹具
- **AND** 不得以该数据再次写入影片或影院表

### Requirement: 来源 ID 为空时不得误用唯一键
`source_movie_id` 和 `source_cinema_id` MAY 为空，但固定 Mock 数据 MUST 非空。来源 ID 为空的记录 MUST NOT 依赖 `source + source_*_id` 作为幂等更新、去重或同一对象判定依据。

#### Scenario: 处理来源 ID 为空的未来内容
- **GIVEN** 未来 Provider 返回来源 ID 为空的影片或影院
- **WHEN** 内容模块准备保存或更新该记录
- **THEN** 系统不得把 `source + source_*_id` 当作唯一业务键
- **AND** 系统在该 Provider 的专用身份识别规则确定前不得将其写入标准内容快照

#### Scenario: 初始化固定 Mock 数据
- **GIVEN** 内容种子初始化 D 维护的共享固定影片和影院数据
- **WHEN** 写入 Mock 影片或影院
- **THEN** `source_movie_id` 或 `source_cinema_id` 必须非空
- **AND** 可以使用 `source + source_*_id` 防止重复初始化

### Requirement: 内容查询必须使用统一内部结果
影片和影院查询 SHALL 返回标准化内容，不得向调用方暴露 Provider 原始字段。每次查询结果 MUST 包含 `source`、`dataTime`、`expiresAt`、`isExpired`、`degraded` 和 `fallbackType`。

#### Scenario: 查询有效影片内容
- **GIVEN** 查询条件合法且存在匹配的有效影片
- **WHEN** 调用方查询影片
- **THEN** 系统返回标准化影片内容
- **AND** `isExpired=false`
- **AND** 来源、数据时间和过期时间均不为空

#### Scenario: 查询有效影院内容
- **GIVEN** 城市条件合法且存在匹配的有效影院
- **WHEN** 调用方查询影院
- **THEN** 系统返回标准化影院内容
- **AND** `isExpired=false`
- **AND** 来源、数据时间和过期时间均不为空

#### Scenario: 查询条件非法
- **GIVEN** 城市、关键字或资源 ID 不符合长度或格式要求
- **WHEN** 调用方发起内容查询
- **THEN** 系统拒绝查询并返回稳定的参数错误
- **AND** 不读取 Provider、不写缓存和快照

### Requirement: 内容查询必须按固定顺序回退
系统 SHALL 按“有效缓存、有效快照、允许使用的过期快照、Demo 固定数据、不可用”的顺序选择结果，不得由调用方或模型临时改变顺序。

#### Scenario: 命中有效缓存
- **GIVEN** 存在与查询条件匹配且未过期的缓存
- **WHEN** 调用方查询内容
- **THEN** 系统返回缓存内容
- **AND** 返回 `degraded=true` 和 `fallbackType=CACHE`

#### Scenario: 缓存不可用但有效快照存在
- **GIVEN** 缓存缺失或不可用且存在未过期快照
- **WHEN** 调用方查询内容
- **THEN** 系统返回快照内容
- **AND** 返回 `degraded=true` 和 `fallbackType=SNAPSHOT`

#### Scenario: 只存在允许使用的过期快照
- **GIVEN** 实时内容、有效缓存和有效快照均不可用
- **AND** 过期快照尚未超过该资源允许的最大陈旧时间
- **WHEN** 调用方查询内容
- **THEN** 系统可以返回该快照用于只读展示
- **AND** 返回 `isExpired=true`、`degraded=true` 和 `fallbackType=SNAPSHOT`

#### Scenario: 所有内容来源均不可用
- **GIVEN** 缓存、快照和 Demo 固定数据均无可用结果
- **WHEN** 调用方查询内容
- **THEN** 系统返回 `303004`
- **AND** 不生成或补造影片、影院、场次、价格和库存

### Requirement: 过期内容不得作为新的可购事实
过期影片或影院快照 SHALL 仅用于标明时间的只读展示，MUST NOT 直接成为新推荐中的当前可购场次依据。

#### Scenario: 推荐读取到过期内容
- **GIVEN** 内容结果的 `expiresAt` 早于当前业务时间
- **WHEN** 推荐模块读取该结果
- **THEN** 推荐模块不得将其作为当前可购事实
- **AND** 调用结果必须保留 `isExpired=true` 和原始来源时间

### Requirement: 内容快照和同步日志必须保留正确的时间与统计关系
`external_data_snapshot` MUST 包含 `version`、`create_time` 和 `update_time`；`expire_time` 可以为空，但不为空时 MUST 不早于 `data_time`。`data_sync_log` MUST 包含 `version`、`create_time` 和 `update_time`，并建立按 `create_time` 清理满 180 天日志的索引。

同步状态只允许 `RUNNING`、`SUCCESS`、`FAILED`、`PARTIAL`。三个统计数不得为负；处理中成功数与失败数之和不得超过总数。结束状态的 `finished_at` MUST 不早于 `started_at`，且统计数必须与状态一致：`SUCCESS` 全部成功、`FAILED` 全部失败、`PARTIAL` 同时存在成功和失败记录；结束状态的成功数和失败数之和必须等于总数。

#### Scenario: 写入过期时间早于数据时间的快照
- **GIVEN** 快照的 `expire_time` 不为空且早于 `data_time`
- **WHEN** 尝试写入快照
- **THEN** 数据库拒绝该记录

#### Scenario: 写入结束时间早于开始时间的同步日志
- **GIVEN** 同步状态为 `SUCCESS`、`FAILED` 或 `PARTIAL`
- **AND** `finished_at` 早于 `started_at`
- **WHEN** 尝试写入同步日志
- **THEN** 数据库拒绝该记录

#### Scenario: 写入与状态不一致的同步统计
- **GIVEN** `SUCCESS` 同步记录存在失败数，或 `FAILED` 同步记录存在成功数，或 `PARTIAL` 未同时存在成功和失败数
- **WHEN** 尝试写入同步日志
- **THEN** 数据库拒绝该记录

### Requirement: D 内容数据不得越过票务边界
影片和影院内容查询 MUST NOT 创建、修改或推断场次、票价、座位、库存和交易状态；这些信息只能来自 A 提供的公开只读能力或双方确认的固定联调数据。

#### Scenario: 内容中包含外部排期字段
- **GIVEN** Provider 或 Demo 文件包含排期、价格或库存字段
- **WHEN** 系统标准化影片或影院内容
- **THEN** 系统不得把这些字段写入票务数据或作为可购事实返回

### Requirement: 内容摘要必须通过公开查询端口提供
内容模块 SHALL 向其他模块提供 `ContentSummaryQueryPort`，用于按影片或影院 ID 查询标准化内容摘要。调用方 MUST NOT 访问 D 的 Entity、Mapper、Repository、缓存实现或内部查询用例。

#### Scenario: A 查询影院摘要
- **GIVEN** A 持有合法的 `cinemaId`
- **WHEN** A 通过 `ContentSummaryQueryPort` 查询影院摘要
- **THEN** 系统返回影院名称、来源、数据时间、过期时间和过期标识，或返回明确的未找到结果
- **AND** A 不访问 D 的持久化和缓存实现

#### Scenario: Port 尚未实现时使用临时 Demo Adapter
- **GIVEN** `ContentSummaryQueryPort` 尚未实现且 D 已确认共享 Demo 数据
- **WHEN** A 需要读取场次展示所需的内容摘要
- **THEN** A 可以使用明确标识 `MOCK/demo-seed` 的临时 Demo Adapter
- **AND** Adapter 只返回内容摘要并复用共享稳定 ID
- **AND** Adapter 不写入内容表、不维护第二套内容数据，也不生成票务事实
