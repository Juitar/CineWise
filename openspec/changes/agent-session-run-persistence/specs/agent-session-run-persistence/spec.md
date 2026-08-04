## ADDED Requirements

### Requirement: Agent 会话和运行必须由当前用户隔离保存
系统 SHALL 保存 B 所有的 Agent 会话、运行、消息和运行步骤。会话与运行必须关联 C 的认证用户 ID；所有读取、继续提交或写入会话的 Application 用例 MUST 仅使用 `CurrentUserAccessor` 获取当前用户，并拒绝访问不属于当前用户的会话或运行。系统 MUST NOT 从请求、模型计划、工具 Command 或回复事实取得用户 ID。`agent_session.active_run_id` MUST 为可空正数内部运行 ID，逻辑关联 `agent_run.id`，并且不得以对外 runId 字符串代替内部关联。

#### Scenario: 当前用户创建并读取自己的会话
- **WHEN** 已认证用户通过 Application 用例创建会话并随后查询该会话
- **THEN** 系统保存该用户的会话及其最小摘要
- **AND** 查询结果只包含该用户自己的会话、运行、结构化消息和步骤快照

#### Scenario: 用户访问其他人的会话被拒绝
- **WHEN** 已认证用户请求不属于自己的 sessionId 或 runId
- **THEN** 系统返回稳定的会话或运行不存在结果
- **AND** 不返回会话摘要、消息、槽位、计划或工具结果

### Requirement: 最小只读消息提交必须持久化已校验运行事实
系统 SHALL 在最小只读消息提交用例中保存用户消息、运行、服务端校验后的候选计划或运行计划、节点状态、尝试次数、自动跳过信息、槽位快照和结构化回复。运行步骤 MUST 只保存计划定义和状态所需字段；工具原始响应、异常对象、模型原始上下文、认证秘密、精确位置和完整第三方响应 MUST NOT 被保存。

#### Scenario: 合法推荐请求保存完成运行
- **WHEN** 当前用户向自己的会话提交具有可信校验上下文的合法只读推荐请求，且本轮得到最终 QUESTION、PLAN_CARD、MOVIE_CARD 或 ERROR 回复
- **THEN** 系统保存用户消息、运行、已校验节点快照和结构化回复
- **AND** 运行不再保持活动状态，后续消息可以创建新的运行

#### Scenario: 处理中工具结果保留运行中状态
- **WHEN** 已登记只读工具返回 `PROCESSING`
- **THEN** 系统保存该节点的 `RUNNING` 状态、尝试次数和安全进度回复
- **AND** 运行保持 `RUNNING`，系统不得把结果未知改写为失败或重新调用工具

### Requirement: 同一会话只能有一个活动运行且重复请求不得再次执行
系统 SHALL 使用数据库条件更新和唯一约束保证同一会话同一时刻最多一个 `RUNNING` 运行。新请求必须在同一短事务内插入 `agent_run`、写入用户消息并执行 `active_run_id IS NULL` 的条件更新；条件更新失败时整笔事务 MUST 回滚并返回 `409 / 206008`。相同当前用户、会话和 `clientRequestId` 的重复提交 MUST 返回既有运行及已保存结果，不得再次调用 `MinimalReadOnlyAgentService` 或 D 的只读工具；相同 `clientRequestId` 但请求摘要不同 MUST 被拒绝。`agent_run` MUST 对 `user_id + session_id + client_request_id` 建唯一约束。会话已有其他活动运行时，系统 MUST 返回 Agent 活动运行冲突，不创建第二个运行。

#### Scenario: 网络重试返回既有运行
- **WHEN** 同一用户对同一会话以相同 `clientRequestId` 和相同请求摘要重复提交消息
- **THEN** 系统返回第一次提交创建的 runId 和当前持久化运行快照
- **AND** 推荐工具调用次数保持为一次

#### Scenario: 活动运行阻止另一条新消息
- **WHEN** 会话存在一个 `RUNNING` 运行，用户以不同 `clientRequestId` 提交另一条消息
- **THEN** 系统返回稳定的活动运行冲突
- **AND** 不创建用户消息、运行、计划节点或新的工具调用

#### Scenario: 并发新请求只有一个占用会话
- **WHEN** 同一用户在同一会话并发提交两个不同的 clientRequestId，且会话初始没有活动运行
- **THEN** 只有一个事务成功插入运行并将 `active_run_id` 设为该运行内部 ID
- **AND** 另一个事务整体回滚并返回 `409 / 206008`

