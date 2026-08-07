## Why

C 已合入的管理端 `agent-logs` 页面已经固定请求路径、查询参数和展示字段，但 B 侧尚未提供只读、脱敏的管理员运行轨迹查询接口，页面不能进行真实 HTTP 联调。

## What Changes

- 新增仅限 `ADMIN` 访问的 Agent 运行列表和详情查询 API。
- 在 Agent 自己的 Application/Repository 边界内查询运行与步骤，映射为明确的脱敏 DTO。
- 提供与 C 当前页面一致的分页、筛选、错误响应、测试和固定 JSON 夹具。
- 不新增迁移，不修改 Agent 主流程、SSE、确认动作、模型调用或其他业务模块。

## Capabilities

### New Capabilities

- `admin-agent-run-audit-api`: 管理员按安全筛选条件查询脱敏 Agent 运行摘要和步骤轨迹。

### Modified Capabilities

无。

## Impact

- Owner：B；修改 `backend/src/main/java/com/miaoyu/ticket/agent/**`、对应测试和 `backend/src/test/resources/fixtures/agent/c/**`。
- 新增 `GET /api/v1/admin/agent-runs` 与 `GET /api/v1/admin/agent-runs/{runId}`；复用公共 `Result<T>`、`PageResult<T>`、现有 Spring Security `/api/v1/admin/**` 的 `ADMIN` 规则和 Agent 的 `206005` 404 规则。
- C 可用当前 `frontend/src/modules/admin-agent/api.ts` 直接调用；不要求 C 修改字段或临时兼容。
