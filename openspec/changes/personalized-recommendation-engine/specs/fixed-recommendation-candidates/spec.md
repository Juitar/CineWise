## MODIFIED Requirements

### Requirement: 推荐候选必须可复现
推荐工具 SHALL 使用已确认的算法版本、可信候选和业务时钟完成确定性排序；相同输入、相同候选集合、相同画像摘要版本和相同业务时钟 MUST 返回相同的候选 ID、方案类型和排列顺序。

#### Scenario: 重复调用推荐
- **GIVEN** 输入、候选集合、算法版本、画像摘要版本和业务时钟均未变化
- **WHEN** B 重复调用推荐工具
- **THEN** 系统返回相同的候选 ID、方案类型和排列顺序
- **AND** 返回相同的 `algorithmVersion`

### Requirement: 推荐工具必须使用公共结果封套
推荐工具 SHALL 返回公共 `ToolResult<T>` 字段，并在业务数据中返回方案列表、缺失因素、放宽建议和画像是否实际采用。动态事实 MUST 携带 `source`、`dataTime`、`expiresAt` 和 `isExpired`。

`ToolRegistry` 中的工具名称 MUST 为 `rankMoviePlan`，适配类为 `RankMoviePlanTool`，并使用经 B、D 确认的类型化命令。命令必须只接收推荐所需的当前约束和候选范围，不得接收 `showId`、价格、座位、库存或不可信的 `userId`。公共时效字段只使用 `ToolResult.dataAt` 和 `ToolResult.expiresAt`，不得新增公共 `dataTime` 字段。

#### Scenario: 推荐工具查询成功
- **GIVEN** 工具输入符合已确认的类型化结构
- **AND** 候选数据完整且未过期
- **WHEN** B 调用推荐工具
- **THEN** 系统返回 `status=SUCCESS`
- **AND** 返回 `retryable=false`、`replanSuggested=false` 和 `suggestedNextAction=RENDER_RESULT`
- **AND** 每个方案均携带来源、时间和解释证据

#### Scenario: 工具输入不合法
- **GIVEN** 工具输入缺少必填字段或字段格式不合法
- **WHEN** B 调用推荐工具
- **THEN** 系统返回结构化失败结果和稳定数值错误码
- **AND** 不自行追问用户、不发布 SSE、不调用模型
