## MODIFIED Requirements

### Requirement: 最小只读消息提交必须持久化已校验运行事实
系统 SHALL 在最小只读消息提交用例中保存用户消息、运行、服务端校验后的候选计划或运行计划、节点状态、尝试次数、自动跳过信息、槽位快照和结构化回复。运行步骤 MUST 只保存计划定义和状态所需字段；工具原始响应、异常对象、模型原始上下文、认证秘密、精确位置和完整第三方响应 MUST NOT 被保存。

#### Scenario: 合法推荐请求保存完成运行
- **WHEN** 当前用户向自己的会话提交具有可信校验上下文的合法只读推荐请求，且本轮得到最终 QUESTION、PLAN_CARD、MOVIE_CARD 或 ERROR 回复
- **THEN** 系统保存用户消息、运行、已校验节点快照和结构化回复
- **AND** 运行不再保持活动状态，后续消息可以创建新的运行

#### Scenario: 处理中工具结果保留运行中状态
- **WHEN** 已登记只读工具返回 `PROCESSING`，且运行计划仍有下游 `PENDING` 节点
- **THEN** 系统保存该工具节点的 `RUNNING` 状态、尝试次数和安全进度回复
- **AND** 运行保持 `RUNNING`，系统不得把下游 `PENDING` 改写为失败、释放会话占用或重新调用工具

### Requirement: 同一会话只能有一个活动运行且重复请求不得再次执行
系统 SHALL 使用数据库条件更新和唯一约束保证同一会话同一时刻最多一个 `RUNNING` 运行。新请求必须在同一短事务内插入 `agent_run`、写入用户消息并执行 `active_run_id IS NULL` 的条件更新；条件更新失败时整笔事务 MUST 回滚并返回 `409 / 206008`。相同当前用户、会话和 `clientRequestId` 的重复提交 MUST 返回既有运行及已保存结果，不得再次调用 `MinimalReadOnlyAgentService` 或 D 的只读工具；相同 `clientRequestId` 但请求摘要不同 MUST 返回 `409 / 206009`。`agent_run` MUST 对 `user_id + session_id + client_request_id` 建唯一约束。会话已有其他活动运行时，系统 MUST 返回 Agent 活动运行冲突，不创建第二个运行。

#### Scenario: 网络重试返回既有运行
- **WHEN** 同一用户对同一会话以相同 `clientRequestId` 和相同请求摘要重复提交消息
- **THEN** 系统返回第一次提交创建的 runId 和当前持久化运行快照
- **AND** 推荐工具调用次数保持为一次

#### Scenario: 并发相同请求标识返回胜者
- **WHEN** MySQL 并发提交相同用户、会话、`clientRequestId` 和请求摘要，且其中一个事务因唯一键冲突失败
- **THEN** 冲突事务回滚后在独立只读事务中读取已提交的胜者并返回相同 runId
- **AND** 不返回数据库异常，不创建第二条消息或运行，也不调用主控或工具

## ADDED Requirements

### Requirement: 请求摘要必须接受合法 Unicode 并完整返回运行消息
系统 MUST 按 Unicode 码点计算 `request_hash v1`，接受合法代理对表示的 emoji 和其他非 BMP 字符，只拒绝未配对代理字符。重复请求读取 MUST 按当前用户和 run ID 返回该运行全部已保存消息，不得先截取会话最近消息再过滤。

#### Scenario: emoji 请求可创建和重复读取
- **WHEN** 用户内容或槽位包含合法 emoji，或重复请求引用较早的运行
- **THEN** 系统生成稳定请求摘要并返回该运行全部消息快照
- **AND** 不因合法代理对抛出参数异常或返回空消息列表
