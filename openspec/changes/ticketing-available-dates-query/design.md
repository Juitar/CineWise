# Design: ticketing-available-dates-query

## 分层与调用方向

实现归属 `com.miaoyu.ticket.ticketing`：

```text
ShowController
  -> AvailableDateQueryService
    -> AvailableDateQueryRepository
      -> TicketingQueryMapper
```

Controller 只解析十进制字符串业务 ID 并映射 REST DTO。Application Service 负责输入校验、业务时间窗口和只读事务；Repository 端口表达日期聚合查询；MyBatis Mapper 使用绑定参数查询 A 权威的 `movie_show` 表。

本用例不需要影院名称或影片标题，因此不调用 D 的 `ContentSummaryQueryPort`，也不访问其他模块持久化类型。

## 查询规则

REST 的 `movieId` 和 `cinemaId` 均必填且必须解析为正 `long`。Application Service 再校验公开调用参数，防止 Controller 以外的消费者绕过规则。

业务时间统一使用注入 `Clock` 和 `Asia/Shanghai`：

- `startsAfter = 当前业务时间`，SQL 使用 `start_time > startsAfter`，所以已开场及恰好到达开场时刻的场次均排除；
- `startsBefore = 当天日期 + 7 天的 00:00`，SQL 使用左闭右开窗口，覆盖运行日到第六天；
- 仅统计 `movie_id`、`cinema_id` 同时匹配且 `status = ON_SALE` 的场次；
- 按 `DATE(start_time)` 分组并按日期升序返回；
- `showCount` 是匹配场次数量，不是余座数，不按 `show_seat` 聚合或过滤。

这种规则与现有 `/shows` 的七天窗口保持一致，但不会为了复用而先加载全部场次及 D 的影院摘要。数据库直接做有界聚合，避免无意义的数据传输和跨模块调用。

## DTO 与接口

```text
GET /api/v1/shows/available-dates?movieId={string}&cinemaId={string}

AvailableDatesResponse(
  dates: List<AvailableDateItemResponse>
)

AvailableDateItemResponse(
  date: LocalDate,
  showCount: int
)
```

Application 层使用独立的 `AvailableDateView`，REST DTO 不复用 Mapper 行模型。接口沿用 `/shows` 的公开匿名权限，不新增 SecurityFilterChain。

## 数据库与事务

这是纯只读查询：

- 使用 `@Transactional(readOnly = true)`；
- SQL 显式列出聚合字段，使用 `#{}` 绑定参数；
- 不写 `movie_show`、`show_seat` 或任何其他表；
- 不新增 Flyway、索引或缓存。

现有排期查询索引承担七天有界读取。本 change 不凭单个演示数据量新增索引；若真实执行计划不满足性能预算，再通过独立 OpenSpec 和前向迁移处理。

## 错误、空结果与兼容

- 缺少查询参数由 Spring 映射为 HTTP 400 / `100001`。
- 非数字、零、负数或超过 `long` 的 ID 由 Controller 映射为 HTTP 400 / `100001`。
- Application Service 收到非正 ID 同样返回 `100001`。
- 无匹配场次返回成功与 `dates: []`，不返回 404，不生成演示日期。
- 新增路径不改变现有响应字段、状态机、权限、数据库或消费者，属于向后兼容增量。

## 测试策略

- Application/HTTP 集成测试：成功聚合、排序、窗口边界、停售和其他影片影院过滤、空结果、缺参及非法 ID。
- OpenAPI 测试：路径公开，两个参数必填，响应包含 `dates/date/showCount`。
- REST 夹具测试：JSON 能反序列化为冻结 DTO，且不包含价格、库存、认证或内容字段。
- 可选真实 MySQL 只读测试：通过显式环境变量启用，只读取隔离测试夹具，不运行 Flyway 或种子。
- 完整执行 Maven `verify`、OpenSpec strict 和 `git diff --check`。