### Requirement: 运行占用和只读工具执行必须分开事务处理
系统 SHALL 在短事务内完成会话归属检查、重复请求查询、活动运行占用和初始运行事实保存。`MinimalReadOnlyAgentService`、`RankMoviePlanExecutionAdapter` 以及 D 的 `RankMoviePlanTool.execute` MUST 在该事务之外执行；工具完成后系统 MUST 在新的短事务内保存最终运行、步骤和结构化回复。运行进入终态时，系统 MUST 只通过 `WHERE active_run_id = 当前内部 runId` 的条件更新清空会话占用；旧运行更新不到记录时不得清空新运行。工具调用期间发生异常时，系统 MUST 将已有运行保存为可诊断失败状态并释放会话活动占用；不得通过回滚初始运行来伪造该请求从未发生。

#### Scenario: 下游工具失败不占用数据库事务
- **WHEN** 已保存的运行在事务外调用只读工具时发生预期失败
- **THEN** 系统在新的事务中保存失败节点和安全 ERROR 回复，并释放会话活动占用
- **AND** 数据库事务不覆盖工具调用的网络等待时间

#### Scenario: 旧运行不能释放新运行占用
- **WHEN** 旧运行已终态但会话的 `active_run_id` 已被后续运行占用
- **THEN** 旧运行的条件清空更新影响行数为零
- **AND** 后续运行继续保持会话活动占用

### Requirement: Agent 表迁移必须经过 A 的版本分配和授权
系统 SHALL 在本 Change 中记录四张 Agent 表的完整字段、正数和状态 CHECK、唯一键、查询索引、`expire_at` 索引、30 天清理顺序和兼容方案。V008 只是候选版本；在 Change 推送至可审查分支、总体设计同步且 A 正式分配版本并授权前，B MUST NOT 创建可执行 Flyway 脚本、启用迁移或修改共享数据库。获授权后迁移 MUST 使用 B 自有的 `agent_session`、`agent_run`、`agent_message` 和 `agent_run_step` 表，不建立物理外键，并使用全局 ID、`DATETIME(3)`、必要索引和 30 天清理字段。本 Change MUST NOT 创建 `agent_event`、`agent_action`、`agent_feedback` 或 `agent_tool_call`。

#### Scenario: 未获得 A 授权时停止迁移实现
- **WHEN** Change 规划已完成但 A 尚未分配迁移版本或授权执行
- **THEN** B 可以保留数据模型、Repository 接口和测试设计
- **AND** 不创建或执行 Flyway 脚本，不连接共享数据库

#### Scenario: A 授权后生成受控迁移
- **WHEN** A 已确认表字段、索引、生命周期并分配迁移版本
- **THEN** B 使用该版本创建仅包含 Agent 表的迁移脚本和空 MySQL 验证
- **AND** 迁移不修改 A、C 或 D 拥有的表

### Requirement: 最小四表字段表必须是唯一迁移依据
系统 SHALL 以 Agent 详细设计第 5.2.1 节的最小四表字段表作为本 Change 的唯一字段、类型、长度、可空性、默认值、CHECK、唯一键和索引依据。该表冻结 `agent_session`、`agent_run`、`agent_message`、`agent_run_step` 的状态、角色、消息类型、终态时间和组合关系。详细设计的未来完整模型 MUST 标明不适用于本次迁移；A 最终分配 V007 或 V008 时不得因版本不同改变该字段表。

#### Scenario: 迁移实现只采用最小四表字段表
- **WHEN** A 已正式分配本 Change 的迁移版本并授权实现
- **THEN** B 只按最小四表字段表创建迁移和持久化映射
- **AND** 不从未来 `agent_event`、`agent_action`、`agent_feedback` 或 `agent_tool_call` 模型复制字段

### Requirement: 步骤推进必须使用版本 CAS，运行恢复不得自动重放工具
系统 SHALL 为 `agent_run_step` 保存非负 `version`，并以版本 CAS 加预期状态条件推进步骤。`PROCESSING` 和进程崩溃造成的遗留节点 MUST 保持可诊断的 `RUNNING + recovery_pending=true`，但 V007/V008 的恢复器 MUST NOT 自动调用工具；运行超过 30 秒或启动扫描到遗留运行时，恢复器只能条件更新为失败、保存安全错误并以当前内部 run ID 条件清空活动会话。到期清理 MUST 只删除终态记录，并且不得留下 `active_run_id` 悬空关联。

#### Scenario: 旧步骤更新不会覆盖新状态
- **WHEN** 两个调度器以相同步骤版本尝试推进节点
- **THEN** 只有一个 `id + version + status` 条件更新成功并递增版本
- **AND** 另一个调度器重新读取当前状态，不覆盖已保存结果

#### Scenario: 崩溃运行超时后安全结束
- **WHEN** 运行超过 30 秒或启动扫描发现遗留 `RUNNING` 运行
- **THEN** 恢复器不调用任何工具，将未完成只读步骤和运行条件更新为失败
- **AND** 只有会话仍指向该内部 run ID 时才清空 `active_run_id`
