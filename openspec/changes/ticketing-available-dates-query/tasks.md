# Tasks: ticketing-available-dates-query

## 1. 契约与设计

- [x] 1.1 A 核对 PRD、总体后端设计、A 详细设计和前端应用设计，冻结 REST 必填参数、未来七天窗口、匿名权限、空结果和 `date + showCount` DTO；验证：不修改 B/C/D 的生产契约。
- [x] 1.2 建立并严格校验本 OpenSpec change；验证：`openspec validate ticketing-available-dates-query --strict` 通过。

## 2. A 后端实现

- [x] 2.1 实现 `AvailableDateQueryRepository`、MyBatis 聚合查询和映射；验证：只读 `movie_show`、参数绑定、稳定日期排序且不按座位余量过滤。
- [x] 2.2 实现 `AvailableDateQueryService` 和 Application DTO；验证：注入 `Clock`、七天左闭右开窗口、非正 ID 拒绝和空集合语义正确。
- [x] 2.3 在现有 `ShowController` 新增 REST 入口及独立响应 DTO；验证：字符串 ID、匿名合法请求 200、匿名缺参/非数字/零/负数 ID 返回 400 / `100001`，`dates` 数组契约已通过 HTTP 集成测试；C 已在唯一安全链登记精确 GET 路径。
- [x] 2.4 同步 OpenAPI 和 A 提供给 C 的成功 Mock 夹具；验证：字段精确且不含内容、库存、认证或交易敏感数据。

## 3. 测试与交付

- [x] 3.1 补充 H2 Application/HTTP 集成测试，覆盖聚合、排序、时间边界、停售、跨影片影院、空结果、匿名合法请求、匿名缺参和非法 ID；验证：匿名合法请求返回 200，匿名缺参、非数字、零和负数 ID 返回 400 / `100001`。
- [x] 3.2 在现有显式环境开关的真实 MySQL 场次集成测试中补充可售日期断言；验证：测试代码不连接共享库，当前轮次未提供环境变量，因此未执行该可选测试。
- [x] 3.3 执行 `mvnw.cmd verify`、OpenSpec strict、`git diff --check` 和变更范围核对；验证：169 个测试通过、0 失败、0 错误、10 个外部环境测试跳过，Checkstyle、SpotBugs、ArchUnit、JaCoCo 通过；本次新增或修改生产代码有效注释率粗测 34.68%，未修改 SQL/Flyway/B/C/D 代码。
- [x] 3.4 记录实现结果、未执行的外部验证和后续前端接入事项；验证：本变更通过个人功能分支 PR 交付，不直提 `dev`，且不提前归档 change。

## 实现记录

- 变更编号及模块：`ticketing-available-dates-query`，A 的 `ticketing` 只读查询。
- 需求/问题与修改范围：新增未来七天可售日期 Application Service、数据库聚合、REST DTO、OpenAPI 和 C 联调夹具；统计 `ON_SALE` 且严格未开场的场次，不按座位余量过滤。
- 契约影响：新增 `GET /api/v1/shows/available-dates?movieId=&cinemaId=`；在唯一 `applicationSecurityFilterChain` 增加该精确 GET 白名单以兑现已冻结的匿名公开契约；不改变既有 `/shows`、座位、订单、数据库、Agent Tool 或 D 内容事实。
- 已执行的验证及结果：定向 12 个测试通过；完整 `mvnw.cmd verify` 共 169 个测试、0 失败、0 错误、10 个外部环境测试跳过，Checkstyle 和 SpotBugs 为 0，ArchUnit、JaCoCo 通过；OpenSpec strict 和 `git diff --check` 通过。
- 未验证事项、剩余风险和后续负责人：匿名公开权限已由 C 评审意见确认并在唯一 `applicationSecurityFilterChain` 中实现，HTTP 集成测试覆盖匿名 200 与参数 400；显式 MySQL 环境测试本轮未执行；前端 AI 后续按 `data.dates[{date,showCount}]` 接入。未连接数据库或执行 SQL。
