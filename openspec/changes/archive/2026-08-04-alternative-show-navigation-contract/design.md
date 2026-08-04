# Design: alternative-show-navigation-contract

## 数据来源与调用链

`RefundApplicationService.queryAlternativeShows` 已先按本人订单取得原场次上下文，其中包含 A 持有的权威 `movieId`。随后 `RefundShowRepository.AlternativeShowCriteria` 使用该 ID 查询同影片候选，因此每个返回候选都满足同一影片约束。

本次在应用映射时将已校验的 `originalShow.movieId()` 写入 `AlternativeShowView`，再由 `RefundController` 转为十进制字符串 `AlternativeShowResponse.movieId`：

```text
本人订单
  → 原场次 RefundShowContext.movieId
  → 同影片替代场次查询
  → AlternativeShowView.movieId
  → AlternativeShowResponse.movieId（string）
```

不访问 D 的 Entity、Mapper、Repository 或表，也不在前端或 Controller 推断影片 ID。

## 兼容性

- 响应对象仅新增字段，路径、请求参数、状态码和原字段保持不变。
- `movieId` 与其他业务 ID 一样输出十进制字符串，避免 JavaScript 精度损失。
- 空候选仍返回空 `shows`，不得为了导航补造数据。
- 旧消费者忽略新增字段即可继续运行；严格消费夹具需在同一变更中同步。

## 数据库与安全影响

无数据库、迁移、权限和敏感信息影响。接口仍只允许当前用户查询本人订单，跨用户与不存在继续使用既有 404 语义。

## 验证方案

- Application 集成测试断言每个候选的 `movieId` 等于原场次影片 ID。
- C 消费夹具增加字符串 `movieId`，精确字段测试防止 DTO 与 JSON 漂移。
- OpenAPI 测试断言 `AlternativeShowResponse.movieId` 类型为 `string`。
- 执行 Maven verify、OpenSpec strict 校验和 Git 空白检查。
