## 1. Contract

- [x] 1.1 A、B 确认两个 Tool 的名称、输入、输出、超时、错误、SSE 责任与非范围。
- [x] 1.2 A 执行 OpenSpec 严格校验。

## 2. Implementation

- [x] 2.1 新增两个 A 公开 Tool API、Command 与 Result DTO。
- [x] 2.2 实现两个 `ReadOnlyToolExecutionAdapter`，只调用公开 Tool API。
- [x] 2.3 在 B 指定的 ToolDefinition/Configuration 扩展点登记两个 Tool 与 Adapter。

## 3. Verification

- [x] 3.1 覆盖注册、合法转换、空结果、参数错误、查询不可用、预算收缩、无 userId 和状态机回写测试。
- [x] 3.2 执行定向测试、`mvnw.cmd verify`、OpenSpec 严格校验与 diff 检查。
- [x] 3.3 两个成功 ToolResult 使用业务 Clock 返回五秒 freshness 窗口，并由固定时钟测试验证 `dataAt/expiresAt`；Adapter 不伪造时间。
