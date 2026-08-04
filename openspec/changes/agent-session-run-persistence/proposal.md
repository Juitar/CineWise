## Why

当前 B 只能在一次同步调用中保留候选计划、节点状态和结构化回复；请求结束后没有会话、运行、消息或步骤事实可查询。因此 SSE 续传、运行恢复、确认动作和安全的重复请求处理都没有可依赖的服务端数据基础。

本 Change 先把最小只读 Agent 请求落入 B 自有的会话和运行存储，让后续能力能基于已校验、可归属的数据继续实现，而不是复用模型原文或内存状态。

## What Changes

- 新增 B 所有的 Agent 会话、运行、消息和运行步骤持久化模型，以及对应 MyBatis Mapper/Repository 和 Application Service。
- 新增受认证用户约束的会话创建、读取和最小只读消息提交用例：只允许当前用户访问自己的会话和运行；同一会话同一时刻只允许一个 `RUNNING` 运行。
- 为消息提交保存 `clientRequestId` 与请求摘要，重复提交同一用户、同一会话、同一请求标识时返回既有运行，不再次执行只读工具；活动运行期间的不同请求返回稳定 Agent 冲突错误。
- 保存服务端已校验的计划版本、节点状态、尝试次数、跳过信息、槽位快照和安全结构化回复；不保存模型原始思维、完整工具响应、精确位置或认证秘密。
- 补齐四张 Agent 表的字段、状态约束、唯一键、查询索引、过期索引和 30 天清理顺序；只有 A 审核已推送 Change、正式分配迁移版本并授权后才创建迁移脚本、进入空 MySQL 验证或执行。

## Capabilities

### New Capabilities

- `agent-session-run-persistence`: 保存并按当前用户访问最小只读 Agent 的会话、运行、消息和步骤，提供重复请求恢复与单会话活动运行约束。

### Modified Capabilities

- 无。

## Impact

- 受影响代码：`backend/src/main/java/com/miaoyu/ticket/agent/**`、Agent 测试、`backend/src/main/resources/db/migration/**`。
- 依赖：C 已提供的 `CurrentUserAccessor`；已有 `MinimalReadOnlyAgentService`、计划校验器、状态机和 `rankMoviePlan` 类型化适配器。
- 数据库：新增 B 的 Agent 表；V007 已由 D 的出行迁移使用，V008 是本 Change 的候选版本，A 复核 Change 后才正式分配；不修改 A/C/D 已有表、Repository 或业务规则。
- 接口：本 Change 只定义 B 的 Application 用例与内部 DTO，不新增 Controller、SSE、确认接口或前端协议。
- 已确认边界：D 的出行任务、快照刷新、邮件、位置、路线和餐饮不在本 Change；B 仅会在未来读取 D 已校验的只读摘要。
