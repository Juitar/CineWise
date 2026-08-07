# Design: ticketing-canonical-business-id

## 规则

票务公开查询入口的业务 ID 采用 canonical 字符串：`^[1-9]\\d*$`，并且必须能解析为 Java `long` 正数。后端响应继续使用 `Long.toString`，因此请求与响应保持同一规范形式。

## 实现

`ShowController` 在进入 Application Service 前先验证字符串正则，再执行 `Long.parseLong` 处理溢出。任何前导零、前缀、符号、空白、零值或溢出值都映射为既有的 `100001 INVALID_PARAMETER`，不会进入查询或座位服务。

## 兼容与风险

先前依赖前导零的调用方会收到 400；该形式未被对外 canonical 契约允许。B/C 已确认改为无前导零的十进制字符串。本次不改变数据库 BIGINT、输出字段、权限或交易状态。

## 验证

- 集成测试覆盖 `01`、`show-70001`、`0` 和超出 long 范围的拒绝行为。
- 保留合法 ID 查询和座位图成功用例。
- 执行 `mvnw.cmd verify`、OpenSpec strict 和 `git diff --check`。
