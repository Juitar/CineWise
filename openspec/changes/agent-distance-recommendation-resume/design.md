## Context

`rankMoviePlan` 已完成普通完整推荐切换，D 已合入一次性距离上下文的创建、清理和消费。C 先创建等待 run，再取得一次性上下文并直接向 D 上传坐标，随后由 B 在同一 run 开始推荐。A 已发布 V018，`agent_run` 已允许 `WAITING_LOCATION`；常规陈旧恢复器仍只扫描 `RUNNING`，因而不能以临时内存状态代替持久化状态。

## Goals / Non-Goals

**Goals:**

- 让同一 `runId + planVersion` 在位置上传成功后以 `ToolContext(distanceContextId, "NEAREST")` 调用唯一完整推荐入口。
- 在拒绝、上传失败或五分钟等待超时后清理可用的一次性上下文，并恢复同一 run 的普通城市推荐；不再次等待位置、不创建新 run。
- 使用 `WAITING_LOCATION`、数据库时间和 `id + version + expectedStatus` CAS 防止恢复、取消、完成间相互覆盖。
- 复用 A 已验证并发布的 V018 前向迁移，不修改冻结 SQL。

**Non-Goals:**

- 不接收、存储、记录、转发或展示坐标、地址、定位来源或路线数据；B 不调用位置上传接口。
- 不修改 D 的距离算法、`DistanceContextService`、`RankMoviePlanTool`、A 的票务接口或 C 的前端。
- 不增加 `RankMoviePlanCommand` 字段，不把 `distanceContextId` 写入槽位、模型输入、SSE、Redis、MySQL 或 Agent 轨迹。
- 不在超时、断网或未知结果下自动重发请求。

## Decisions

### 1. 持久化等待状态而非临时前端提示

计划已保存且即将执行的 `rankMoviePlan` 遇到用户选择距离推荐时，B 在同一事务中把 run 从 `RUNNING` CAS 为 `WAITING_LOCATION`，不释放 `agent_session.active_run_id`，并发出不含位置资料的通用等待事件。`WAITING_LOCATION.finished_at` 为 NULL。上传成功、拒绝、上传失败和超时均只接受 `id + version + expectedStatus=WAITING_LOCATION` 的转换。

临时内存提示在刷新或重启时会丢失、会永久占用 `active_run_id`，且无法与取消和恢复竞争，因此不采用。

### 1.1 四个 REST 与初始化边界

`POST /api/v1/agent/sessions/{sessionId}/distance-recommendation-runs` 原子创建 USER message 和
`WAITING_LOCATION` run，初始 run 不带 plan，且不得调用模型、推荐工具、D 距离上下文或位置上传接口。
相同 `clientRequestId` 与同内容只返回原 run；摘要不同返回既有请求摘要冲突；其它 active run 返回 409。
`GET /api/v1/agent/sessions/{sessionId}/runs/by-client-request/{clientRequestId}` 仅用于 POST 结果未知时
查询原结果，找不到统一 404。两个接口成功结果都是 `{runId,status,lastEventId}`，其中 `lastEventId` 是字符串。

`POST /api/v1/agent/sessions/{sessionId}/runs/{runId}/distance-context` 仅允许当前用户的等待 run 创建上下文。
`POST /api/v1/agent/runs/{runId}/distance-recommendation` 只接受 UUID、`NEAREST` 和
`UPLOADED|DENIED|CANCELLED|FAILED`；成功只返回运行摘要，绝不返回上下文 ID。

### 2. 等待时钟固定为数据库 update_time

进入 `WAITING_LOCATION` 的状态转换写入一次 `agent_run.update_time`。等待期间的读取、SSE 重连和上下文创建均不得刷新该字段。恢复扫描以数据库 `CURRENT_TIMESTAMP(3)` 和 `update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE)` 判断；CAS 条件同时要求预期 version 和 `WAITING_LOCATION`。五分钟与 D 的 300 秒 TTL 对齐。

常规 `RUNNING` 陈旧恢复仍维持既有 30 秒规则；它不得扫描或终止未超时的 `WAITING_LOCATION`。等待恢复器单独扫描，超时后恢复普通推荐而不是标记失败。

### 3. 距离上下文仅在 B 内存中短暂关联

`POST /distance-context` 以可信 runId 调 D 的 `createForRun`，返回 `{distanceContextId, expiresAt, distancePreference:"NEAREST"}`，不保存 ID。`POST /distance-recommendation` 校验请求的 UUID 和固定枚举后，仅在当前应用调用中将其放入可信 ToolContext；C 的坐标始终直传 D。

`DENIED`、`FAILED` 和超时时，若当前进程仍持有 ID，B 调用 D `cleanup(id, runId)`；`NOT_FOUND` 等同清理成功。重启后 B 不可能取得未持久化的 ID，明确依赖 D 的 300 秒 TTL；除非 D 以后提供可信的按 runId 清理 API，B 不猜测或补建该调用。

### 4. 结果分支及恢复身份

初始化等待 run 尚未生成 plan。`UPLOADED` 在同一 run 上生成并执行一次 NEAREST 推荐；`DENIED`、`CANCELLED`、`FAILED`、等待超时在同一 run 上生成并执行一次普通推荐，且不再进入等待。主动运行取消在清理后进入 `CANCELLED`，运行内部失败在清理后进入 `FAILED`，两者均不得继续推荐。旧事件、旧 planVersion 或 CAS 失败均不得恢复已取消、完成或已恢复的 run。

### 5. V018 已发布并冻结

保留 V008 不变。A 已发布的 V018 仅替换 `chk_agent_run_status` 和 `chk_agent_run_completion`：旧状态继续合法，新增 `WAITING_LOCATION`，并使 RUNNING/WAITING_LOCATION 均要求未完成时间。迁移不重写历史数据、不改列长度、不建物理外键；B 不得修改冻结 SQL。

## Risks / Trade-offs

- [进程重启后无法主动 cleanup] → 上下文 ID 不持久化，依赖 D 的 300 秒 TTL；后续由 D 提供可信 runId 清理 API 才能消除该窗口。
- [并发上传、取消、超时竞争] → 每个状态改变均以 `id + version + expectedStatus` CAS；CAS 未命中只重新查询状态，绝不覆盖。
- [位置上传 URL 记录 ID] → D 或公共基础设施确认路径访问日志脱敏前，不能把“不进入日志”标为验收完成。
- [后续结构需求] → V018 已冻结；任何进一步表结构变更必须由 A 分配更高版本，B 不私自改迁移顺序。

## Migration Plan

1. A 已在 MySQL 8.4 完成 V018 的空库、历史状态和重复 Flyway 验证并发布共享库。
2. B 实施持久化状态、REST 应用服务、恢复器、SSE 脱敏与定向测试；C 按已确认 REST 合约联调。
3. 发生回退时停止创建新的等待状态；已有 WAITING_LOCATION 由超时恢复为普通推荐，D 的未消费上下文按 TTL 清除。

## Open Questions

- D/公共基础设施：`/api/v1/recommendation/distance-contexts/{distanceContextId}/location` 的访问日志是否已脱敏或禁记。
- C/B：定义使同一 RUNNING run 进入 WAITING_LOCATION 的可信触发接口或已持久化计划语义。
