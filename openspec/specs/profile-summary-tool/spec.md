# profile-summary-tool Specification

## Purpose
TBD - created by archiving change user-profile-management. Update Purpose after archive.
## Requirements
### Requirement: 摘要工具只能返回最小化本人画像

系统 SHALL 提供 `GetProfileSummaryTool`，该工具只能调用 D 的 Application Service，并从 B 注入的 `ToolContext` 获取当前用户身份。工具不接收 userId，不读取或写入 `agent_*` 表，不发布 SSE，不调用模型。

#### Scenario: 个性化已开启
- **GIVEN** 当前用户开启个性化且有有效标签
- **WHEN** B 为推荐任务请求画像摘要
- **THEN** 系统返回 `enabled=true`、版本、生成时间和有效标签的类型、值、极性、权重、置信度、来源及更新时间
- **AND** 返回不包含原始行为、完整对话、邮箱、精确位置或持久化实体

#### Scenario: 个性化已关闭
- **GIVEN** 当前用户关闭个性化
- **WHEN** B 或推荐模块请求摘要
- **THEN** 系统只返回 `enabled=false`
- **AND** 不查询或返回任何长期标签

### Requirement: 本期不接入长期对话偏好写入

本期系统 SHALL 不接收 B 的长期 `CONVERSATION` 标签写入。一次性的对话条件只属于本轮任务，不得写入画像；D 不负责显示确认卡片或保存 Agent 上下文。后续需要长期保存时，由 B 新建 change，并在用户确认后调用 D 的受控写入接口。

#### Scenario: 用户说“今天别太晚”
- **WHEN** B 传入本轮时间条件而没有长期保存确认
- **THEN** D 不创建或更新任何标签
- **AND** 推荐只在当前任务使用该条件

#### Scenario: 用户确认“以后不推荐恐怖片”
- **WHEN** B 在本期收到该确认
- **THEN** B 仅保留会话槽位，不调用 D 的长期画像写入
- **AND** 后续需要长期保存时另建 B change

### Requirement: 推荐使用画像时不得覆盖本轮明确要求

推荐模块 SHALL 将 `ProfileSummary` 视为可选的长期特征。当前用户明确需求、负向条件和本轮会话约束必须优先于长期标签；画像不可用时推荐必须继续按本轮输入和通用规则运行。

#### Scenario: 当前需求与长期排斥冲突
- **GIVEN** 用户长期标签排斥恐怖片
- **WHEN** 用户本轮明确要求观看恐怖片
- **THEN** 推荐允许本轮恐怖片候选进入筛选
- **AND** 不修改用户的长期标签

#### Scenario: 画像服务不可用
- **WHEN** 推荐请求摘要失败
- **THEN** 推荐继续使用本轮输入和通用特征
- **AND** 不把缺失画像伪装为用户没有偏好

### Requirement: 推荐结果必须说明是否实际使用画像

推荐模块 SHALL 在自身结果中记录 `usedProfile`，并在为 true 时只记录本次采用的标签类型、值、极性和来源作为解释证据。该结果不得复制原始行为、完整 `ProfileSummary`、会话内容或精确位置。

#### Scenario: 使用长期偏好排序
- **GIVEN** 个性化开启且存在可用标签
- **WHEN** 推荐将某个标签作为评分特征
- **THEN** 结果标记 `usedProfile=true` 并返回最小化的采用标签证据
- **AND** 用户可以知道本次参考了哪类偏好

#### Scenario: 关闭个性化后推荐
- **GIVEN** 用户关闭个性化
- **WHEN** 推荐生成方案
- **THEN** 结果标记 `usedProfile=false`
- **AND** 不读取、展示或使用任何长期标签

