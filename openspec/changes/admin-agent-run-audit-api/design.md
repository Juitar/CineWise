## Context

Agent 已持久化 `agent_run` 与 `agent_run_step`，但现有 `AgentRuntimeQueryService` 仅按当前用户读取，并会返回消息和事件的原始展示载荷，不能复用于管理员页面。C 的 `admin-agent` 模块已经固定列表和详情字段；认证模块已公开 `UserAdminQueryPort`，可在不访问认证 Mapper/Entity 的前提下按管理员权限取得脱敏用户显示值。

## Goals / Non-Goals

**Goals:**

- 在 `agent` 模块新增管理查询 Application Service、只读 Repository 端口与 MyBatis 实现、REST DTO 和 Controller。
- 列表只接受 C 已固定的 `status`、`userKeyword`、`startedFrom`、`startedTo`、`page`、`size`，默认页码和上限复用 `ApiProperties`。
- 详情与列表共享摘要投影；步骤只从 `AgentRunStep` 的结构化列组装安全字段。
- 应用层再次检查 `CurrentUserAccessor` 中的 `ADMIN`，并复用 `206005` 作为不存在运行的 404。

**Non-Goals:**

- 不读取或返回消息、事件 payload、步骤 JSON、确认动作、模型上下文或工具入参。
- 不新增表、Flyway、SSE 或写操作；不改 C 前端、auth Mapper/Entity/Repository 或其他 Owner 的私有实现。

## Decisions

### 1. 使用 Agent 自有的管理查询端口

新增 `AdminAgentRunQueryRepository`，只暴露分页运行主行、运行详情和步骤；MyBatis SQL 只读取 `agent_run`、`agent_run_step` 的明确列。Application Service 不读取 Mapper、Entity 或 JSON 列。

未复用 `AgentRuntimeQueryService`，因为它的“本人恢复”语义和消息/事件读取范围不适用于管理员脱敏审计。

### 2. 用户显示和关键词只调用 C 的公开端口

Application Service 先校验管理员；有 `userKeyword` 时调用 `UserAdminQueryPort.findUserIdsByKeyword` 获取受限 ID 集合，再传给 Agent 自有 Repository。当前页用户显示通过 `findByUserIds` 批量取得 `emailMasked`；历史用户缺失时返回固定 `"用户不可用"`，不回传内部 `userId` 或补查认证持久化对象。

这不访问 auth 的 Mapper、Entity 或 Repository，且不会在 Agent 保存第二份用户目录。

### 3. DTO 是白名单投影

列表与详情返回 `runId`、`sessionId`、`userDisplay`、`status`、`planId`、`planVersion`、节点计数、开始/结束时间、耗时、错误码和安全错误摘要；详情增加 `nodes`。列表节点计数由当前页 `runId` 批量聚合 `agent_run_step.status` 得到。节点只包含 `nodeId`、`nodeType`、`targetName`、状态、次数、时间、耗时、`toolStatus`、错误码、错误摘要和恢复提示。

当前 `AgentRunStep` 没有持久化 `targetName`、工具状态、错误码或错误摘要；`AgentRun` 也没有失败码/摘要。不得从 `inputRefsJson`、`slotSnapshotJson`、`dependsOnJson`、事件 payload 或确认命令提取或猜测这些字段；没有结构化来源时返回 `null`。后端无法在不新增经过确认的安全摘要列/读取规则前满足 C 的必填字段。

### 4. 时间、排序和分页

`startedFrom`/`startedTo` 由 Spring 绑定为 ISO 8601 `OffsetDateTime`，再转换到业务时区的 `LocalDateTime`；范围为左闭右开，结束时间早于开始时间返回 400。列表固定按 `started_at DESC, id DESC`，禁止客户端排序字段；页码从 1 开始，页大小按已有 `ApiProperties`。

### 5. 已发现的前端字段冲突

前端 `frontend/src/modules/admin-agent/types.ts` 将 `planId`、`planVersion`、节点 `targetName` 设为必填，并显示节点 `toolStatus`、`errorCode`、`errorSummary`。但：

- `AgentRun.planId`、`planVersion` 在 `WAITING_LOCATION` 运行中可为 `null`；
- `AgentRunStep` 没有 `targetName`、工具状态、错误码或错误摘要列；
- 运行表没有失败码或安全失败摘要列。

本 change 不把空值改成伪造字符串、不把节点类型当作工具名，也不解析或透传持久化 JSON。需要 C 确认可空/删除字段的前端 DTO，或由 B/A 确认新增持久化安全摘要的字段、生命周期和迁移方案。另，前端筛选只声明 `RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED`，而 Agent 持久化还存在 `WAITING_LOCATION`；本 change 不改写该状态。

## Risks / Trade-offs

- [auth 用户目录不可用] → 保持 C 端口的业务错误，不把真实故障当作空结果。
- [历史用户已删除] → 使用固定安全占位，不泄露内部 ID。
- [步骤中存在敏感 JSON] → SQL 和 DTO 均不选择、不持有这些列，并用响应测试断言敏感值不存在。
- [C 的必填字段没有安全数据源] → 已记录 `types.ts` 和持久化对象差异；在字段来源被确认前不实现伪造 DTO。
- [C 未处理 `WAITING_LOCATION`] → 已记录具体字段，后端保持真实状态，未做映射兼容。

## Migration Plan

无需数据库迁移。部署后，ADMIN 使用现有 Cookie/JWT 请求新 GET 接口；回退时移除 Controller 路由即可，不影响运行主流程或已保存的 Agent 数据。

## Open Questions

- C 是否接受 `planId`、`planVersion`、节点 `targetName` 为可空，并删除/改为可选 `toolStatus`、`errorCode`、`errorSummary`，需 C 明确。
- 如 C 必须保留上述字段，B/A 是否批准为 Agent 运行/步骤新增哪些安全摘要列、由谁分配 Flyway 版本，需明确。
- `status=WAITING_LOCATION` 是否加入 C 的筛选选项和页面文案，需 C 明确；本次不猜测映射规则。
