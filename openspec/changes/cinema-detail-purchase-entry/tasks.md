# Tasks: cinema-detail-purchase-entry

## 方案与协作

- [x] 创建 proposal、design、两个 capability spec 和实施任务清单。
- [x] 产品范围已确认：排片、影厅、票价和座位允许使用 `demo-seed`，验收目标是从影院详情完成现有购票流程，不要求第三方真实排片。
- [ ] D 提供或扩展公开 Application API，解析至少一家长沙 LIVE 影院和可用影片的内部 ID；A 不得按名称、地址或直接读表关联。
- [ ] A、C、D 核对 `available-movies` DTO、时间窗口、来源字段、错误码和演示标识，并在实现前记录确认结果。

## A：按影院查询和演示排期

- [x] A 实现按 `cinemaId` 聚合未来 7 天 `ON_SALE` 且未开场场次的 Application 查询。
- [ ] D 审查确认 `ContentPurchaseQueryPort` 的批量输入、`MovieSummary` 五个输出字段、缺失项排除和整体不可用返回空 Map 的规则；未取得 D 确认前不得勾选。
- [x] A 实现 `GET /api/v1/shows/available-movies?cinemaId=`、REST DTO、参数校验、统一错误映射和 OpenAPI。
- [x] A 为至少一家已确认的长沙 LIVE 影院建立明确标记 `demo-seed` 的可重复 Mock 排期；不得覆盖非 `AVAILABLE` 座位。
- [x] A 增加 H2 HTTP 契约测试和演示种子测试，覆盖聚合、排序、空数组、非法参数和种子幂等；真实 MySQL 集成仍待共享库验收。

## C：影院详情和购票入口

- [x] C 将影院列表卡片改为指向 `/cinemas/:cinemaId` 的语义化 `Link`，保留城市和来源展示规则。
- [x] C 增加可售影片 DTO、API 方法和 Hook，复用公共请求层并处理取消、竞态和统一错误。
- [x] C 实现公开影院详情路由和页面，并行查询影院详情与可售影片，分别处理加载、失败和重试。
- [x] C 实现影院 404、影片空数组、排期部分失败、离线、海报占位、来源和更新时间展示。
- [x] C 对 Mock 来源显示“演示排期”；没有可售影片时不展示价格、余座或购票按钮。
- [x] C 将影片选择入口跳转到 `/shows?movieId={movieId}&cinemaId={cinemaId}`，不改写现有选座、建单和支付规则。
- [x] C 完成 PC 和移动端响应式、键盘访问、可见焦点和不小于 44px 的触控目标。
- [x] C 修复共享环境连续写请求复用旧 CSRF Token 的问题，建单响应后支付必须重新取得服务端更新的 Token。
- [x] A、C 按评审意见拆分并展示 `contentSource/contentDataTime` 与 `scheduleSource/scheduleDataTime`，同步 OpenAPI、DTO 和测试。

## 验证与交付

- [x] C 增加 API/Hook 和页面测试，覆盖空态、404、部分失败、重试、竞态、响应式及完整跳转。
- [x] A、C 在 HTTP 契约测试中导出并核对 OpenAPI，确认新接口路径、字符串 ID、时间、来源字段和空结果格式与前端 DTO 一致。
- [x] 使用真实后端和数据库完成 HTTP 联调：验证至少一家长沙 LIVE 影院可进入明确标识的演示购票流程，其他无排期影院准确显示空态。
- [x] 运行后端 `mvn verify`、前端格式/Lint/类型检查/全量测试/生产构建、OpenSpec 严格校验和差异检查，并完成共享 MySQL 浏览器验收。
- [x] 前端默认 `pnpm check` 已通过；最新 `dev` 已将既有后台订单测试超时调整到可用范围。
- [ ] 实现完成后同步规格并归档本 change；未完成跨模块任务前不得标记整个 change 完成。

## 本地验证记录

- 后端全量：`mvn -DforkCount=0 verify` 通过，407 个测试，0 失败、0 错误、23 个可选环境测试跳过；Checkstyle 和 SpotBugs 均为 0 问题。
- 后端新增能力：`ShowControllerIntegrationTest` 9 个测试通过；`DemoSeedInitializerTest` 3 个测试通过，其中长沙 LIVE 影院生成 2 个 Mock 影厅、42 个场次和 3360 个座位。
- 前端全量：`pnpm check` 通过，72 个测试文件、285 个测试通过；格式、Lint、类型检查和生产构建检查全部通过。
- 本地 HTTP：8081 的真实 Spring Boot + H2 进程成功返回影院详情、`available-movies` 和后续 `/shows`；首个影片返回 4 个 `ON_SALE`、`MOCK/demo-seed` 场次。
- 共享 MySQL HTTP：8082 返回 20 家长沙 LIVE 影院；目标影院 `2084825488119652354` 返回 3 部 `demo-seed` 影片、首部影片 14 个未来场次，首场座位图 80 座。种子幂等确认目标影院为 42 个场次、3360 个座位。
- 共享环境性能：批量影片摘要改为一次目录查询后，`available-movies` 从约 12.9 秒降到约 4.7 秒，低于公共请求层 10 秒超时。
- 浏览器 HTTP：8002 前端代理到 8082，实际完成登录、长沙影院详情、选场、选座、建单、模拟支付和电子票展示；页面请求无 4xx/5xx 和脚本错误。诊断期间产生的一笔待支付订单已取消并释放座位。
- 变基后端共享环境复核时，最新认证配置要求 `AUTH_INVITE_HASH_SECRET` 至少 32 字节；现有共享 `.env` 只有 23 字节，本次仅通过进程级随机值启动，未修改 `.env`。该共享环境配置需由部署负责人补齐。
- 未验证：共享 Redis 当前不可连接，因此整体健康检查仍为 503；影院、排片、座位、订单、支付和电子票的共享 MySQL 流程已单独验证。生产构建静态资源浏览器验收仍沿用已有全量检查结果，本次使用开发服务器验证真实 HTTP。
