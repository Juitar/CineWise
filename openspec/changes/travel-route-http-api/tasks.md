## 1. 契约

- [x] 1.1 C 已确认路径、请求字段、`DRIVING/WALKING` 范围、共享确认、隐私和错误语义。
- [x] 1.2 D 已新增 OpenAPI Controller 与请求/响应 DTO，验证公开字段和状态码。

## 2. 实现与测试

- [x] 2.1 D 已将浏览器坐标适配到现有 `BasicRouteService`，不保存坐标。
- [x] 2.2 D 已覆盖成功、任务归属、共享确认、坐标、方式和路线 Provider 异常测试。

## 3. 验证

- [x] 3.1 D 已运行相关测试、`mvnw.cmd verify`、严格 OpenSpec 校验、Checkstyle、SpotBugs 和差异检查。
