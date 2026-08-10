# agent-conversation-state-and-card-contracts Specification

## Purpose
TBD - created by archiving change agent-conversation-state-and-card-contracts. Update Purpose after archive.
## Requirements
### Requirement: 已校验的对话槽位必须随本人会话保存
系统 SHALL 只在当前认证用户的 ACTIVE Agent 会话中保存已校验的 `cityCode`、`date`、`ticketCount`。下一次消息提交 MUST 从该会话读取仍有效的槽位并用于计划校验；已存在的必填槽位不得再次发出 QUESTION。系统不得保存或接受 userId、模型原文、Cookie、位置或未校验文本作为会话槽位。

#### Scenario: 用户依次回答三个必填项
- **WHEN** 用户在同一会话依次回答城市、日期和票数
- **THEN** 每个通过校验的值写入会话快照
- **AND** 随后的运行使用三个值创建计划，不再发出 QUESTION

#### Scenario: 非法或过期回答
- **WHEN** 回答不能通过该槽位校验，或会话已清空、到期、取消或不再 ACTIVE
- **THEN** 系统不写入该非法值，也不复用失效会话的槽位
- **AND** 保持既有会话/运行错误和生命周期行为

### Requirement: C 可以直接使用受控卡片 DTO 渲染
系统 SHALL 将 QUESTION、PLAN_CARD、BUSINESS_INTENT、确认卡和确认结果映射为正式 Agent 卡片 DTO。每个 SSE 卡片 MUST 保持既有 `eventId`、`eventType`、`runId` 和 `payload` 结构；历史消息中的 `runId` MUST 是字符串 UUID。映射不得从模型文本推断字段，也不得改变既有 card payload 字段。

#### Scenario: C 恢复确认结果
- **WHEN** C 读取历史消息或运行事件中的确认卡/结果
- **THEN** C 可仅用 `eventType + payload` 渲染卡片，并用字符串 `runId` 查询运行状态
- **AND** 不查询历史消息补字段、不创建额外 run 或 SSE

### Requirement: B 自有会话列变更必须可控
系统 SHALL 使用 A 分配的 V020 为 `agent_session` 增加可恢复槽位 JSON 列，默认空对象且兼容既有会话。该列 MUST 以 `CHECK (JSON_TYPE(slot_snapshot_json) = 'OBJECT')` 限制顶层为对象；空对象仅表示旧会话或未收集槽位。系统只保存已校验、规范化的 `cityCode`、`date`、`ticketCount`；更新 MUST 使用 `id + userId + version` 的 CAS 并递增版本。读取旧快照后缺失槽位 MUST 继续 QUESTION；会话到期时槽位按既有 30 天清理规则随会话删除。不得修改 V008、不得创建外键或 JSON 索引，也不得修改任何 A/C/D 表；执行到共享数据库前仍须经 A 静态审查批准。

#### Scenario: 旧会话读取
- **WHEN** 已有会话的槽位列为默认空对象
- **THEN** 系统按无已确认槽位处理并依次追问
- **AND** 不因 JSON 为空使会话读取失败

