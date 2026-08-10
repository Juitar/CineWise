# frontend-agent-runtime-contract-compatibility Specification

## Purpose
TBD - created by archiving change frontend-agent-runtime-contract-compatibility. Update Purpose after archive.
## Requirements
### Requirement: 用户工作区必须接受等待位置运行

系统 SHALL 将 B 返回的 `WAITING_LOCATION` 解析为合法活动运行，并保留原 `runId`、`planVersion` 和 `lastEventId`。等待期间 MUST 禁止提交第二条 Agent 消息，但 MUST 保留取消原运行的入口。系统 MUST NOT 把等待位置显示为完成、失败或结果未知，也不得自动创建新运行。

#### Scenario: 断线恢复得到等待位置运行

- **WHEN** 前端按原 `runId` 查询运行，B 返回 `status=WAITING_LOCATION`
- **THEN** 工作区显示固定“等待位置”状态并禁用消息输入
- **AND** 页面保留取消原运行的入口，不重新提交原消息或建立第二条活动流

### Requirement: 历史普通消息必须接受空 payload

系统 SHALL 接受历史消息 DTO 中的 `payload=null`，并将普通用户文本恢复为只读历史消息。系统 MUST 继续拒绝字符串、数组、数字等非对象且非空的 payload。精简的历史 `QUESTION` payload 只用于展示问题文本，MUST NOT 被恢复成可重复提交的交互卡。

#### Scenario: 刷新包含空 payload 的历史会话

- **WHEN** 历史接口返回用户 `TEXT` 消息且 `payload=null`，并返回只有 `missingSlot` 的 `QUESTION`
- **THEN** 工作区恢复两条只读历史消息且不显示数据格式错误
- **AND** 历史问题不生成可再次提交的选项或确认动作

