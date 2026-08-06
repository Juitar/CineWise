# api-error-envelope-without-data Specification

## Purpose
TBD - created by archiving change fix-api-error-envelope-without-data. Update Purpose after archive.
## Requirements
### Requirement: 失败响应允许不包含 data

公共 `apiRequest<T>()` SHALL 先校验 JSON 对象中的 `code/message/traceId`，再判断 HTTP 状态和业务错误码。HTTP 非 2xx 或 `code` 表示失败时，响应 MAY 省略 `data` 或包含 `data: null`，客户端 MUST 生成现有 `ApiError` 并保留安全消息、HTTP 状态、稳定错误码和 `traceId`。

#### Scenario: 非 2xx 失败信封省略 data

- **GIVEN** 后端返回 401、403、409、422、429 或 5xx
- **AND** JSON 对象包含合法 `code/message/traceId` 但不包含 `data`
- **WHEN** 公共客户端解析响应
- **THEN** 客户端抛出带真实 `status/code/traceId/message` 的 `ApiError`
- **AND** 不把错误改成“服务返回的数据格式不正确”

#### Scenario: 非 2xx 失败信封包含 data null

- **GIVEN** 后端返回合法失败信封且 `data: null`
- **WHEN** 公共客户端解析响应
- **THEN** 处理结果与省略 `data` 相同

#### Scenario: 2xx 中业务码表示失败

- **GIVEN** HTTP 状态为 2xx 且响应 `code !== 0`
- **AND** 响应省略 `data`
- **WHEN** 公共客户端解析响应
- **THEN** 客户端抛出 `kind: BUSINESS` 的 `ApiError`
- **AND** 保留业务错误码、安全消息和 `traceId`

### Requirement: 成功响应继续校验必需 data

公共客户端 MUST 在 `response.ok && code === 0` 时要求信封包含 `data`。本 change MUST NOT 把全部成功响应改成允许缺少 `data`，也 MUST NOT 使用强制类型断言掩盖缺失数据。

#### Scenario: 成功响应包含合法 data

- **GIVEN** HTTP 状态为 2xx、`code === 0` 且包含合法 `data`
- **WHEN** 公共客户端解析响应
- **THEN** 客户端返回该 `data`

#### Scenario: 成功响应缺少 data

- **GIVEN** HTTP 状态为 2xx、`code === 0` 但不包含 `data`
- **WHEN** 公共客户端解析响应
- **THEN** 客户端抛出 `INVALID_RESPONSE`
- **AND** 消息为“服务返回的数据格式不正确”

#### Scenario: 当前正式接口的成功响应显式 data null

- **GIVEN** HTTP 状态为 2xx、`code === 0` 且 `data: null`
- **AND** 当前仓库正式接口均要求具体成功数据
- **WHEN** 公共客户端解析响应
- **THEN** 客户端抛出 `INVALID_RESPONSE`
- **AND** 不通过泛型或强制类型转换临时声明成功 null 契约

### Requirement: 认证和 CSRF 错误必须保留现有公共行为

公共客户端 SHALL 在合法失败信封解析后处理 401 和 403。`401/201006` SHALL 触发共享会话失效处理；`403/201009` SHALL 刷新 CSRF Token 但不得重放原写请求；`403/201007` SHALL 保留当前登录状态和内存 CSRF Token。

#### Scenario: 多个并发 401 省略 data

- **GIVEN** 多个并发请求返回 `401/201006` 且省略 `data`
- **WHEN** 公共客户端处理响应
- **THEN** 只执行一次会话清理和登录跳转处理
- **AND** 每个调用方都收到 `ApiError(status=401, code=201006)`

#### Scenario: CSRF 失效省略 data

- **GIVEN** 写请求返回 `403/201009` 且省略 `data`
- **WHEN** 公共客户端处理响应
- **THEN** 清理内存旧 CSRF Token并可获取新 Token
- **AND** 不自动重放原写请求
- **AND** 调用方收到 `ApiError(status=403, code=201009)`

#### Scenario: 权限不足省略 data

- **GIVEN** 写请求返回 `403/201007` 且省略 `data`
- **WHEN** 公共客户端处理响应
- **THEN** 不清理或刷新内存 CSRF Token
- **AND** 不执行会话失效处理或跳转登录
- **AND** 调用方收到 `ApiError(status=403, code=201007)`

### Requirement: 不可解析响应不得泄露原始正文

非 JSON、空响应、HTML、非法 JSON 或结构完全错误时，公共客户端 SHALL 返回固定安全的网络、HTTP 或响应格式错误，并 MUST NOT 把原始响应正文、HTML、代理内容、堆栈或敏感信息写入错误消息、日志或测试输出。

#### Scenario: 非 2xx 返回空响应 HTML 或非法 JSON

- **GIVEN** 后端或代理返回非 2xx 空响应、HTML 或非法 JSON
- **WHEN** 公共客户端读取响应
- **THEN** 客户端抛出固定安全文案的 `INVALID_RESPONSE`
- **AND** 保留可用的 HTTP 状态和响应头 traceId
- **AND** 错误消息不包含原始正文

#### Scenario: 合法 5xx 失败信封

- **GIVEN** 后端返回 5xx 且 JSON 信封包含合法 `code/message/traceId`
- **WHEN** 公共客户端解析响应
- **THEN** 客户端抛出保留后端错误码和 `traceId` 的 HTTP `ApiError`

### Requirement: 退款调用方必须收到公共结构化错误

A 的退款调用方 SHALL 继续复用公共 `apiRequest<T>()`。本 change MUST NOT 修改退款页面或业务实现来绕过公共客户端。

#### Scenario: 退款请求返回认证 CSRF 或业务冲突错误

- **GIVEN** 现有退款请求分别返回 `403/201009`、`401/201006` 或 409 退款业务错误
- **WHEN** `createRefund()` 调用公共客户端
- **THEN** 调用方收到保留真实状态、错误码、消息和 `traceId` 的 `ApiError`
- **AND** 不收到“服务返回的数据格式不正确”
