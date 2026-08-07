## ADDED Requirements

### Requirement: 受控槽位执行出行建议 Tool
系统 SHALL 将已注册的 `getTravelAdvice` 作为 `readOnly=true`、无需确认的生产 Tool。执行 Adapter MUST 只调用 D 的 `GetTravelAdviceTool.execute(ToolContext, GetTravelAdviceCommand)`，命令任务号 MUST 只来自当前执行节点单一的 `SLOT/travelTaskId` 引用；Adapter 不得接收 `userId`、调用 D 的 Controller/Repository/Entity/Mapper 或解析内部 JSON。

#### Scenario: 受控任务槽位查询成功
- **WHEN** 当前运行节点使用已确认的 `travelTaskId` 槽位且 D Tool 成功返回结构化结果
- **THEN** Adapter 调用一次 D 的公开 Tool，Supervisor 将成功结果交给出行建议卡片映射，并且不产生确认动作或写操作

#### Scenario: 模型伪造任务输入被拒绝
- **WHEN** 节点缺少槽位、包含多个输入引用，或任务号引用不是 `SLOT/travelTaskId`
- **THEN** Adapter 在调用 D 前返回现有安全参数错误，且不读取或泄露任何出行任务

#### Scenario: D Tool 查询失败
- **WHEN** D Tool 返回任务不存在、无权限、过期查询失败或其他失败结果
- **THEN** Supervisor 使用既有安全错误映射，不泄露任务归属、内部异常、原始 JSON 或其他用户信息
