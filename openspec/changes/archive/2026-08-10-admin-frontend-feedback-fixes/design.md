# Design: 管理员前端表单标识与同步反馈修复

## Decisions

### 1. 保留现有筛选状态和查询契约

筛选组件仅补充稳定的 DOM `id` 与文本输入 `name`，继续由既有本地状态组装白名单查询参数，不新增字段、不改变查询值和重置行为。

### 2. 由 Hook 返回服务端接受结果

`useAdminContentSync.submit` 返回 `ContentSyncTask | null`。只有 `requestContentSync` 返回任务时返回该任务；重复提交门禁、已知失败和结果未知均返回 `null`。页面据此决定是否显示成功提示，继续复用 Hook 内现有的错误、结果未知和原请求标识恢复逻辑。

## Compatibility and Impact

- 不改变后端接口、请求体、错误码、权限或同步任务状态。
- 不自动重试内容同步，不生成额外 `clientRequestId`。
- 现有调用方忽略返回值时行为不变。

## Verification

- 筛选组件测试断言稳定字段名称并覆盖查询、重置回归。
- Hook 测试覆盖成功返回任务与失败返回空结果。
- 页面测试覆盖仅成功时提示。
