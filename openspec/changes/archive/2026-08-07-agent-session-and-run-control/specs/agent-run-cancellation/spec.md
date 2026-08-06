## ADDED Requirements

### Requirement: 当前用户可以取消自己的运行
系统 SHALL 通过 `POST /api/v1/agent/runs/{runId}/cancel` 取消当前用户自己的 `RUNNING` 运行。系统 MUST 只按 `runId + 当前用户 ID` 查询运行；不存在或不属于当前用户时 MUST 使用现有 `404 / 206005` 资源不存在语义。取消 MUST 在事务中只将仍为 `PENDING` 的步骤改为 `SKIPPED`，并将运行改为 `CANCELLED`，清除仅仍指向该运行的 `active_run_id`。

#### Scenario: 取消正在运行的本人运行
- **WHEN** 当前用户取消自己的 `RUNNING` 运行
- **THEN** 系统保留已完成和正在执行的步骤，仅跳过取消时仍未开始的步骤
- **AND** 系统保存运行终态 `CANCELLED`、完成时间和对应的可恢复 `run.complete` 事件

### Requirement: 运行取消不得产生额外业务副作用
取消 SHALL 不回滚已完成步骤、不删除消息、运行、步骤或事件、不重新执行工具，也不得因 HTTP 超时、SSE 断开或重复请求自动重试。若存在正在执行的只读工具，系统 MUST 保持该步骤的已有运行模型，不新增强制中断机制。

#### Scenario: 取消时存在完成和运行中的步骤
- **WHEN** 当前用户取消的运行同时有 `SUCCESS`、`RUNNING` 和 `PENDING` 步骤
- **THEN** `SUCCESS` 与 `RUNNING` 步骤保持原样，只有 `PENDING` 步骤变为 `SKIPPED`
- **AND** 系统不发起新的工具调用或回滚已保存结果

### Requirement: 运行取消必须幂等并保护终态
系统 MUST 返回当前持久化运行状态。重复取消、并发取消或对 `COMPLETED`、`FAILED`、`CANCELLED` 运行取消时 MUST 不改变已保存终态、步骤、消息或事件，也 MUST 不追加第二个取消事件。

#### Scenario: 重复取消已经取消的运行
- **WHEN** 当前用户再次取消已经为 `CANCELLED` 的运行
- **THEN** 系统成功返回该运行当前的 `CANCELLED` 状态
- **AND** 不修改运行版本、不新增事件、不再次更新步骤

#### Scenario: 取消已结束运行
- **WHEN** 当前用户取消已经为 `COMPLETED` 或 `FAILED` 的运行
- **THEN** 系统成功返回当前终态
- **AND** 系统不改变已保存终态或活动会话引用

### Requirement: 取消与会话清空的并发操作保持一致
系统 MUST 以数据库条件更新和事务判断取消与清空之间的竞争；不能仅依赖 JVM 锁。任一操作完成后，会话若仍为 `ACTIVE` 且运行终态已保存，`active_run_id` MUST 已释放；会话若已 `CLEARED`，则不得保留活动运行引用。

#### Scenario: 取消与清空同一会话并发
- **WHEN** 一个请求取消运行，另一个请求同时清空所属会话
- **THEN** 清空要么收到 `409 / 206008` 后由后续请求处理，要么在取消提交并释放活动引用后成功清空
- **AND** 最终不出现悬空 `active_run_id`、运行状态倒退或重复取消事件
