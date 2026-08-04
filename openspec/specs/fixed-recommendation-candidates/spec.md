# fixed-recommendation-candidates Specification

## Purpose

为 B 的主控 Agent 和推荐联调提供第一版可预测的候选结果，在正式评分服务完成前验证工具调用、结果展示、数据来源和错误处理，同时避免伪造可购事实。

## Requirements

### Requirement: 固定候选必须可复现
推荐工具 SHALL 使用版本化固定候选数据；相同输入、相同候选版本和相同业务时钟 MUST 返回相同的候选内容和顺序。

#### Scenario: 重复调用固定推荐
- **GIVEN** 输入、固定候选版本和业务时钟均未变化
- **WHEN** B 重复调用推荐工具
- **THEN** 系统返回相同的候选 ID、方案类型和排列顺序
- **AND** 返回相同的 `algorithmVersion`

### Requirement: 推荐工具必须使用公共结果封套
推荐工具 SHALL 返回公共 `ToolResult<T>` 字段，并在业务数据中返回固定候选列表。动态事实 MUST 携带 `source`、`dataTime`、`expiresAt` 和 `isExpired`。

`ToolRegistry` 中的工具名称 MUST 为 `rankMoviePlan`，适配类为 `RankMoviePlanTool`，并使用 `RankMoviePlanCommand`。Command 必填 `movieId`、`cinemaId`、`date`；`timeFrom`、`timeTo` 必须同时为空或同时传入，且同时传入时 `timeFrom` MUST 早于 `timeTo`。Command MUST NOT 接收 `showId`、价格、座位、库存或 `userId`。公共时效字段只使用 `ToolResult.dataAt` 和 `ToolResult.expiresAt`，不得新增公共 `dataTime` 字段。

#### Scenario: 固定候选查询成功
- **GIVEN** 工具输入符合已确认的类型化结构
- **AND** 固定候选数据完整且未过期
- **WHEN** B 调用推荐工具
- **THEN** 系统返回 `status=SUCCESS`
- **AND** 返回 `retryable=false`、`replanSuggested=false` 和 `suggestedNextAction=RENDER_RESULT`
- **AND** 每个候选均携带来源和时间信息

#### Scenario: 工具输入不合法
- **GIVEN** 工具输入缺少必填字段或字段格式不合法
- **WHEN** B 调用推荐工具
- **THEN** 系统返回结构化失败结果和稳定数值错误码
- **AND** 不自行追问用户、不发布 SSE、不调用模型

### Requirement: 固定候选不得伪造可购信息
推荐工具 MUST NOT 由 D 自行生成或维护 `showId`、票价、开场时间、座位或库存。包含这些字段的候选 MUST 来自 A 提供的公开 Application 查询结果；同一应用内不得通过 HTTP 调用 Controller 获取这些数据。

#### Scenario: A 的场次数据尚未提供
- **GIVEN** D 只有影片和影院内容，没有 A 确认的场次、价格和库存数据
- **WHEN** 推荐工具生成固定候选
- **THEN** 系统只能返回不可购的影片或影院候选以及缺失原因
- **AND** 不得生成 `PLAN_CARD` 可购方案

#### Scenario: 使用 A 的场次查询结果
- **GIVEN** A 的公开 Application 查询返回可购场次、价格和开场时间
- **WHEN** 推荐工具返回可购固定候选
- **THEN** 候选必须引用该查询结果中的原始业务 ID 和事实字段
- **AND** D 不得修改、缓存为另一份固定场次，或通过 HTTP 调用本应用 Controller

### Requirement: 过期或不完整候选不得生成可购卡片
候选缺少 `showId`、`movieId`、`cinemaId`、两位小数字符串价格或开场时间，或者候选已经过期时，系统 MUST NOT 将其作为可购方案返回。

#### Scenario: 固定候选字段不完整
- **GIVEN** 固定候选缺少任一可购必填字段
- **WHEN** 推荐工具处理该候选
- **THEN** 系统排除该可购方案
- **AND** 返回空方案或明确的缺失因素

#### Scenario: 固定候选已经过期
- **GIVEN** 候选的 `expiresAt` 早于当前业务时间
- **WHEN** 推荐工具处理该候选
- **THEN** 系统不得生成可购卡片
- **AND** 结果保留数据过期信息供 B 决定下一步

### Requirement: 第一版回归数据必须可独立验证
系统 SHALL 提供固定的输入、数据版本和期望结果，用于验证内容来源、缓存/快照回退、候选顺序、过期处理和票务边界。

#### Scenario: 执行固定回归用例
- **GIVEN** 使用约定的固定时钟、Demo 数据版本和候选版本
- **WHEN** 执行第一版回归用例
- **THEN** 实际结果必须与期望的候选、来源、时间和降级标识一致
- **AND** 不得出现 D 自行生成的可购场次、价格或库存
