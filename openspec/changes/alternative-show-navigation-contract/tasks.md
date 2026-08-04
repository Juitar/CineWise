# Tasks: alternative-show-navigation-contract

## 1. 契约确认

- [x] 1.1 A 确认替代场次选座回流需要 `showId + movieId + cinemaId`，且 `movieId` 来自 A 的原场次上下文；验证：不查询 D 持久层、不修改数据库。
- [x] 1.2 严格校验本 change；验证：`openspec validate alternative-show-navigation-contract --strict` 通过。

## 2. 后端实现

- [x] 2.1 为 `AlternativeShowView` 和 `AlternativeShowResponse` 增加 `movieId`，同步应用与 Controller 映射；验证：ID 以十进制字符串输出，既有字段语义不变。
- [x] 2.2 同步 C 的替代场次成功夹具和精确字段契约测试；验证：运行时 DTO 反序列化及敏感字段扫描通过。
- [x] 2.3 补充 Application 与 OpenAPI 回归断言；验证：候选影片与原订单场次一致，OpenAPI 中 `movieId` 为 string。

## 3. 文档与交付

- [x] 3.1 同步 A 后端设计与前端消费接口中的替代场次字段；验证：设计不再声明缺少 `movieId` 的旧结构。
- [x] 3.2 执行 `mvnw.cmd verify`、OpenSpec strict 校验、`git diff --check` 和变更范围核对；验证：本地定向 10 个测试与排除 `ContentControllerIntegrationTest` 后的完整生命周期通过；本地全量验证受该 D 内容测试 `MOCK/CACHE` 环境差异影响，GitHub Backend Verify #123 未排除测试并完整通过。
