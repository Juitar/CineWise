## ADDED Requirements

### Requirement: 状态机必须只选择可安全开始的节点
系统 SHALL 接收已通过 `PlanSchemaValidator` 的运行计划及其节点状态，且仅把 `status=PENDING`、全部直接前置节点均为 `SUCCESS` 的节点作为可开始节点。对于 `CALL_TOOL` 节点，系统 MUST 再通过 `ToolRegistry` 确认目标工具已登记且为只读；非只读工具、未知工具和 `CONFIRM_ACTION` 节点 MUST NOT 被选为可开始节点。本 change 不调用工具、不创建确认动作，也不把任何节点推进到 `WAITING_CONFIRMATION`。

#### Scenario: 找到相互独立的只读节点
- **WHEN** 已校验计划中有两个没有前置节点的已登记只读 `CALL_TOOL` 节点和一个依赖其中之一的节点
- **THEN** 系统只返回前两个节点作为当前可开始节点
- **AND** 不执行工具，也不修改任一节点状态

#### Scenario: 拒绝写工具和确认节点
- **WHEN** 运行计划被构造为包含非只读 `CALL_TOOL` 节点或 `CONFIRM_ACTION` 节点
- **THEN** 系统不把这些节点返回为可开始节点
- **AND** 系统不生成 `WAITING_CONFIRMATION` 状态、不生成 `actionId`，也不调用写工具

### Requirement: 节点状态推进必须受控且保留尝试次数
系统 SHALL 只允许状态机将可开始节点从 `PENDING` 推进到 `RUNNING`，再根据执行反馈推进为 `SUCCESS` 或失败处理结果。终态 `SUCCESS`、`FAILED` 和 `SKIPPED` MUST NOT 被重新开始或改写为其他终态。每个节点 MUST 记录已开始的尝试次数；状态机只记录状态和类型化结果摘要所需的运行信息，不直接调用 A、D 的工具。

#### Scenario: 正常完成一个只读节点
- **WHEN** 一个可开始的只读节点被标记为开始并收到成功的 `ToolResult`
- **THEN** 节点状态依次为 `PENDING`、`RUNNING`、`SUCCESS`
- **AND** 尝试次数增加一次，成功节点可作为其下游节点的前置条件

#### Scenario: 拒绝重新开始终态节点
- **WHEN** 调用方尝试开始已经为 `SUCCESS`、`FAILED` 或 `SKIPPED` 的节点
- **THEN** 状态机拒绝该状态推进
- **AND** 已有节点状态、尝试次数和结果保持不变

### Requirement: 前置失败或跳过必须自动跳过全部下游节点
当节点进入 `FAILED`，或其前置节点已经是 `FAILED` 或 `SKIPPED` 时，系统 SHALL 将该失败或跳过节点的全部传递下游 `PENDING` 节点标记为 `SKIPPED`。自动跳过的节点 MUST 设置 `autoSkipped=true`、`skipReason=UPSTREAM_FAILED` 和最初阻塞该分支的 `skipSourceNodeId`；无依赖该分支的节点 MUST 保持原状态并可继续执行。

#### Scenario: 失败节点跳过多层下游但不影响独立分支
- **WHEN** 节点 A 失败，节点 B 依赖 A，节点 C 依赖 B，节点 D 与 A 无依赖关系
- **THEN** B 和 C 均变为 `SKIPPED`，并记录 `UPSTREAM_FAILED` 及来源节点 A
- **AND** D 仍保持原状态，并在满足自身前置条件时可以开始

#### Scenario: 已被跳过的前置节点阻塞下游
- **WHEN** 一个 `PENDING` 节点的前置节点已为 `SKIPPED`
- **THEN** 状态机将该节点及其未开始的下游节点标记为 `SKIPPED`
- **AND** 不把它们作为可开始节点返回

### Requirement: 只读工具失败最多自动重试一次
对于已登记只读 `CALL_TOOL` 节点，系统 SHALL 仅在节点的 `failurePolicy=RETRY_ONCE`、工具反馈 `status=FAILED` 且 `retryable=true`、并且该节点尚未重试过时，将节点从本次失败恢复为 `PENDING` 以允许同一节点再开始一次。第二次失败、不可重试失败、非 `RETRY_ONCE` 策略或非只读节点 MUST 进入 `FAILED`，随后执行下游跳过规则。状态为 `PROCESSING` 的工具反馈 MUST 保持节点为 `RUNNING`，且不消耗重试次数。

#### Scenario: 可重试的只读工具第一次失败后重新排队
- **WHEN** 已登记只读工具节点第一次执行失败，且其策略为 `RETRY_ONCE` 并返回 `retryable=true`
- **THEN** 节点回到 `PENDING`，已开始尝试次数为一次，已使用重试次数为一次
- **AND** 下一次只能再开始该同一节点一次

#### Scenario: 第二次失败后结束该节点
- **WHEN** 同一只读工具节点已使用一次重试后再次失败
- **THEN** 节点变为 `FAILED`
- **AND** 系统自动跳过其全部未开始的下游节点，不再把该节点重新排队

### Requirement: 单次运行的重规划次数不得超过两次
状态机 SHALL 在运行状态中保存重规划次数，并仅在当前次数小于 2 时批准一次新的重规划。每次批准 MUST 使计数加一；当次数已为 2 时，系统 MUST 返回拒绝结果且不得改变节点、计划版本或重规划计数。本 change 只负责额度判断和计数，不生成新计划、不替换节点、不调用模型。

#### Scenario: 前两次重规划被批准
- **WHEN** 同一运行连续请求两次重规划
- **THEN** 两次请求均被批准，重规划次数依次为 1 和 2
- **AND** 状态机不生成候选计划或调用 `ModelGateway`

#### Scenario: 第三次重规划被拒绝
- **WHEN** 同一运行的重规划次数已经为 2 后再次请求重规划
- **THEN** 状态机返回已达上限的拒绝结果
- **AND** 运行中的节点、计划版本和重规划次数保持不变
