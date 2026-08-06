## Why

后端失败响应会因全局忽略 null 字段而省略 `data`。当前公共 `apiRequest<T>()` 在判断 HTTP 状态和业务错误码之前强制要求响应含有 `data`，导致真实的 401、403、409、422、429 和 5xx 错误被改成“服务返回的数据格式不正确”，调用页面拿不到稳定错误码和 `traceId`。

## What Changes

- 调整公共 REST 客户端的统一响应解析顺序：先校验 JSON 对象及 `code/message/traceId`，再按 HTTP 状态和业务错误码处理失败。
- 失败响应允许省略 `data` 或显式返回 `data: null`，并生成保留状态、错误码、安全消息和 `traceId` 的现有 `ApiError`。
- 成功响应继续要求存在 `data`；不为全部成功接口放宽类型，也不使用 `as T` 掩盖缺失数据。
- 保留并补强 401 单次会话失效处理，以及 403 中 `201009` 与 `201007` 的不同处理。
- 增加公共客户端、认证请求层和退款调用方回归测试，不修改任何业务页面或后端。

### 非范围

- 不修改 A 的订单、支付、选座、退款页面或业务实现。
- 不修改 B 的 Agent 后端、D 的内容/画像/出行代码。
- 不修改后端 `Result<T>`、Jackson null 配置、错误码、数据库或迁移。
- 不自动重放任何写请求，不记录 Cookie、JWT、CSRF Token、密码、验证码或完整响应体。

## Capabilities

### New Capabilities

- `api-error-envelope-without-data`: 公共 REST 客户端正确解析不含 `data` 的失败响应，并保留各调用方恢复所需的结构化错误信息。

### Modified Capabilities

无。

## Impact

- Owner：C。
- 受影响调用方：A/B/C/D 均复用公共 `apiRequest<T>()`，需要审查同步时对 `frontend/src/shared/api/client.ts` 的冲突处理；本 change 不要求其他 Owner 修改业务模块。
- 主要文件：`frontend/src/shared/api/client.ts`、`frontend/src/shared/api/client.test.ts`，以及必要的 `ApiError`、公共类型和现有调用方测试。
- 完成标志：失败信封不含 `data` 时仍生成真实 `ApiError`；成功缺少必需 `data` 仍报响应格式错误；规定的认证、CSRF、退款回归和完整前端检查通过。
